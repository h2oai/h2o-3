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

  @Test
  public void testValidateJdbcConnectionStringMysqlKeyValuePairs() {
    exception.expect(IllegalArgumentException.class);
    exception.expectMessage("Potentially dangerous JDBC parameter found: autoDeserialize");

    String jdbcConnection = "jdbc:mysql://(host=127.0.0.1,port=3308,autoDeserialize=true,queryInterceptors=com.mysql.cj.jdbc.interceptors.ServerStatusDiffInterceptor,user=deser_CUSTOM,maxAllowedPacket=655360)";

    SQLManager.validateJdbcUrl(jdbcConnection);
  }

  @Test
  public void testValidateJdbcConnectionStringMysqlKeyValuePairsSpace() {
    exception.expect(IllegalArgumentException.class);
    exception.expectMessage("Potentially dangerous JDBC parameter found: autoDeserialize");

    String jdbcConnection = "jdbc:mysql://(host=127.0.0.1,port=3308, autoDeserialize  =  true,queryInterceptors = com.mysql.cj.jdbc.interceptors.ServerStatusDiffInterceptor,user=deser_CUSTOM,maxAllowedPacket=655360)";

    SQLManager.validateJdbcUrl(jdbcConnection);
  }

  @Test
  public void testValidateJdbcConnectionStringMysqlKeyValuePairsCAPITAL() {
    exception.expect(IllegalArgumentException.class);
    exception.expectMessage("Potentially dangerous JDBC parameter found: AUTODeserialize");

    String jdbcConnection = "jdbc:mysql://(host=127.0.0.1,port=3308, AUTODeserialize  =  true,queryInterceptors = com.mysql.cj.jdbc.interceptors.ServerStatusDiffInterceptor,user=deser_CUSTOM,maxAllowedPacket=655360)";

    SQLManager.validateJdbcUrl(jdbcConnection);
  }  

  @Test
  public void testValidateJdbcConnectionStringMysqlSpaceBetween() {
    exception.expect(IllegalArgumentException.class);
    exception.expectMessage("Potentially dangerous JDBC parameter found: allowLoadLocalInfile");

    String jdbcConnection = "jdbc:mysql://127.0.0.1:3306/mydb?allowLoadLocalInfile  =  true&  autoDeserialize=true";

    SQLManager.validateJdbcUrl(jdbcConnection);
  }

  @Test
  public void testValidateJdbcConnectionStringMysqlCAPITAL() {
    exception.expect(IllegalArgumentException.class);
    exception.expectMessage("Potentially dangerous JDBC parameter found: AUTODESERIALIZE");

    String jdbcConnection = "jdbc:mysql://127.0.0.1:3306/mydb?AUTODESERIALIZE  =  true&  allowLoadLocalInfile=true";

    SQLManager.validateJdbcUrl(jdbcConnection);
  }  

  @Test
  public void testValidateJdbcConnectionStringMysqlOneParameter() {
    exception.expect(IllegalArgumentException.class);
    exception.expectMessage("Potentially dangerous JDBC parameter found: allowLoadLocalInfile");

    String jdbcConnection = "jdbc:mysql://127.0.0.1:3306/mydb?allowLoadLocalInfile=true";

    SQLManager.validateJdbcUrl(jdbcConnection);
  }

  @Test
  public void testValidateJdbcConnectionStringMysqlDoubleEncodedString() {
    exception.expect(IllegalArgumentException.class);
    exception.expectMessage("Potentially dangerous JDBC parameter found: allowLoadLocalInfile");

    String jdbcConnection = "jdbc%3Amysql%3A%2F%2F127.0.0.1%3A3308%2Ftest%3F+%2561%256c%256c%256f%2577%254c%256f%2561%2564%254c%256f%2563%2561%256c%2549%256e%2566%2569%256c%2565%3Dtrue%26+%2561%256c%256c%256f%2577%2555%2572%256c%2549%256e%254c%256f%2563%2561%256c%2549%256e%2566%2569%256c%2565%3Dtrue&table=a&username=fileread_/etc/passwd&password=123123&fetch_mode=SINGLE";

    SQLManager.validateJdbcUrl(jdbcConnection);
  }

  @Test
  public void testValidateJdbcConnectionStringMysqlMultipleEncodedString() {
    exception.expect(IllegalArgumentException.class);
    exception.expectMessage("JDBC URL contains invalid characters");

    String jdbcConnection = "jdbc%2525252525252525253Amysql%2525252525252525253A%2525252525252525252F%2525252525252525252F127.0.0.1%2525252525252525253A3308%2525252525252525252Ftest%2525252525252525253F%25252525252525252B%2525252525252525252561%252525252525252525256c%252525252525252525256c%252525252525252525256f%2525252525252525252577%252525252525252525254c%252525252525252525256f%2525252525252525252561%2525252525252525252564%252525252525252525254c%252525252525252525256f%2525252525252525252563%2525252525252525252561%252525252525252525256c%2525252525252525252549%252525252525252525256e%2525252525252525252566%2525252525252525252569%252525252525252525256c%2525252525252525252565%2525252525252525253Dtrue%25252525252525252526%25252525252525252B%2525252525252525252561%252525252525252525256c%252525252525252525256c%252525252525252525256f%2525252525252525252577%2525252525252525252555%2525252525252525252572%252525252525252525256c%2525252525252525252549%252525252525252525256e%252525252525252525254c%252525252525252525256f%2525252525252525252563%2525252525252525252561%252525252525252525256c%2525252525252525252549%252525252525252525256e%2525252525252525252566%2525252525252525252569%252525252525252525256c%2525252525252525252565%2525252525252525253Dtrue%252525252525252526table%25252525252525253Da%252525252525252526username%25252525252525253Dfileread_%25252525252525252Fetc%25252525252525252Fpasswd%252525252525252526password%25252525252525253D123123%252525252525252526fetch_mode%25252525252525253DSINGLE";

    SQLManager.validateJdbcUrl(jdbcConnection);
  }

  /**
   * Test fail if any exception is thrown therefore no assert
   */
  @Test
  public void testValidateJdbcConnectionStringMysqlPass() {
    String jdbcConnection = "jdbc:mysql://127.0.0.1:3306/mydb?allowedParameter=true";
    SQLManager.validateJdbcUrl(jdbcConnection);
  }
}
