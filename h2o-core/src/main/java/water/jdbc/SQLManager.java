package water.jdbc;

import water.*;
import water.fvec.*;
import water.parser.BufferedString;
import water.util.Log;

import java.sql.*;
import java.util.concurrent.ArrayBlockingQueue;

import static water.fvec.Vec.makeCon;

public class SQLManager {


  /**
   * @param connection_url (Input) 
   * @param table (Input)
   * @param username (Input)
   * @param password (Input)
   * @param optimize (Input)                
   */
  public static Job<Frame> importSqlTable(final String connection_url, final String table, final String username, final String password,
                                          boolean optimize) {
    
    Connection conn = null;
    Statement stmt = null;
    ResultSet rs = null;

    int catcols = 0, intcols = 0, bincols = 0, realcols = 0, timecols = 0, stringcols = 0, numCol;
    final int maxCon; 
    long numRow;
    final String[] columnNames;
    final int[] columnSQLTypes;
    final byte[] columnH2OTypes;
    try {
      conn = DriverManager.getConnection(connection_url, username, password);
      stmt = conn.createStatement();
      rs = stmt.executeQuery("SELECT COUNT(1) FROM " + table);
      rs.next();
      numRow = rs.getInt(1);
      rs = stmt.executeQuery("SELECT @@max_connections");
      rs.next();
      maxCon = rs.getInt(1);
      rs = stmt.executeQuery("SELECT * FROM " + table + " LIMIT 1");
      ResultSetMetaData rsmd = rs.getMetaData();
      numCol = rsmd.getColumnCount();

      columnNames = new String[numCol];
      columnSQLTypes = new int[numCol];
      columnH2OTypes = new byte[numCol];

      for (int i = 0; i < numCol; i++) {
        columnNames[i] = rsmd.getColumnName(i + 1);
        int sqlType = rsmd.getColumnType(i + 1);
        columnSQLTypes[i] = sqlType;
        switch (sqlType) {
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

    H2O.H2OCountedCompleter work = new H2O.H2OCountedCompleter() {
      @Override
      public void compute2() {
        Frame fr = new SqlTableToH2OFrame(connection_url, table, username, password, columnSQLTypes, maxCon, _v.nChunks(), j).doAll(columnH2OTypes, _v)
                .outputFrame(destination_key, columnNames, null);
        DKV.put(fr);
        _v.remove();
        tryComplete();
      }
    };
    j.start(work, _v.nChunks());
    
    return j;
  }

  public static class SqlTableToH2OFrame extends MRTask<SqlTableToH2OFrame> {
    final String _url, _table, _user, _password;
    final int[] _sqlColumnTypes;
    final int _nChunks, _maxCon;
    final Job _job;

    transient ArrayBlockingQueue<Connection> sqlConn;

    public SqlTableToH2OFrame(String url, String table, String user, String password, int[] sqlColumnTypes, int maxCon, int nChunks, Job job) {
      _url = url;
      _table = table;
      _user = user;
      _password = password;
      _sqlColumnTypes = sqlColumnTypes;
      _maxCon = maxCon;
      _nChunks = nChunks;
      _job = job;

    }

    @Override
    protected void setupLocal() {
      int conPerNode = (int) Math.min(Math.ceil((double) _nChunks/ H2O.getCloudSize()), Runtime.getRuntime().availableProcessors());
      conPerNode = Math.min(conPerNode, _maxCon / H2O.getCloudSize()); //upper bound on number of connections
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
      String sqlText = "SELECT * FROM " + _table + " LIMIT " + c0._len + " OFFSET " + c0.start();
      try {
        conn = sqlConn.take();
        stmt = conn.createStatement();
        rs = stmt.executeQuery(sqlText);
        while (rs.next()) {
          for (int i = 0; i < _sqlColumnTypes.length; i++) {
            Object res = rs.getObject(i + 1);
            if (res == null) ncs[i].addNA();
            else
              switch (_sqlColumnTypes[i]) {
                case Types.NUMERIC:
                case Types.REAL:
                case Types.DOUBLE:
                case Types.FLOAT:
                case Types.DECIMAL:
                  ncs[i].addNum((double) res);
                  break;
                case Types.INTEGER:
                case Types.TINYINT:
                case Types.SMALLINT:
                case Types.BIGINT:
                  ncs[i].addNum((long) (int) res, 0);
                  break;
                case Types.BIT:
                case Types.BOOLEAN:
                  ncs[i].addNum(((boolean) res ? 1 : 0), 0);
                  break;
                case Types.VARCHAR:
                case Types.NVARCHAR:
                case Types.CHAR:
                case Types.NCHAR:
                case Types.LONGVARCHAR:
                case Types.LONGNVARCHAR:
                  ncs[i].addStr(new BufferedString((String) res));
                  break;
                case Types.DATE:
                case Types.TIME:
                case Types.TIMESTAMP:
                  ncs[i].addNum(((Date) res).getTime(), 0);
                  break;
                default:
                  ncs[i].addNA();
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

}

