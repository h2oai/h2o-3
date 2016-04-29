package water.jdbc;

import water.*;
import water.fvec.*;
import water.parser.BufferedString;
import water.parser.ParseDataset;
import water.util.Log;

import java.math.BigDecimal;
import java.sql.*;
import java.util.concurrent.ArrayBlockingQueue;

import static water.fvec.Vec.makeCon;

public class SQLManager {
  
  final static String TEMP_TABLE_NAME = "table_for_h2o_import";
  //upper bound on number of connections to database
  final static int MAX_CONNECTIONS = 100;
  
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
    String db_sys = connection_url.split(":",3)[1];
    final boolean needFetchClause = db_sys.equals("oracle") || db_sys.equals("sqlserver");
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
        table = SQLManager.TEMP_TABLE_NAME;
        //returns number of rows, but as an int, not long. if int max value is exceeded, result is negative
        numRow = stmt.executeUpdate("CREATE TABLE " + table + " AS " + select_query);
      } else if (table.equals(SQLManager.TEMP_TABLE_NAME)) {
        //tables with this name are assumed to be created here temporarily and are dropped
        throw new IllegalArgumentException("The specified table cannot be named: " + SQLManager.TEMP_TABLE_NAME);
      }
      //get number of rows. check for negative row count
      if (numRow <= 0) {
        rs = stmt.executeQuery("SELECT COUNT(1) FROM " + table);
        rs.next();
        numRow = rs.getLong(1);
      }
      //get H2O column names and types 
      if (needFetchClause)
        rs = stmt.executeQuery("SELECT " + columns + " FROM " + table + " FETCH NEXT 1 ROWS ONLY");
      else
        rs = stmt.executeQuery("SELECT " + columns + " FROM " + table + " LIMIT 1");
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

    double binary_ones_fraction = 0.5; //estimate

    //create template vectors in advance and run MR
    long totSize =
            (long)((float)(catcols+intcols)*numRow*4 //4 bytes for categoricals and integers
                    +(float)bincols          *numRow*1*binary_ones_fraction //sparse uses a fraction of one byte (or even less)
                    +(float)(realcols+timecols+stringcols) *numRow*8); //8 bytes for real and time (long) values
    final Vec _v;
    if (optimize) {
      _v = makeCon(totSize, numRow);
    } else {
      double rows_per_chunk = FileVec.calcOptimalChunkSize(totSize, numCol, numCol * 4,
              Runtime.getRuntime().availableProcessors(), H2O.getCloudSize(), false, false);
      _v = makeCon(0, numRow, (int) Math.ceil(Math.log1p(rows_per_chunk)), false);
    }
    Log.info("Number of chunks: " + _v.nChunks());
    //create frame
    final Key destination_key = Key.make(table + "_sql_to_hex");
    final Job<Frame> j = new Job(destination_key, Frame.class.getName(), "Import SQL Table");

    final String finalTable = table;
    H2O.H2OCountedCompleter work = new H2O.H2OCountedCompleter() {
      @Override
      public void compute2() {
        Frame fr = new SqlTableToH2OFrame(connection_url, finalTable, needFetchClause, username, password, columns, 
                numCol, _v.nChunks(), j).doAll(columnH2OTypes, _v)
                .outputFrame(destination_key, columnNames, null);
        DKV.put(fr);
        _v.remove();
        ParseDataset.logParseResults(fr);
        if (finalTable.equals(SQLManager.TEMP_TABLE_NAME)) 
          dropTempTable(connection_url, username, password);
        tryComplete();
      }
    };
    j.start(work, _v.nChunks());
    
    return j;
  }

  private static class SqlTableToH2OFrame extends MRTask<SqlTableToH2OFrame> {
    final String _url, _table, _user, _password, _columns;
    final int _numCol, _nChunks;
    final boolean _needFetchClause;
    final Job _job;

    transient ArrayBlockingQueue<Connection> sqlConn;

    public SqlTableToH2OFrame(String url, String table, boolean needFetchClause, String user, String password, 
                              String columns, int numCol, int nChunks, Job job) {
      _url = url;
      _table = table;
      _needFetchClause = needFetchClause;
      _user = user;
      _password = password;
      _columns = columns;
      _numCol = numCol;
      _nChunks = nChunks;
      _job = job;

    }

    @Override
    protected void setupLocal() {
      int conPerNode = (int) Math.min(Math.ceil((double) _nChunks / H2O.getCloudSize()), Runtime.getRuntime().availableProcessors());
      conPerNode = Math.min(conPerNode, SQLManager.MAX_CONNECTIONS / H2O.getCloudSize()); 
      Log.info("Database connections per node: " + conPerNode);
      sqlConn = new ArrayBlockingQueue<>(conPerNode);
      try {
        for (int i = 0; i < conPerNode; i++) {
          Connection conn = DriverManager.getConnection(_url, _user, _password);
          sqlConn.add(conn);
        }
      } catch (SQLException ex) {
        throw new RuntimeException("SQLException: " + ex.getMessage() + "\nFailed to connect to SQL database with url: " + _url);
      }
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
          for (int i = 0; i < _numCol; i++) {
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

