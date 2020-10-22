package water.parser;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.TestUtil;
import water.fvec.Frame;
import water.jdbc.SQLManager;
import water.jdbc.SqlFetchMode;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.assertTrue;

import org.h2.tools.Server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;

@RunWith(H2ORunner.class)
@CloudSize(2)
public class ImportSQLTest {
  public static final int ROW_COUNT = 20_000_000;
  //MySQL
  static private String conUrl;
  static private Server server;
  String user = "sa";
  String password = "";
  
  //postgresql
//  String conUrl = "jdbc:postgresql://mr-0xf2/ingestSQL";
//  String user = "postgres";
//  String password = "postgres";
  
  String select_query = "";
  String columns = "*";
  
  @BeforeClass
  static public void setup() throws SQLException {
    startDB();
    fillDB();
  }

  private static void startDB() throws SQLException {
    server = Server.createTcpServer("-tcpAllowOthers", "-ifNotExists").start();
    server.start();
    String serverURL = server.getURL();
    System.out.println(serverURL);
    
    conUrl = "jdbc:h2:" + serverURL + "/~/test.t";
  }

  private static void fillDB() throws SQLException {
    Connection connection = DriverManager.
            getConnection(conUrl, "sa", "");
    // add application code here

    connection.createStatement().executeUpdate("CREATE TABLE foo (id INTEGER not NULL, i INTEGER, s VARCHAR(256));");
    
    connection.setAutoCommit(false);
    
    final String query = "INSERT INTO foo (id, i, s) VALUES (?, ?, ?)";
    
    PreparedStatement ps = connection.prepareStatement(query);
    for (int i = 0; i < ROW_COUNT; i++) {
      ps.setInt(1, i);
      ps.setInt(2, i);
      ps.setString(3, "__" + i + "__");
      ps.addBatch();
    }
    ps.executeBatch();
    connection.commit();

    connection.close();
  }


    @AfterClass
  static public void shutdown() throws SQLException {
    Connection connection = DriverManager.getConnection(conUrl, "sa", "");
    connection.createStatement().executeUpdate("DROP TABLE foo;");
    server.shutdown();
  }

  @Test
  public void citibike20k() {
    String table = "citibike20k";
    Frame sql_f = SQLManager.importSqlTable(conUrl, "foo", select_query, user, password, columns, null, null, SqlFetchMode.DISTRIBUTED, 5).get();
    assertTrue(sql_f.numRows() == ROW_COUNT);
    System.out.println(Arrays.toString(sql_f.names()));
    double min = sql_f.vec("I").min();
    System.out.println(min);
    assertTrue(min == 0);
    double max = sql_f.vec("I").max();
    System.out.println(max);
    assertTrue(max == (20_000_000.0 - 1));
//    assertTrue(sql_f.numCols() == 15);
//    sql_f.delete();
//    sql_f = SQLManager.importSqlTable(conUrl, table, select_query, user, password, "bikeid, starttime", null, null, SqlFetchMode.DISTRIBUTED, null).get();
//    assertTrue(sql_f.numRows() == 2e4);
//    assertTrue(sql_f.numCols() == 2);
    sql_f.delete();
  }
  
  @Ignore @Test
  public void allSQLTypes() {
    String table = "allSQLTypes";
    Frame sql_f = SQLManager.importSqlTable(conUrl, table, select_query, user, password, columns, null, null, SqlFetchMode.DISTRIBUTED, null).get();
    sql_f.delete();
    
  }
  
  @Ignore @Test
  public void airlines() {
    String conUrl = "jdbc:mysql://localhost:3306/menagerie?&useSSL=false";
    String table = "air";
    String password = "ludi";
    Frame sql_f = SQLManager.importSqlTable(conUrl, table, select_query, user, password, columns, null, null, SqlFetchMode.DISTRIBUTED, null).get();
    sql_f.delete();
  }

  @Ignore @Test
  public void select_query() {
    Frame sql_f = SQLManager.importSqlTable(conUrl, "", "SELECT bikeid from citibike20k", user, password, columns, null, null, SqlFetchMode.DISTRIBUTED, null).get();
    assertTrue(sql_f.numCols() == 1);
    assertTrue(sql_f.numRows() == 2e4);
    sql_f.delete();
  }

}
