package water.parser;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.jdbc.SQLManager;
import water.util.Log;

public class JDBCTest extends TestUtil{
  @BeforeClass
  static public void setup() { stall_till_cloudsize(1);}

  @Ignore @Test
  public void run() {
    String database_sys = "mysql";
    String database = "menagerie";
    final String table = "pet";
    String user = "root";
    String password = "ludi";
    String host = "localhost";
    String port = "3306";
    boolean optimize = false;
    
    Frame f = SQLManager.importSqlTable(database_sys, database, table, user, password, host, port, optimize).get();
    Log.info("Number of rows: " + f.numRows());
    f.delete();
  }

}
