package water.jdbc;

import water.*;
import water.fvec.*;
import water.parser.ParseDataset;
import water.util.Log;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.sql.*;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SQLManager {

  private static final String TEMP_TABLE_NAME = "table_for_h2o_import";
  private static final String MAX_USR_CONNECTIONS_KEY = H2O.OptArgs.SYSTEM_PROP_PREFIX + "sql.connections.max";
  private static final String JDBC_DRIVER_CLASS_KEY_PREFIX = H2O.OptArgs.SYSTEM_PROP_PREFIX + "sql.jdbc.driver.";
  //A target upper bound on number of connections to database
  private static final int MAX_CONNECTIONS = 100;
  //A lower bound on number of connections to database per node
  private static final int MIN_CONNECTIONS_PER_NODE = 1;
  private static final String NETEZZA_DB_TYPE = "netezza";
  private static final String HIVE_DB_TYPE = "hive2";
  private static final String ORACLE_DB_TYPE = "oracle";
  private static final String SQL_SERVER_DB_TYPE = "sqlserver";
  private static final String TERADATA_DB_TYPE = "teradata";

  private static final String NETEZZA_JDBC_DRIVER_CLASS = "org.netezza.Driver";
  private static final String HIVE_JDBC_DRIVER_CLASS = "org.apache.hive.jdbc.HiveDriver";

  private static final String TMP_TABLE_ENABLED = H2O.OptArgs.SYSTEM_PROP_PREFIX + "sql.tmp_table.enabled";

  private static final String DISALLOWED_JDBC_PARAMETERS_PARAM = H2O.OptArgs.SYSTEM_PROP_PREFIX + "sql.jdbc.disallowed.parameters";

  private static final Pattern JDBC_PARAMETERS_REGEX_PATTERN = Pattern.compile("(?i)[?;&]([a-z]+)=");

  private static final List<String> DEFAULT_JDBC_DISALLOWED_PARAMETERS = Stream.of(
          "autoDeserialize", "queryInterceptors", "allowLoadLocalInfile", "allowMultiQueries", //mysql 
          "allowLoadLocalInfileInPath", "allowUrlInLocalInfile", "allowPublicKeyRetrieval", //mysql 
          "init", "script", "shutdown" //h2
  ).map(String::toLowerCase).collect(Collectors.toList());
  private static AtomicLong NEXT_TABLE_NUM = new AtomicLong(0);
  
  static Key<Frame> nextTableKey(String prefix, String postfix) {
    Objects.requireNonNull(prefix);
    Objects.requireNonNull(postfix);

    final long num = NEXT_TABLE_NUM.incrementAndGet();
    final String s = prefix + "_" + num +  "_" + postfix;
    final String withoutWhiteChars = s.replaceAll("\\W", "_");

    return Key.make(withoutWhiteChars);
  }
  
  /**
   * @param connection_url (Input)
   * @param table (Input)
   * @param select_query (Input)
   * @param username (Input)
   * @param password (Input)
   * @param columns (Input)
   * @param fetchMode (Input)
   * @param numChunksHint (optional) Specifies the desired number of chunks for the target Frame  
   */
  public static Job<Frame> importSqlTable(
      final String connection_url, final String table, final String select_query,
      final String username, final String password, final String columns,
      final Boolean useTempTable, final String tempTableName,
      final SqlFetchMode fetchMode, final Integer numChunksHint) {
    validateJdbcUrl(connection_url);

    final Key<Frame> destination_key = nextTableKey(table, "sql_to_hex");
    final Job<Frame> j = new Job<>(destination_key, Frame.class.getName(), "Import SQL Table");

    final String databaseType = getDatabaseType(connection_url);
    initializeDatabaseDriver(databaseType); // fail early if driver is not present

    SQLImportDriver importDriver = new SQLImportDriver(
        j, destination_key, databaseType, connection_url, 
        table, select_query, username, password, columns, 
        useTempTable, tempTableName,
        fetchMode, numChunksHint
    );
    j.start(importDriver, Job.WORK_UNKNOWN);

    return j;
  }

  private static class SQLImportDriver extends H2O.H2OCountedCompleter<SQLImportDriver> {

    final Job<Frame> _j;
    final Key<Frame> _destination_key;
    final String _database_type;

    final String _connection_url;
    final String _table;
    final String _select_query;
    final String _username;
    final String _password;
    final String _columns;
    final boolean _useTempTable;
    final String _tempTableName;
    final SqlFetchMode _fetch_mode;
    final Integer _num_chunks_hint;

    SQLImportDriver(
        Job<Frame> job, Key<Frame> destination_key, String database_type, 
        String connection_url, String table, String select_query, String username, String password, String columns,
        Boolean useTempTable, String tempTableName, SqlFetchMode fetch_mode, Integer numChunksHint
    ) {
      _j = job;
      _destination_key = destination_key;
      _database_type = database_type;
      _connection_url = connection_url;
      _table = table;
      _select_query = select_query;
      _username = username;
      _password = password;
      _columns = columns;
      _useTempTable = shouldUseTempTable(useTempTable);
      _tempTableName = getTempTableName(tempTableName);
      _fetch_mode = fetch_mode;
      _num_chunks_hint = numChunksHint;
    }

    /*
     * if tmp table disabled, we use sub-select instead, which outperforms tmp source_table for very
     * large queries/tables the main drawback of sub-selects is that we lose isolation, but as we're only reading data
     * and counting the rows from the beginning, it should not an issue (at least when using hive...)
     */
    private boolean shouldUseTempTable(Boolean fromParams) {
      if (fromParams != null) {
        return fromParams;
      } else {
        return Boolean.parseBoolean(System.getProperty(TMP_TABLE_ENABLED, "true"));
      }
    }

    private String getTempTableName(String fromParams) {
      if (fromParams == null || fromParams.isEmpty()) {
        return SQLManager.TEMP_TABLE_NAME;
      } else {
        return fromParams;
      }
    }

    @Override
    public void compute2() {
      _j.update(0, "Initializing import");
      Connection conn = null;
      Statement stmt = null;
      ResultSet rs = null;
      int catcols = 0, intcols = 0, bincols = 0, realcols = 0, timecols = 0, stringcols = 0;
      final int numCol;
      long numRow = 0;
      String source_table = _table;
      final String[] columnNames;
      final byte[] columnH2OTypes;
      try {
        conn = getConnectionSafe(_connection_url, _username, _password);
        stmt = conn.createStatement();
        //set fetch size for improved performance
        stmt.setFetchSize(1);
        //if _select_query has been specified instead of source_table
        if (source_table.equals("")) {
          if (!_select_query.toLowerCase().startsWith("select")) {
            throw new IllegalArgumentException("The select query must start with `SELECT`, but instead is: " + _select_query);
          }
          if (_useTempTable) {
            source_table = _tempTableName;
            //returns number of rows, but as an int, not long. if int max value is exceeded, result is negative
            _j.update(0L, "Creating a temporary table");
            numRow = stmt.executeUpdate(createTempTableSql(_database_type, source_table, _select_query));
          } else {
            source_table = "(" + _select_query + ") sub_h2o_import";
          }
        } else if (source_table.equals(SQLManager.TEMP_TABLE_NAME)) {
          //tables with this name are assumed to be created here temporarily and are dropped
          throw new IllegalArgumentException("The specified source_table cannot be named: " + SQLManager.TEMP_TABLE_NAME);
        }
        //get number of rows. check for negative row count
        if (numRow <= 0) {
          _j.update(0L, "Getting number of rows");
          rs = stmt.executeQuery("SELECT COUNT(1) FROM " + source_table);
          rs.next();
          numRow = rs.getLong(1);
          rs.close();
        }
        //get H2O column names and types
        _j.update(0L, "Getting table schema");
        if (SqlFetchMode.DISTRIBUTED.equals(_fetch_mode)) {
          rs = stmt.executeQuery(buildSelectSingleRowSql(_database_type, source_table, _columns));
        } else {
          // we use a simpler SQL-dialect independent query in the `streaming` mode because the goal is to be dialect independent
          stmt.setMaxRows(1);
          rs = stmt.executeQuery("SELECT " + _columns + " FROM " + source_table);
        }
        ResultSetMetaData rsmd = rs.getMetaData();
        numCol = rsmd.getColumnCount();

        columnNames = new String[numCol];
        columnH2OTypes = new byte[numCol];

        rs.next();
        for (int i = 0; i < numCol; i++) {
          columnNames[i] = rsmd.getColumnName(i + 1);
          //must iterate through sql types instead of getObject bc object could be null
          switch (rsmd.getColumnType(i + 1)) {
            case Types.NUMERIC:
            case Types.REAL:
            case Types.DOUBLE:
            case Types.FLOAT:
            case Types.DECIMAL:
              columnH2OTypes[i] = Vec.T_NUM;
              realcols += 1;
              break;
            case Types.INTEGER:
            case Types.TINYINT:
            case Types.SMALLINT:
            case Types.BIGINT:
              columnH2OTypes[i] = Vec.T_NUM;
              intcols += 1;
              break;
            case Types.BIT:
            case Types.BOOLEAN:
              columnH2OTypes[i] = Vec.T_NUM;
              bincols += 1;
              break;
            case Types.VARCHAR:
            case Types.NVARCHAR:
            case Types.CHAR:
            case Types.NCHAR:
            case Types.LONGVARCHAR:
            case Types.LONGNVARCHAR:
              columnH2OTypes[i] = Vec.T_STR;
              stringcols += 1;
              break;
            case Types.DATE:
            case Types.TIME:
            case Types.TIMESTAMP:
              columnH2OTypes[i] = Vec.T_TIME;
              timecols += 1;
              break;
            default:
              Log.warn("Unsupported column type: " + rsmd.getColumnTypeName(i + 1));
              columnH2OTypes[i] = Vec.T_BAD;
          }
        }

      } catch (SQLException ex) {
        Log.err(ex);
        throw new RuntimeException("SQLException: " + ex.getMessage() + "\nFailed to connect and read from SQL database with connection_url: " + _connection_url, ex);
      } finally {
        // release resources in a finally{} block in reverse-order of their creation
        if (rs != null) {
          try {
            rs.close();
          } catch (SQLException sqlEx) {} // ignore
          rs = null;
        }

        if (stmt != null) {
          try {
            stmt.close();
          } catch (SQLException sqlEx) {} // ignore
          stmt = null;
        }

        if (conn != null) {
          try {
            conn.close();
          } catch (SQLException sqlEx) {} // ignore
          conn = null;
        }
      }

      double binary_ones_fraction = 0.5; //estimate

      //create template vectors in advance and run MR
      final long totSize =
              (long)((float)(catcols+intcols)*numRow*4 //4 bytes for categoricals and integers
                      +(float)bincols          *numRow*1*binary_ones_fraction //sparse uses a fraction of one byte (or even less)
                      +(float)(realcols+timecols+stringcols) *numRow*8); //8 bytes for real and time (long) values

      final Vec vec;
      final int num_chunks;
      if (_num_chunks_hint == null) {
        final int chunk_size = FileVec.calcOptimalChunkSize(totSize, numCol, numCol * 4,
                H2O.ARGS.nthreads, H2O.getCloudSize(), false, true);
        final double rows_per_chunk = chunk_size; //why not numRow * chunk_size / totSize; it's supposed to be rows per chunk, not the byte size
        num_chunks = Vec.nChunksFor(numRow, (int) Math.ceil(Math.log1p(rows_per_chunk)), false);
        Log.info("Optimal calculated target number of chunks: " + num_chunks);
      } else {
        num_chunks = _num_chunks_hint;
        Log.info("Using user-specified target number of chunks: " + num_chunks);
      }

      if (SqlFetchMode.DISTRIBUTED.equals(_fetch_mode)) {
        final int num_retrieval_chunks = ConnectionPoolProvider.estimateConcurrentConnections(H2O.getCloudSize(), H2O.ARGS.nthreads);
        vec = num_retrieval_chunks >= num_chunks
                ? Vec.makeConN(numRow, num_chunks)
                : Vec.makeConN(numRow, num_retrieval_chunks);
      } else {
        vec = Vec.makeConN(numRow, num_chunks);
      }

      Log.info("Number of chunks for data retrieval: " + vec.nChunks() + ", number of rows: " + numRow);
      _j.setWork(vec.nChunks());

      // Finally read the data into an H2O Frame
      _j.update(0L, "Importing data");
      final ConnectionPoolProvider provider = new ConnectionPoolProvider(_connection_url, _username, _password, vec.nChunks());
      final Frame fr;

      if (SqlFetchMode.DISTRIBUTED.equals(_fetch_mode)) {
        fr = new SqlTableToH2OFrame(source_table, _database_type, _columns, columnNames, numCol, _j, provider)
                .doAll(columnH2OTypes, vec)
                .outputFrame(_destination_key, columnNames, null);
      } else {
        fr = new SqlTableToH2OFrameStreaming(source_table, _database_type, _columns, columnNames, numCol, _j, provider)
                .readTable(vec, columnH2OTypes, _destination_key);
      }
      vec.remove();

      DKV.put(fr);
      ParseDataset.logParseResults(fr);
      if (source_table.equals(_tempTableName))
        dropTempTable(_connection_url, _username, _password, source_table);
      tryComplete();
    }

  }
  
  static String createTempTableSql(String databaseType, String tableName, String selectQuery) {

      switch (databaseType) {
        case TERADATA_DB_TYPE:
          return "CREATE TABLE " + tableName + " AS (" + selectQuery + ") WITH DATA";

        default:
          return "CREATE TABLE " + tableName + " AS " + selectQuery;
      }
  }

  /**
   * Builds SQL SELECT to retrieve single row from a table based on type of database
   *
   * @param databaseType
   * @param table
   * @param columns
   * @return String SQL SELECT statement
   */
  static String buildSelectSingleRowSql(String databaseType, String table, String columns) {

    switch(databaseType) {
      case SQL_SERVER_DB_TYPE: //syntax supported since SQLServer 2008
        return "SELECT TOP(1) " + columns + " FROM " + table;

      case ORACLE_DB_TYPE:
        return "SELECT " + columns + " FROM " + table + " FETCH NEXT 1 ROWS ONLY";

      case TERADATA_DB_TYPE:
        return "SELECT TOP 1 " + columns + " FROM " + table;

      default:
        return "SELECT " + columns + " FROM " + table + " LIMIT 1";
    }
  }

  /**
   * Builds SQL SELECT to retrieve chunk of rows from a table based on row offset and number of rows in a chunk.
   *
   * Pagination in following Databases:
   *     SQL Server, Oracle 12c: OFFSET x ROWS FETCH NEXT y ROWS ONLY
   * SQL Server, Vertica may need ORDER BY
   *
   * MySQL, PostgreSQL, MariaDB: LIMIT y OFFSET x
   *
   * Teradata (and possibly older Oracle):
   *      SELECT * FROM mytable
   *         QUALIFY ROW_NUMBER() OVER (ORDER BY column_name) BETWEEN x and x+y;
   *
   * @param databaseType
   * @param table
   * @param start
   * @param length
   * @param columns
   * @param columnNames array of column names retrieved and parsed from single row SELECT prior to this call
   * @return String SQL SELECT statement
   */
  static String buildSelectChunkSql(String databaseType, String table, long start, int length, String columns, String[] columnNames) {

    String sqlText = "SELECT " + columns + " FROM " + table;
    switch(databaseType) {
      case SQL_SERVER_DB_TYPE:  // requires ORDER BY clause with OFFSET/FETCH NEXT clauses, syntax supported since SQLServer 2012
        sqlText += " ORDER BY ROW_NUMBER() OVER (ORDER BY (SELECT 0))";
        sqlText += " OFFSET " + start + " ROWS FETCH NEXT " + length + " ROWS ONLY";
        break;

      case ORACLE_DB_TYPE:
        sqlText += " OFFSET " + start + " ROWS FETCH NEXT " + length + " ROWS ONLY";
        break;

      case TERADATA_DB_TYPE:
        sqlText += " QUALIFY ROW_NUMBER() OVER (ORDER BY " + columnNames[0] + ") BETWEEN " + (start+1) + " AND " + (start+length);
        break;

      default:
        sqlText += " LIMIT " + length + " OFFSET " + start;
    }

    return sqlText;
  }


  static class ConnectionPoolProvider extends Iced<ConnectionPoolProvider> {

    private String _url;
    private String _user;
    private String _password;
    private int _nChunks;

    /**
     * Instantiates ConnectionPoolProvider
     * @param url       Database URL (JDBC format)
     * @param user      Database username
     * @param password  Username's password
     * @param nChunks   Number of chunks
     */
    ConnectionPoolProvider(String url, String user, String password, int nChunks) {
      _url = url;
      _user = user;
      _password = password;
      _nChunks = nChunks;
    }

    public ConnectionPoolProvider() {} // Externalizable classes need no-args constructor

    /**
     * Creates a connection pool for given target database, based on current H2O environment
     *
     * @return A connection pool, guaranteed to contain at least 1 connection per node if the database is reachable
     * @throws RuntimeException Thrown when database is unreachable
     */
    ArrayBlockingQueue<Connection> createConnectionPool() {
      return createConnectionPool(H2O.getCloudSize(), H2O.ARGS.nthreads);
    }

    Connection createConnection() throws SQLException {
      return getConnectionSafe(_url, _user, _password);
    }

    /**
     * Creates a connection pool for given target database, based on current H2O environment
     *
     * @param cloudSize Size of H2O cloud
     * @param nThreads  Number of maximum threads available
     * @return A connection pool, guaranteed to contain at least 1 connection per node if the database is reachable
     * @throws RuntimeException Thrown when database is unreachable
     */
    ArrayBlockingQueue<Connection> createConnectionPool(final int cloudSize, final short nThreads)
        throws RuntimeException {

      final int maxConnectionsPerNode = getMaxConnectionsPerNode(cloudSize, nThreads, _nChunks);
      Log.info("Database connections per node: " + maxConnectionsPerNode);
      final ArrayBlockingQueue<Connection> connectionPool = new ArrayBlockingQueue<Connection>(maxConnectionsPerNode);

      try {
        for (int i = 0; i < maxConnectionsPerNode; i++) {
          Connection conn = createConnection();
          connectionPool.add(conn);
        }
      } catch (SQLException ex) {
        throw new RuntimeException("SQLException: " + ex.getMessage() + "\nFailed to connect to SQL database with url: " + _url, ex);
      }

      return connectionPool;

    }

    private static int getMaxConnectionsTotal() {
      return getMaxConnectionsTotal(MAX_CONNECTIONS);
    }

    static int getMaxConnectionsTotal(final int allowedMaxConnections) {
      int maxConnections = allowedMaxConnections;
      final String userDefinedMaxConnections = System.getProperty(MAX_USR_CONNECTIONS_KEY);
      if (userDefinedMaxConnections != null) {
        try {
          final int userMaxConnections = Integer.parseInt(userDefinedMaxConnections);
          if (userMaxConnections > 0 && userMaxConnections < allowedMaxConnections) {
            maxConnections = userMaxConnections;
          }
        } catch (NumberFormatException e) {
          Log.warn("Unable to parse maximal number of connections: " + userDefinedMaxConnections
                  + ". Falling back to default settings (" + allowedMaxConnections + ").", e);
        }
      }
      Log.info("SQL import will be limited be maximum of " + maxConnections + " connections.");
      return maxConnections;
    }

    /**
     * @return Number of connections to an SQL database to be opened on a single node.
     */
    static int getMaxConnectionsPerNode(final int cloudSize, final short nThreads, final int nChunks) {
     return calculateLocalConnectionCount(getMaxConnectionsTotal(), cloudSize, nThreads, nChunks);
    }

    /**
     * Counts number of connections per node from give maximal number of connections for the whole cluster
     *
     * @param maxTotalConnections Maximal number of total connections to be opened by the whole cluster
     * @return Number of connections to open per node, within given minmal and maximal range
     */
    private static int calculateLocalConnectionCount(final int maxTotalConnections, final int cloudSize,
                                                           final short nThreads, final int nChunks) {
      int conPerNode = (int) Math.min(Math.ceil((double) nChunks / cloudSize), nThreads);
      conPerNode = Math.min(conPerNode, maxTotalConnections / cloudSize);
      //Make sure at least some connections are available to a node
      return Math.max(conPerNode, MIN_CONNECTIONS_PER_NODE);
    }

    /**
     * for data retrieval and rebalancing, use
     *   minimum 1 connection per node,
     *   maximum = min(max(total threads), max(total allowed connections))
     * t
     * @return an estimation of the optimal amount of total concurrent connections available to retrieve data
     */
    private static int estimateConcurrentConnections(final int cloudSize, final short nThreads) {
      return cloudSize * Math.min(nThreads, Math.max(getMaxConnectionsTotal() / cloudSize, MIN_CONNECTIONS_PER_NODE));
    }
  }

  /**
   * Makes sure the appropriate database driver is initialized before calling DriverManager#getConnection.
   * 
   * @param url JDBC connection string
   * @param username username
   * @param password password
   * @return a connection to the URL
   * @throws SQLException if a database access error occurs or the url is
   */
  public static Connection getConnectionSafe(String url, String username, String password) throws SQLException {
    validateJdbcUrl(url);
    initializeDatabaseDriver(getDatabaseType(url));
    try {
      return DriverManager.getConnection(url, username, password);
    } catch (NoClassDefFoundError e) {
      throw new RuntimeException("Failed to get database connection, probably due to using thin jdbc driver jar.", e);
    }
  }

  static String getDatabaseType(String url) {
    if (url == null)
      return null;
    String[] parts = url.split(":", 3);
    if (parts.length < 2)
      return null;
    return parts[1];
  }

  /**
   * Initializes database driver for databases with JDBC driver version lower than 4.0
   *
   * @param databaseType Name of target database from JDBC connection string
   */
  static void initializeDatabaseDriver(String databaseType) {
    String driverClass = System.getProperty(JDBC_DRIVER_CLASS_KEY_PREFIX + databaseType);
    if (driverClass != null) {
      Log.debug("Loading " + driverClass + " to initialize database of type " + databaseType);
      try {
        Class.forName(driverClass);
      } catch (ClassNotFoundException e) {
        throw new RuntimeException("Connection to '" + databaseType + "' database is not possible due to missing JDBC driver. " +
                "User specified driver class: " + driverClass, e);
      }
      return;
    }
    // use built-in defaults
    switch (databaseType) {
      case HIVE_DB_TYPE:
        try {
          Class.forName(HIVE_JDBC_DRIVER_CLASS);
        } catch (ClassNotFoundException e) {
          throw new RuntimeException("Connection to HIVE database is not possible due to missing JDBC driver.", e);
        }
        break;
      case NETEZZA_DB_TYPE:
        try {
          Class.forName(NETEZZA_JDBC_DRIVER_CLASS);
        } catch (ClassNotFoundException e) {
          throw new RuntimeException("Connection to Netezza database is not possible due to missing JDBC driver.", e);
        }
        break;
      default:
        //nothing to do
    }
  }

  public static void validateJdbcUrl(String jdbcUrl) throws IllegalArgumentException {
    if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
      throw new IllegalArgumentException("JDBC URL is null or empty");
    }

    if (!jdbcUrl.toLowerCase().startsWith("jdbc:")) {
      throw new IllegalArgumentException("JDBC URL must start with 'jdbc:'");
    }

    Matcher matcher = JDBC_PARAMETERS_REGEX_PATTERN.matcher(jdbcUrl);
    String property = System.getProperty(DISALLOWED_JDBC_PARAMETERS_PARAM);
    List<String> disallowedParameters = property == null ?
            DEFAULT_JDBC_DISALLOWED_PARAMETERS :
            Arrays.stream(property.split(",")).map(String::toLowerCase).collect(Collectors.toList());

    while (matcher.find()) {
      String key = matcher.group(1);
      if (disallowedParameters.contains(key.toLowerCase())) {
        throw new IllegalArgumentException("Potentially dangerous JDBC parameter found: " + key +
                ". That behavior can be altered by setting " + DISALLOWED_JDBC_PARAMETERS_PARAM + " env variable to another comma separated list.");
      }
    }
  }

  static class SqlTableToH2OFrameStreaming {
    final String _table, _columns, _databaseType;
    final int _numCol;
    final Job _job;
    final ConnectionPoolProvider _poolProvider;
    final String[] _columnNames;

    SqlTableToH2OFrameStreaming(final String table, final String databaseType,
                                final String columns, final String[] columnNames, final int numCol,
                                final Job job, final ConnectionPoolProvider poolProvider) {
      _table = table;
      _databaseType = databaseType;
      _columns = columns;
      _columnNames = columnNames;
      _numCol = numCol;
      _job = job;
      _poolProvider = poolProvider;
    }

    Frame readTable(Vec blueprint, byte[] columnTypes, Key<Frame> destinationKey) {
      Vec.VectorGroup vg = blueprint.group();
      int vecIdStart = vg.reserveKeys(columnTypes.length);

      AppendableVec[] res = new AppendableVec[columnTypes.length];
      long[] espc = MemoryManager.malloc8(blueprint.nChunks());
      for (int i = 0; i < res.length; ++i) {
        res[i] = new AppendableVec(vg.vecKey(vecIdStart + i), espc, columnTypes[i], 0);
      }

      String query = "SELECT " + _columns + " FROM " + _table;
      ResultSet rs = null;
      Futures fs = new Futures();
      try (Connection conn = _poolProvider.createConnection();
           Statement stmt = conn.createStatement()) {
        final int fetchSize = (int) Math.min(blueprint.chunkLen(0), 1e5);
        stmt.setFetchSize(fetchSize);
        rs = stmt.executeQuery(query);
        for (int cidx = 0; cidx < blueprint.nChunks(); cidx++) {
          if (_job.stop_requested()) 
            break;
          NewChunk[] ncs = new NewChunk[columnTypes.length];
          for (int i = 0; i < columnTypes.length; i++) {
            ncs[i] = res[i].chunkForChunkIdx(cidx);
          }
          final int len = blueprint.chunkLen(cidx);
          for (int r = 0; r < len && rs.next(); r++) {
            SqlTableToH2OFrame.writeRow(rs, ncs);
          }
          fs.add(H2O.submitTask(new FinalizeNewChunkTask(cidx, ncs)));
          _job.update(1);
        }
      } catch (SQLException e) {
        throw new RuntimeException("SQLException: " + e.getMessage() + "\nFailed to read SQL data", e);
      } finally {
        //close result set
        if (rs != null) {
          try {
            rs.close();
          } catch (SQLException sqlEx) {
            Log.trace(sqlEx);
          } // ignore
        }
      }
      fs.blockForPending();

      Vec[] vecs = AppendableVec.closeAll(res);
      if (vecs.length > 0 && vecs[0].length() != blueprint.length()) {
        Log.warn("Query `" + query + "` returned less rows than expected. " +
                "Actual: " + vecs[0].length() + ", expected: " + blueprint.length());
      }
      return new Frame(destinationKey, _columnNames, vecs);
    }

  }

  private static class FinalizeNewChunkTask extends H2O.H2OCountedCompleter<FinalizeNewChunkTask> {
    private final int _cidx;
    private transient NewChunk[] _ncs;

    FinalizeNewChunkTask(int cidx, NewChunk[] ncs) {
      _cidx = cidx;
      _ncs = ncs;
    }

    @Override
    public void compute2() {
      if (_ncs == null)
        throw new IllegalStateException("There are no chunks to work with!");

      Futures fs = new Futures();
      for (NewChunk nc : _ncs) {
        nc.close(_cidx, fs);
      }
      fs.blockForPending();

      tryComplete();
    }
  }

  static class SqlTableToH2OFrame extends MRTask<SqlTableToH2OFrame> {
    final String _table, _columns, _databaseType;
    final int _numCol;
    final Job _job;
    final ConnectionPoolProvider _poolProvider;
    final String[] _columnNames;

    transient ArrayBlockingQueue<Connection> sqlConn;

    public SqlTableToH2OFrame(final String table, final String databaseType,
                              final String columns, final String[] columnNames, final int numCol,
                              final Job job, final ConnectionPoolProvider poolProvider) {
      _table = table;
      _databaseType = databaseType;
      _columns = columns;
      _columnNames = columnNames;
      _numCol = numCol;
      _job = job;
      _poolProvider = poolProvider;
    }

    @Override
    protected void setupLocal() {
      sqlConn = _poolProvider.createConnectionPool();
    }

    @Override
    public void map(Chunk[] cs, NewChunk[] ncs) {
      if (isCancelled() || _job != null && _job.stop_requested()) return;
      //fetch data from sql table with limit and offset
      Connection conn = null;
      Statement stmt = null;
      ResultSet rs = null;
      Chunk c0 = cs[0];
      String sqlText = buildSelectChunkSql(_databaseType, _table, c0.start(), c0._len , _columns, _columnNames);
      try {
        conn = sqlConn.take();
        stmt = conn.createStatement();
        //set fetch size for best performance
        stmt.setFetchSize(c0._len);
        rs = stmt.executeQuery(sqlText);
        while (rs.next()) {
          writeRow(rs, ncs);
        }
      } catch (SQLException ex) {
        throw new RuntimeException("SQLException: " + ex.getMessage() + "\nFailed to read SQL data", ex);
      } catch (InterruptedException e) {
        throw new RuntimeException("Interrupted exception when trying to take connection from pool", e);
      } finally {

        //close result set
        if (rs != null) {
          try {
            rs.close();
          } catch (SQLException sqlEx) {
          } // ignore
          rs = null;
        }

        //close statement
        if (stmt != null) {
          try {
            stmt.close();
          } catch (SQLException sqlEx) {
          } // ignore
          stmt = null;
        }

        //return connection to pool
        sqlConn.add(conn);

      }
      if (_job != null) _job.update(1);
    }

    static void writeRow(ResultSet rs, NewChunk[] ncs) throws SQLException {
      for (int i = 0; i < ncs.length; i++) {
        Object res = rs.getObject(i + 1);
        writeItem(res, ncs[i]);
      }
    }

    static void writeItem(Object res, NewChunk nc) {
      if (res == null)
        nc.addNA();
      else {
        if (res instanceof Long || res instanceof Integer || res instanceof Short || res instanceof Byte)
          nc.addNum(((Number) res).longValue(), 0);
        else if (res instanceof Number)
          nc.addNum(((Number) res).doubleValue());
        else if (res instanceof Boolean)
          nc.addNum(((boolean) res ? 1 : 0), 0);
        else if (res instanceof String)
          nc.addStr(res);
        else if (res instanceof java.util.Date)
          nc.addNum(((java.util.Date) res).getTime(), 0);
        else
          nc.addNA();
      }
    }

    @Override
    protected void closeLocal() {
      try {
        for (Connection conn : sqlConn) {
          conn.close();
        }
      } catch (Exception ex) {
      } // ignore
    }
  }

  private static void dropTempTable(String connection_url, String username, String password, String tableName) {
    Connection conn = null;
    Statement stmt = null;

    String drop_table_query = "DROP TABLE " + tableName;
    try {
      conn = DriverManager.getConnection(connection_url, username, password);
      stmt = conn.createStatement();
      stmt.executeUpdate(drop_table_query);
    } catch (SQLException ex) {
      throw new RuntimeException("SQLException: " + ex.getMessage() + "\nFailed to execute SQL query: " + drop_table_query, ex);
    } finally {
      // release resources in a finally{} block in reverse-order of their creation
      if (stmt != null) {
        try {
          stmt.close();
        } catch (SQLException sqlEx) {
        } // ignore
        stmt = null;
      }

      if (conn != null) {
        try {
          conn.close();
        } catch (SQLException sqlEx) {
        } // ignore
        conn = null;
      }
    }
  }
}

