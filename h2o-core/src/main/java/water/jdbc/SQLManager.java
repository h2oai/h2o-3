package water.jdbc;

import water.*;
import water.fvec.*;
import water.parser.BufferedString;
import water.parser.ParseDataset;
import water.util.Log;

import java.math.BigDecimal;
import java.sql.*;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;

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

  /**
   * @param connection_url (Input) 
   * @param table (Input)
   * @param select_query (Input)
   * @param username (Input)
   * @param password (Input)
   * @param columns (Input)
   * @param sqlFetchMode (Input)
   */
  public static Job<Frame> importSqlTable(final String connection_url, String table, final String select_query,
                                          final String username, final String password, final String columns,
                                          final SqlFetchMode sqlFetchMode) {
    Connection conn = null;
    Statement stmt = null;
    ResultSet rs = null;
    final String databaseType = connection_url.split(":", 3)[1];
    initializeDatabaseDriver(databaseType);
    int catcols = 0, intcols = 0, bincols = 0, realcols = 0, timecols = 0, stringcols = 0;
    final int numCol;
    long numRow = 0;
    final String[] columnNames;
    final byte[] columnH2OTypes;
    try {
      conn = DriverManager.getConnection(connection_url, username, password);
      stmt = conn.createStatement();
      //set fetch size for improved performance
      stmt.setFetchSize(1);
      //if select_query has been specified instead of table
      if (table.equals("")) {
        if (!select_query.toLowerCase().startsWith("select")) {
          throw new IllegalArgumentException("The select_query must start with `SELECT`, but instead is: " + select_query);
        }

        //if tmp table disabled, we use sub-select instead, which outperforms tmp table for very large queries/tables
        // the main drawback of sub-selects is that we lose isolation, but as we're only reading data
        // and counting the rows from the beginning, it should not an issue (at least when using hive...)
        final boolean createTmpTable = Boolean.parseBoolean(System.getProperty(TMP_TABLE_ENABLED, "true")); //default to true to keep old behaviour
        if (createTmpTable) {
          table = SQLManager.TEMP_TABLE_NAME;
          //returns number of rows, but as an int, not long. if int max value is exceeded, result is negative
          numRow = stmt.executeUpdate("CREATE TABLE " + table + " AS " + select_query);
        } else {
          table = "(" + select_query + ") sub_h2o_import";
        }
      } else if (table.equals(SQLManager.TEMP_TABLE_NAME)) {
        //tables with this name are assumed to be created here temporarily and are dropped
        throw new IllegalArgumentException("The specified table cannot be named: " + SQLManager.TEMP_TABLE_NAME);
      }
      //get number of rows. check for negative row count
      if (numRow <= 0) {
        rs = stmt.executeQuery("SELECT COUNT(1) FROM " + table);
        rs.next();
        numRow = rs.getLong(1);
        rs.close();
      }
      //get H2O column names and types
      if (SqlFetchMode.DISTRIBUTED.equals(sqlFetchMode)) {
        rs = stmt.executeQuery(buildSelectSingleRowSql(databaseType, table, columns));
      } else {
        // we use a simpler SQL-dialect independent query in the `streaming` mode because the goal is to be dialect independent
        stmt.setMaxRows(1);
        rs = stmt.executeQuery("SELECT " + columns + " FROM " + table);
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
      throw new RuntimeException("SQLException: " + ex.getMessage() + "\nFailed to connect and read from SQL database with connection_url: " + connection_url, ex);
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
    final int chunk_size = FileVec.calcOptimalChunkSize(totSize, numCol, numCol * 4,
            H2O.ARGS.nthreads, H2O.getCloudSize(), false, false);
    final double rows_per_chunk = chunk_size; //why not numRow * chunk_size / totSize; it's supposed to be rows per chunk, not the byte size
    final int num_chunks = Vec.nChunksFor(numRow, (int) Math.ceil(Math.log1p(rows_per_chunk)), false);

    if (SqlFetchMode.DISTRIBUTED.equals(sqlFetchMode)) {
      final int num_retrieval_chunks = ConnectionPoolProvider.estimateConcurrentConnections(H2O.getCloudSize(), H2O.ARGS.nthreads);
      vec = num_retrieval_chunks >= num_chunks
              ? Vec.makeConN(numRow, num_chunks)
              : Vec.makeConN(numRow, num_retrieval_chunks);
    } else {
      vec = Vec.makeConN(numRow, num_chunks);
    }
    Log.info("Number of chunks for data retrieval: " + vec.nChunks() + ", number of rows:" + numRow);
    //create frame
    final Key<Frame> destination_key = Key.make((table + "_sql_to_hex").replaceAll("\\W", "_"));
    final Job<Frame> j = new Job(destination_key, Frame.class.getName(), "Import SQL Table");

    final String finalTable = table;
    H2O.H2OCountedCompleter work = new H2O.H2OCountedCompleter() {
      @Override
      public void compute2() {
        final ConnectionPoolProvider provider = new ConnectionPoolProvider(connection_url, username, password, vec.nChunks());
        final Frame fr;

        if (SqlFetchMode.DISTRIBUTED.equals(sqlFetchMode)) {
          fr = new SqlTableToH2OFrame(finalTable, databaseType, columns, columnNames, numCol, j, provider)
                  .doAll(columnH2OTypes, vec)
                  .outputFrame(destination_key, columnNames, null);
        } else {
          fr = new SqlTableToH2OFrameStreaming(finalTable, databaseType, columns, columnNames, numCol, j, provider)
                  .readTable(vec, columnH2OTypes, destination_key);
        }
        vec.remove();

        DKV.put(fr);
        ParseDataset.logParseResults(fr);
        if (finalTable.equals(SQLManager.TEMP_TABLE_NAME))
          dropTempTable(connection_url, username, password);
        tryComplete();
      }
    };
    j.start(work, vec.nChunks());

    return j;
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
      case ORACLE_DB_TYPE:
      case SQL_SERVER_DB_TYPE:
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
      case ORACLE_DB_TYPE:
      case SQL_SERVER_DB_TYPE:
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
      return DriverManager.getConnection(_url, _user, _password);
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
      int maxConnections = MAX_CONNECTIONS;
      final String userDefinedMaxConnections = System.getProperty(MAX_USR_CONNECTIONS_KEY);
      try {
        Integer userMaxConnections = Integer.valueOf(userDefinedMaxConnections);
        if (userMaxConnections > 0 && userMaxConnections < MAX_CONNECTIONS) {
          maxConnections = userMaxConnections;
        }
      } catch (NumberFormatException e) {
        Log.info("Unable to parse maximal number of connections: " + userDefinedMaxConnections
                + ". Falling back to default settings (" + MAX_CONNECTIONS + ").", e);
      }
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
        chunks: for (int cidx = 0; cidx < blueprint.nChunks(); cidx++) {
          if (_job.stop_requested()) break;
          NewChunk[] ncs = new NewChunk[columnTypes.length];
          for (int i = 0; i < columnTypes.length; i++) {
            ncs[i] = res[i].chunkForChunkIdx(cidx);
          }
          final int len = blueprint.chunkLen(cidx);
          int r = 0;
          while (r < len) {
            if (! rs.next()) {
              long totalLen = blueprint.espc()[cidx] + r;
              Log.warn("Query `" + query + "` returned less rows than expected. Actual: " + totalLen + ", expected: " + blueprint.length());
              break chunks;
            }
            SqlTableToH2OFrame.writeRow(rs, ncs);
            r++;
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
        if (res == null) ncs[i].addNA();
        else {
          switch (res.getClass().getSimpleName()) {
            case "Double":
              ncs[i].addNum((double) res);
              break;
            case "Integer":
              ncs[i].addNum((long) (int) res, 0);
              break;
            case "Long":
              ncs[i].addNum((long) res, 0);
              break;
            case "Float":
              ncs[i].addNum((double) (float) res);
              break;
            case "Short":
              ncs[i].addNum((long) (short) res, 0);
              break;
            case "Byte":
              ncs[i].addNum((long) (byte) res, 0);
              break;
            case "BigDecimal":
              ncs[i].addNum(((BigDecimal) res).doubleValue());
              break;
            case "Boolean":
              ncs[i].addNum(((boolean) res ? 1 : 0), 0);
              break;
            case "String":
              ncs[i].addStr(new BufferedString((String) res));
              break;
            case "Date":
              ncs[i].addNum(((Date) res).getTime(), 0);
              break;
            case "Time":
              ncs[i].addNum(((Time) res).getTime(), 0);
              break;
            case "Timestamp":
              ncs[i].addNum(((Timestamp) res).getTime(), 0);
              break;
            default:
              ncs[i].addNA();
          }
        }
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

  private static void dropTempTable(String connection_url, String username, String password) {
    Connection conn = null;
    Statement stmt = null;

    String drop_table_query = "DROP TABLE " + SQLManager.TEMP_TABLE_NAME;
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

