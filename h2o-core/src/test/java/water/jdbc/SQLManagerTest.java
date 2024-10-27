package water.jdbc;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ProvideSystemProperty;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.rules.ExpectedException;
import water.H2O;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ArrayBlockingQueue;

public class SQLManagerTest {

  private static final File BUILD_DIR = new File("build").getAbsoluteFile();

  @Rule
  public final ProvideSystemProperty provideSystemProperty =
      new ProvideSystemProperty(H2O.OptArgs.SYSTEM_PROP_PREFIX + "sql.connections.max", "7")
              .and(H2O.OptArgs.SYSTEM_PROP_PREFIX + "sql.jdbc.driver.testdb", "InvalidDriverClass");

  @Rule
  public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties();

  @Rule
  public final ExpectedException exception = ExpectedException.none();

  @BeforeClass
  public static void initDB() throws ClassNotFoundException {
    System.setProperty("derby.stream.error.file", new File(BUILD_DIR, "SQLManagerTest.derby.log").getAbsolutePath());
    Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
  }

  @Test
  public void testCreateConnectionPool() throws SQLException {
    final String connectionString = String.format("jdbc:derby:memory:SQLManagerTest_%d;create=true", System.currentTimeMillis());
    final SQLManager.ConnectionPoolProvider provider = new SQLManager.ConnectionPoolProvider(connectionString, "me", "mine", 10);
    ArrayBlockingQueue<Connection> pool = provider.createConnectionPool(1, (short) 100);

    Assert.assertNotNull(pool);
    Assert.assertEquals(7, pool.size());

    for (Connection c : pool) {
      c.close();
    }
  }

  @Test
  public void testInitializeDatabaseDriver() {
    exception.expect(RuntimeException.class);
    exception.expectMessage("Connection to 'testdb' database is not possible due to missing JDBC driver. User specified driver class: InvalidDriverClass");
    SQLManager.initializeDatabaseDriver("testdb");
  }

  @Test
  public void testConnectionPoolSize() {
    Integer maxConnectionsPerNode = SQLManager.ConnectionPoolProvider.getMaxConnectionsPerNode(1, (short) 100, 10);
    //Even if there are 100 available processors on a single node, there should be only limited number of connections
    // in the pool.
    Assert.assertEquals(Integer.valueOf(System.getProperty(H2O.OptArgs.SYSTEM_PROP_PREFIX + "sql.connections.max")),
        maxConnectionsPerNode);
  }

  @Test
  public void testConnectionPoolSizeOneProcessor() {
    int maxConnectionsPerNode = SQLManager.ConnectionPoolProvider.getMaxConnectionsPerNode(1, (short) 1, 10);
    //The user-defined limit for number of connections in the pool is 7, however there is only one processor.
    Assert.assertEquals(1,
        maxConnectionsPerNode);
  }

  /**
   * Tests if there is at least one connection in the pool instantiated for each node, even if number of available
   * processors is a number lower than 1.
   */
  @Test
  public void testConnectionPoolSizeZeroProcessors() {
    int maxConnectionsPerNode = SQLManager.ConnectionPoolProvider.getMaxConnectionsPerNode(1, (short) -1, 10);
    Assert.assertEquals(1,
        maxConnectionsPerNode);
  }

  @Test
  public void testConnectionPoolSizeTwoNodes() {
    int maxConnectionsPerNode = SQLManager.ConnectionPoolProvider.getMaxConnectionsPerNode(2, (short) 10, 10);
    int expectedConnectionsPerNode = Integer.parseInt(
        System.getProperty(H2O.OptArgs.SYSTEM_PROP_PREFIX + "sql.connections.max")
    ) / 2;
    Assert.assertEquals(expectedConnectionsPerNode, maxConnectionsPerNode);
  }

