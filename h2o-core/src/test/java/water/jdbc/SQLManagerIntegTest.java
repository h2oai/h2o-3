package water.jdbc;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import water.Job;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.Assert.assertArrayEquals;

public class SQLManagerIntegTest extends TestUtil {

  private static final File BUILD_DIR = new File("build").getAbsoluteFile();

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder(BUILD_DIR);

  private String connectionString;

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @BeforeClass
  public static void initDB() throws ClassNotFoundException {
    System.setProperty("derby.stream.error.file", new File(BUILD_DIR, "SQLManagerIntegTest.derby.log").getAbsolutePath());
    Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
  }

  @Before
  public void createTable() throws Exception  {
    connectionString = String.format("jdbc:derby:%s/SQLManagerIntegTest_DB;create=true", tmp.newFolder("derby").getAbsolutePath());
    try (Connection conn = DriverManager.getConnection(connectionString);
         Statement stmt = conn.createStatement()) {

      stmt.executeUpdate("CREATE TABLE TestData (ID INT PRIMARY KEY, NAME VARCHAR(12))");
      stmt.executeUpdate("INSERT INTO TestData VALUES (1,'TOM'),(2,'BILL'),(3,'AMY'),(4,'OWEN')");
    }
  }

  @Test
  public void importSqlTable() {
    Scope.enter();
    try {
      Frame expected = Scope.track(new TestFrameBuilder()
              .withColNames("ID", "NAME")
              .withVecTypes(Vec.T_NUM, Vec.T_STR)
              .withDataForCol(0, new long[]{1, 2, 3, 4})
              .withDataForCol(1, new String[]{"TOM", "BILL", "AMY", "OWEN"})
              .build());

      Job<Frame> j = SQLManager.importSqlTable(connectionString, "TestData", "", "", "", "*", SqlFetchMode.SINGLE);
      Frame fr = Scope.track(j.get());

      assertArrayEquals(expected._names, fr._names);
      assertVecEquals(expected.vec(0), fr.vec(0), 0);
      assertStringVecEquals(expected.vec(1), fr.vec(1));
    } finally {
      Scope.exit();
    }
  }

}
