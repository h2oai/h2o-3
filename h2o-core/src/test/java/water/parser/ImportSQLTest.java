package water.parser;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.jdbc.SQLManager;
import water.jdbc.SqlFetchMode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ImportSQLTest extends TestUtil{
  //MySQL
  private String conUrl = "jdbc:mysql://172.16.2.178:3306/ingestSQL?&useSSL=false";
  String user = "root";
  String password = "0xdata";
  
  //postgresql
//  String conUrl = "jdbc:postgresql://mr-0xf2/ingestSQL";
//  String user = "postgres";
//  String password = "postgres";
  
  String select_query = "";
  String columns = "*";
  
  @BeforeClass
  static public void setup() {stall_till_cloudsize(1);}

  @Ignore @Test
  public void citibike20k() {
    String table = "citibike20k";
    Frame sql_f = SQLManager.importSqlTable(conUrl, table, select_query, user, password, columns, SqlFetchMode.DISTRIBUTED).get();
    assertTrue(sql_f.numRows() == 2e4);
    assertTrue(sql_f.numCols() == 15);
    sql_f.delete();
    sql_f = SQLManager.importSqlTable(conUrl, table, select_query, user, password, "bikeid, starttime", SqlFetchMode.DISTRIBUTED).get();
    assertTrue(sql_f.numRows() == 2e4);
    assertTrue(sql_f.numCols() == 2);
    sql_f.delete();
  }
  
  @Ignore @Test
  public void allSQLTypes() {
    String table = "allSQLTypes";
    Frame sql_f = SQLManager.importSqlTable(conUrl, table, select_query, user, password, columns, SqlFetchMode.DISTRIBUTED).get();
    sql_f.delete();
    
  }
  
  @Ignore @Test
  public void airlines() {
    String conUrl = "jdbc:mysql://localhost:3306/menagerie?&useSSL=false";
    String table = "air";
    String password = "ludi";
    Frame sql_f = SQLManager.importSqlTable(conUrl, table, select_query, user, password, columns, SqlFetchMode.DISTRIBUTED).get();
    sql_f.delete();
  }

  @Ignore @Test
  public void select_query() {
    Frame sql_f = SQLManager.importSqlTable(conUrl, "", "SELECT bikeid from citibike20k", user, password, columns, SqlFetchMode.DISTRIBUTED).get();
    assertTrue(sql_f.numCols() == 1);
    assertTrue(sql_f.numRows() == 2e4);
    sql_f.delete();
  }

}