  @Test
  public void testGetMaxConnectionsTotal() {
    System.setProperty(H2O.OptArgs.SYSTEM_PROP_PREFIX + "sql.connections.max", "-10");
    Assert.assertEquals(77, SQLManager.ConnectionPoolProvider.getMaxConnectionsTotal(77));
    System.setProperty(H2O.OptArgs.SYSTEM_PROP_PREFIX + "sql.connections.max", "42");
    Assert.assertEquals(42, SQLManager.ConnectionPoolProvider.getMaxConnectionsTotal(66));
    System.setProperty(H2O.OptArgs.SYSTEM_PROP_PREFIX + "sql.connections.max", "89");
    Assert.assertEquals(88, SQLManager.ConnectionPoolProvider.getMaxConnectionsTotal(88));
    System.getProperties().remove(H2O.OptArgs.SYSTEM_PROP_PREFIX + "sql.connections.max");
    Assert.assertEquals(99, SQLManager.ConnectionPoolProvider.getMaxConnectionsTotal(99));
  }
  
  /**
   * Test generated SQL based on database type
   */
  @Test
  public void testBuildSelectOneRowSql() {
    // Oracle
    Assert.assertEquals("SELECT * FROM mytable FETCH NEXT 1 ROWS ONLY",
            SQLManager.buildSelectSingleRowSql("oracle","mytable","*"));

    // SQL Server
    Assert.assertEquals("SELECT TOP(1) * FROM mytable",
            SQLManager.buildSelectSingleRowSql("sqlserver", "mytable", "*"));

    // Teradata
    Assert.assertEquals("SELECT TOP 1 * FROM mytable",
            SQLManager.buildSelectSingleRowSql("teradata", "mytable", "*"));

    // default: PostgreSQL, MySQL
    Assert.assertEquals("SELECT * FROM mytable LIMIT 1",
            SQLManager.buildSelectSingleRowSql("", "mytable", "*"));
  }

  @Test
  public void testBuildSelectChunkSql() {
    // Oracle
    Assert.assertEquals("SELECT * FROM mytable OFFSET 0 ROWS FETCH NEXT 1310 ROWS ONLY",
            SQLManager.buildSelectChunkSql("oracle", "mytable", 0, 1310, "*", null));

    // SQL Server
    Assert.assertEquals("SELECT * FROM mytable ORDER BY ROW_NUMBER() OVER (ORDER BY (SELECT 0)) OFFSET 0 ROWS FETCH NEXT 1310 ROWS ONLY",
            SQLManager.buildSelectChunkSql("sqlserver", "mytable", 0, 1310, "*", null));

    // Teradata
    Assert.assertEquals("SELECT * FROM mytable QUALIFY ROW_NUMBER() OVER (ORDER BY id) BETWEEN 1 AND 1310",
            SQLManager.buildSelectChunkSql("teradata", "mytable", 0, 1310, "*", new String[]{"id","col1","col2"}));

    // default: PostgreSQL, mySQL
    Assert.assertEquals("SELECT * FROM mytable LIMIT 1310 OFFSET 0",
            SQLManager.buildSelectChunkSql("", "mytable", 0, 1310, "*", null));
  }

  @Test
  public void testValidateJdbcConnectionStringH2() {
    exception.expect(IllegalArgumentException.class);
    exception.expectMessage("Potentially dangerous JDBC parameter found: init");

    String h2MaliciousJdbc = "jdbc:h2:mem:test;MODE=MSSQLServer;init=CREATE ALIAS RBT AS '@groovy.transform.ASTTest(value={ assert java.lang.Runtime.getRuntime().exec(\"reboot\")" + "})" + "def rbt" + "'";

    SQLManager.validateJdbcUrl(h2MaliciousJdbc);
  }

  @Test
  public void testValidateJdbcConnectionStringMysql() {
    exception.expect(IllegalArgumentException.class);
    exception.expectMessage("Potentially dangerous JDBC parameter found: autoDeserialize");
    
    String mysqlMaliciousJdbc = "jdbc:mysql://domain:123/test?autoDeserialize=true&queryInterceptors=com.mysql.cj.jdbc.interceptors.ServerStatusDiffInterceptor&user=abcd";

    SQLManager.validateJdbcUrl(mysqlMaliciousJdbc);
  }
}
