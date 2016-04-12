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
    String conUrl = "jdbc:mysql://localhost:3306/menagerie?&useSSL=false";
    final String table = "pet";
    String user = "root";
    String password = "ludi";
    boolean optimize = true;
    
    Frame f = SQLManager.importSqlTable(conUrl, table, user, password, optimize).get();
    
    Log.info("Number of rows: " + f.numRows());
    f.delete();
  }

}
