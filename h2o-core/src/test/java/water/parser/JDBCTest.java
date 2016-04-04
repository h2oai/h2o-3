package water.parser;

import java.sql.*;

import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.*;
import water.util.Log;

import static water.fvec.Vec.makeCon;

public class JDBCTest extends TestUtil{
  @BeforeClass
  static public void setup() { stall_till_cloudsize(1);}
  
  @Test
  public void run() {
    Connection conn = null;
    Statement stmt = null;
    ResultSet rs = null;
    String url = null;

    String host = "localhost";
    String port = "3306";
    String database = "menagerie";
    final String table = "pet";
    String user = "root";
    String password = "ludi";

    int catcols = 0, intcols = 0, bincols = 0, realcols = 0, timecols = 0, stringcols = 0, numCol, numRow;
    String[] columnNames;
    int[] columnSQLTypes;
    byte[] columnH2OTypes;
    try {
      url = String.format("jdbc:mysql://%s:%s/%s?&useSSL=false", host, port, database);
      conn = DriverManager.getConnection(url, user, password);
      stmt = conn.createStatement();
      rs = stmt.executeQuery("SELECT COUNT(1) FROM " + table);
      rs.next();
      numRow = rs.getInt(1);
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
      throw new RuntimeException("SQLException: " + ex.getMessage() + "\nFailed to connect and read from SQL database with url: " + url);
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
    
    int rows_per_chunk = FileVec.calcOptimalChunkSize(
            (int)((float)(catcols+intcols)*numRow*4 //4 bytes for categoricals and integers
                    +(float)bincols          *numRow*1*binary_ones_fraction //sparse uses a fraction of one byte (or even less)
                    +(float)(realcols+timecols+stringcols) *numRow*8), //8 bytes for real and time (long) values
            numCol, numCol*4, Runtime.getRuntime().availableProcessors(), H2O.getCloudSize(), false);
    
    rows_per_chunk = 0;

    //create template vectors in advance and run MR
    Vec _v = makeCon(0, numRow, (int)Math.ceil(Math.log1p(rows_per_chunk)),false);
    
    //create frame
    Frame fr = new SqlTableToH2OFrame(host, port, database, table, user, password, columnSQLTypes).doAll(columnH2OTypes, _v)
            .outputFrame(Key.make(table + "_sql_to_hex"), columnNames, null);
    
    System.out.println(fr);
    if (fr != null) fr.delete();
    _v.remove();


  }

  public static class SqlTableToH2OFrame extends MRTask<SqlTableToH2OFrame> {
    final String _host, _port, _database, _table, _user, _password;
    final int[] _sqlColumnTypes;

    transient Connection conn;
    
    private SqlTableToH2OFrame(String host, String port, String database, String table, String user, String password,
                               int[] sqlColumnTypes) {
      _host = host;
      _port = port;
      _database = database;
      _table = table;
      _user = user;
      _password = password;
      _sqlColumnTypes = sqlColumnTypes;
      
    }

    @Override
    protected void setupLocal() {
      String url = null;
      try {
        url = String.format("jdbc:mysql://%s:%s/%s?&useSSL=false", _host, _port, _database);
        conn = DriverManager.getConnection(url, _user, _password);
      } catch (SQLException ex) {
        throw new RuntimeException("SQLException: " + ex.getMessage() + "\nFailed to connect to SQL database with url: "+ url);
      }
    }

    @Override
    public void map(Chunk[] cs, NewChunk[] ncs) {
      //fetch data from sql table with limit and offset
      Statement stmt = null;
      ResultSet rs = null;
      Chunk c0 = cs[0];
      String sqlText = "SELECT * FROM " + _table + " LIMIT " + c0._len + " OFFSET " + c0.start();
      try {
        stmt = conn.createStatement();
        rs = stmt.executeQuery(sqlText);

        while (rs.next()) {
          for (int i = 0; i < _sqlColumnTypes.length; i++) {
            Object res = rs.getObject(i+1);
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
                  ncs[i].addNum(((boolean) res ? 1: 0), 0);
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
      } finally {
        //close statment 
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
      }
    }

    @Override
    protected void closeLocal() {
      if (conn != null) {
        try {
          conn.close();
        } catch (SQLException sqlEx) {} // ignore
        conn = null;
      }
    }
  }
  
}
