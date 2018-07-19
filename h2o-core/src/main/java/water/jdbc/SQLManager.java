package water.jdbc;

import water.*;
import water.fvec.*;
import water.parser.BufferedString;
import water.parser.ParseDataset;
import water.util.Log;

import jsr166y.CountedCompleter;

import java.math.BigDecimal;
import java.sql.*;
import java.util.concurrent.ArrayBlockingQueue;

import static water.fvec.Vec.makeCon;

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

  private static final String NETEZZA_JDBC_DRIVER_CLASS = "org.netezza.Driver";
  private static final String HIVE_JDBC_DRIVER_CLASS = "org.apache.hive.jdbc.HiveDriver";

  private static final String MAX_CHUNK_LOG2_SIZE = H2O.OptArgs.SYSTEM_PROP_PREFIX + "sql.max.chunk.log2.size";
  private static final String AUTO_REBALANCE_ENABLED = H2O.OptArgs.SYSTEM_PROP_PREFIX + "sql.rebalance.enabled";
  private static final String TMP_TABLE_ENABLED = H2O.OptArgs.SYSTEM_PROP_PREFIX + "sql.tmp.table.enabled";

  /**
   * @param connection_url (Input) 
   * @param table (Input)
   * @param select_query (Input)
   * @param username (Input)
   * @param password (Input)
   * @param columns (Input)
   * @param optimize (Input)                
   */
  public static Job<Frame> importSqlTable(final String connection_url, String table, final String select_query,
                                          final String username, final String password, final String columns,
                                          boolean optimize) {
    Connection conn = null;
    Statement stmt = null;
    ResultSet rs = null;
    /** Pagination in following Databases:
    SQL Server, Oracle 12c: OFFSET x ROWS FETCH NEXT y ROWS ONLY
     SQL Server, Vertica may need ORDER BY
    MySQL, PostgreSQL, MariaDB: LIMIT y OFFSET x 
    ? Teradata (and possibly older Oracle): 
     SELECT * FROM (
        SELECT ROW_NUMBER() OVER () AS RowNum_, <table>.* FROM <table>
     ) QUALIFY RowNum_ BETWEEN x and x+y;
     */
    String databaseType = connection_url.split(":", 3)[1];
    initializeDatabaseDriver(databaseType);
    final boolean needFetchClause = ORACLE_DB_TYPE.equals(databaseType) || SQL_SERVER_DB_TYPE.equals(databaseType);
    long numRow = 0;
    final RowDesc rd;

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
     //get H2O column names and types
      if (needFetchClause)
        rs = stmt.executeQuery("SELECT " + columns + " FROM " + table + " FETCH NEXT 1 ROWS ONLY");
      else
        rs = stmt.executeQuery("SELECT " + columns + " FROM " + table + " LIMIT 1");

      rd = RowDesc.fromResultSetMetaData(rs.getMetaData());

      rs.next();
      rs.close();

      if (numRow <= 0) {
        rs = stmt.executeQuery("SELECT COUNT(1) FROM " + table);
        rs.next();
        numRow = rs.getLong(1);
        rs.close();
      }

    } catch (SQLException ex) {
      if (table.equals(SQLManager.TEMP_TABLE_NAME))
        dropTempTable(connection_url, username, password);

      throw new RuntimeException("SQLException: " + ex.getMessage() + "\nFailed to connect and read from SQL database with connection_url: " + connection_url);
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

    //create template vectors in advance and run MR
    final long totSize = numRow * rd.rowSize();
    final Vec _retrieval_v;
    final int chunk_size = FileVec.calcOptimalChunkSize(totSize, rd.numCol, rd.numCol * 4,
            H2O.ARGS.nthreads, H2O.getCloudSize(), false, false);
    final double rows_per_chunk = chunk_size; //why not numRow * chunk_size / totSize; it's supposed to be rows per chunk, not the byte size
    final Vec _v = makeCon(0, numRow, (int) Math.ceil(Math.log1p(rows_per_chunk)), false);

    if (optimize) {
      //for single and small number of nodes (~3), retrieval is optimal on hive for maxChunkLog2Size=27 (instead of default 28)
//      final int maxChunkLog2Size = Integer.parseInt(System.getProperty(MAX_CHUNK_LOG2_SIZE, "0"));
//      _retrieval_v = maxChunkLog2Size > 0 ? makeCon(totSize, numRow, 1<<maxChunkLog2Size) : makeCon(totSize, numRow);

      // for optimal retrieval and rebalancing, use min 1 chunk per node, and max chunks = min(max(total threads), max(total connections))
      final int num_retrieval_chunks = H2O.getCloudSize() * ConnectionPoolProvider.getOptimalConnectionPerNode(H2O.getCloudSize(), H2O.ARGS.nthreads);
      _retrieval_v = num_retrieval_chunks > _v.nChunks() ? _v : makeCon(numRow, num_retrieval_chunks);
    } else {
      _retrieval_v = _v;
    }
    //if autoRebalance set to true, we first use optimal #chunks for retrieval and then immediately rebalance to optimal #chunks for later processing
    final boolean autoRebalance = _v != _retrieval_v && Boolean.parseBoolean(System.getProperty(AUTO_REBALANCE_ENABLED, "false"));
    Log.info("Number of chunks for data retrieval: " + _retrieval_v.nChunks());
    Log.info("Number of final chunks: " + (autoRebalance ? _v.nChunks() : _retrieval_v.nChunks()));
    //create frame
    final Key destination_key = Key.make((table + "_sql_to_hex").replaceAll("\\W", "_"));
    final Job<Frame> j = new Job(destination_key, Frame.class.getName(), "Import SQL Table");

    final String finalTable = table;
    H2O.H2OCountedCompleter work = new H2O.H2OCountedCompleter() {
      @Override
      public void compute2() {
        final ConnectionPoolProvider provider = new ConnectionPoolProvider(connection_url, username, password, _retrieval_v.nChunks());
        final Frame fr;
        final Key k = Key.make();
        final Frame retrieval_fr = new SqlTableToH2OFrame(finalTable, needFetchClause, columns, rd , j, provider)
                .doAll(rd.columnH2OTypes, _retrieval_v)
                .outputFrame(k, rd.columnNames, rd.getDomains());

        if (autoRebalance) {
          final RebalanceDataSet rds = new RebalanceDataSet(retrieval_fr, destination_key, _v.nChunks());
          H2O.submitTask(rds).join();
          fr = rds.getResult();
        } else {
          fr = new Frame(destination_key, retrieval_fr.names(), retrieval_fr.vecs());
        }
        Frame.deleteTempFrameAndItsNonSharedVecs(retrieval_fr, fr);
        _retrieval_v.remove();
        _v.remove();

        DKV.put(fr);
        ParseDataset.logParseResults(fr);
        tryComplete();
      }

      @Override
      public void onCompletion(CountedCompleter caller) {
        if (finalTable.equals(SQLManager.TEMP_TABLE_NAME))
          dropTempTable(connection_url, username, password);
        super.onCompletion(caller);
      }

      @Override
      public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller) {
        if (finalTable.equals(SQLManager.TEMP_TABLE_NAME))
          dropTempTable(connection_url, username, password);
        return super.onExceptionalCompletion(ex, caller);
      }
    };
    j.start(work, _retrieval_v.nChunks());
    
    return j;
  }

  static class RowDesc extends Iced<RowDesc> {
    private static final double BINARY_ONES_FRACTION = 0.5; //estimate

    static RowDesc fromResultSetMetaData(ResultSetMetaData rsmd) throws SQLException {
      final RowDesc rd = new RowDesc(rsmd.getColumnCount());

      for (int i = 0; i < rd.numCol; i++) {
        rd.columnNames[i] = rsmd.getColumnName(i + 1);
        //must iterate through sql types instead of getObject bc object could be null
        switch (rsmd.getColumnType(i + 1)) {
          case Types.NUMERIC:
          case Types.REAL:
          case Types.DOUBLE:
          case Types.FLOAT:
          case Types.DECIMAL:
            rd.columnH2OTypes[i] = Vec.T_NUM;
            rd.real_c += 1;
            break;
          case Types.INTEGER:
          case Types.TINYINT:
          case Types.SMALLINT:
          case Types.BIGINT:
            rd.columnH2OTypes[i] = Vec.T_NUM;
            rd.int_c += 1;
            break;
          case Types.BIT:
          case Types.BOOLEAN:
            rd.columnH2OTypes[i] = Vec.T_NUM;
            rd.binary_c += 1;
            break;
          case Types.VARCHAR:
          case Types.NVARCHAR:
          case Types.CHAR:
          case Types.NCHAR:
          case Types.LONGVARCHAR:
          case Types.LONGNVARCHAR:
            rd.columnH2OTypes[i] = Vec.T_STR;
            rd.string_c += 1;
            break;
          case Types.DATE:
          case Types.TIME:
          case Types.TIMESTAMP:
            rd.columnH2OTypes[i] = Vec.T_TIME;
            rd.time_c += 1;
            break;
          default:
            Log.warn("Unsupported column type: " + rsmd.getColumnTypeName(i + 1));
            rd.columnH2OTypes[i] = Vec.T_BAD;
        }
      }

      return rd;
    }

    final int numCol;
    final String[] columnNames;
    final byte[] columnH2OTypes;

    int categorical_c = 0, int_c = 0, binary_c = 0, real_c = 0, time_c = 0, string_c = 0;

    private RowDesc(int numCol) {
        this.numCol = numCol;
        this.columnNames = new String[numCol];
        this.columnH2OTypes = new byte[numCol];
    }

    int rowSize() {
      return (
              (this.categorical_c + this.int_c) * 4               //4 bytes for categoricals and integer
              + (int)(this.binary_c * 1 * BINARY_ONES_FRACTION)   //sparse uses a fraction of one byte (or even less)
              + (this.real_c + this.time_c + this.string_c) * 8   //8 bytes for real and time (long) values
      );
    }

    String[][] getDomains() {
      return null;
    }
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
          Connection conn = DriverManager.getConnection(_url, _user, _password);
          connectionPool.add(conn);
        }
      } catch (SQLException ex) {
        throw new RuntimeException("SQLException: " + ex.getMessage() + "\nFailed to connect to SQL database with url: " + _url);
      }

      return connectionPool;

    }

    static int getMaxConnectionsTotal() {
      int maxConnections = MAX_CONNECTIONS;
      final String userDefinedMaxConnections = System.getProperty(MAX_USR_CONNECTIONS_KEY);
      try {
        Integer userMaxConnections = Integer.valueOf(userDefinedMaxConnections);
        if (userMaxConnections > 0 && userMaxConnections < MAX_CONNECTIONS) {
          maxConnections = userMaxConnections;
        }
      } catch (NumberFormatException e) {
        Log.info("Unable to parse maximal number of connections: " + userDefinedMaxConnections
                + ". Falling back to default settings.");
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

    static int getOptimalConnectionPerNode(final int cloudSize, final short nThreads) {
      return Math.min(nThreads, Math.max(getMaxConnectionsTotal() / cloudSize, MIN_CONNECTIONS_PER_NODE));
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
                "User specified driver class: " + driverClass);
      }
      return;
    }
    // use built-in defaults
    switch (databaseType) {
      case HIVE_DB_TYPE:
        try {
          Class.forName(HIVE_JDBC_DRIVER_CLASS);
        } catch (ClassNotFoundException e) {
          throw new RuntimeException("Connection to HIVE database is not possible due to missing JDBC driver.");
        }
        break;
      case NETEZZA_DB_TYPE:
        try {
          Class.forName(NETEZZA_JDBC_DRIVER_CLASS);
        } catch (ClassNotFoundException e) {
          throw new RuntimeException("Connection to Netezza database is not possible due to missing JDBC driver.");
        }
        break;
      default:
        //nothing to do
    }
  }

  static class SqlTableToH2OFrame extends MRTask<SqlTableToH2OFrame> {
    final String _table, _columns;
    final boolean _needFetchClause;
    final RowDesc _rowDesc;
    final Job _job;
    final ConnectionPoolProvider _poolProvider;

    transient ArrayBlockingQueue<Connection> sqlConn;

    public SqlTableToH2OFrame(final String table, final boolean needFetchClause, final String columns, final RowDesc rowDesc,
                              final Job job, final ConnectionPoolProvider poolProvider) {
      _table = table;
      _needFetchClause = needFetchClause;
      _columns = columns;
      _rowDesc = rowDesc;
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
      String sqlText = "SELECT " + _columns + " FROM " + _table;
      if (_needFetchClause)
        sqlText += " OFFSET " + c0.start() + " ROWS FETCH NEXT " + c0._len + " ROWS ONLY";
      else
        sqlText += " LIMIT " + c0._len + " OFFSET " + c0.start();
      try {
        conn = sqlConn.take();
        stmt = conn.createStatement();
        //set fetch size for best performance
        stmt.setFetchSize(c0._len);
        rs = stmt.executeQuery(sqlText);
        while (rs.next()) {
          for (int i = 0; i < _rowDesc.numCol; i++) {
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
                  BufferedString bs = new BufferedString((String) res);
                  ncs[i].addStr(bs);
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
      } catch (SQLException ex) {
        throw new RuntimeException("SQLException: " + ex.getMessage() + "\nFailed to read SQL data");
      } catch (InterruptedException e) {
        e.printStackTrace();
        throw new RuntimeException("Interrupted exception when trying to take connection from pool");
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
      throw new RuntimeException("SQLException: " + ex.getMessage() + "\nFailed to execute SQL query: " + drop_table_query);
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

