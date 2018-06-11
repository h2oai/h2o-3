package water.jdbc;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ProvideSystemProperty;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import water.H2O;
import water.Paxos;

public class SQLManagerTest {

  @Rule
  public final ProvideSystemProperty provideSystemProperty =
      new ProvideSystemProperty(H2O.OptArgs.SYSTEM_PROP_PREFIX + "sql.connections.max", "7");

  @Rule
  public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties();

  @Before
  public void setUp() throws Exception {
    Paxos._commonKnowledge = true;
  }

  @Test
  public void testConnectionPoolSize() throws Exception {
    SQLManager.SqlTableToH2OFrame frame = new SQLManager.SqlTableToH2OFrame("", "", false,
        "", "", "", 1, 10, null);
    frame._cloudSize = 1;
    frame._nthreads = 100;
    Integer maxConnectionsPerNode = frame.getMaxConnectionsPerNode();
    //Even if there are 100 available processors on a single node, there should be only limited number of connections
    // in the pool.
    Assert.assertEquals(Integer.valueOf(System.getProperty(H2O.OptArgs.SYSTEM_PROP_PREFIX + "sql.connections.max")),
        maxConnectionsPerNode);
  }

  @Test
  public void testConnectionPoolSizeOneProcessor() throws Exception {
    SQLManager.SqlTableToH2OFrame frame = new SQLManager.SqlTableToH2OFrame("", "", false,
        "", "", "", 1, 10, null);
    frame._cloudSize = 1;
    frame._nthreads = 1;
    int maxConnectionsPerNode = frame.getMaxConnectionsPerNode();
    //The user-defined limit for number of connections in the pool is 7, however there is only one processor.
    Assert.assertEquals(1,
        maxConnectionsPerNode);
  }

  /**
   * Tests if there is at least one connection in the pool instantiated for each node, even if number of available
   * processors is a number lower than 1.
   */
  @Test
  public void testConnectionPoolSizeZeroProcessors() throws Exception {
    SQLManager.SqlTableToH2OFrame frame = new SQLManager.SqlTableToH2OFrame("", "", false,
        "", "", "", 1, 10, null);
    frame._cloudSize = 1;
    frame._nthreads = -1;
    int maxConnectionsPerNode = frame.getMaxConnectionsPerNode();
    Assert.assertEquals(1,
        maxConnectionsPerNode);
  }

  @Test
  public void testConnectionPoolSizeTwoNodes() throws Exception {
    H2O.ARGS.nthreads = 10;
    SQLManager.SqlTableToH2OFrame frame = new SQLManager.SqlTableToH2OFrame("", "", false,
        "", "", "", 1, 10, null);
    frame._nthreads = 10;
    frame._cloudSize = 2;
    int maxConnectionsPerNode = frame.getMaxConnectionsPerNode();
    int expectedConnectionsPerNode = Integer.valueOf(
        System.getProperty(H2O.OptArgs.SYSTEM_PROP_PREFIX + "sql.connections.max")
    ).intValue() / 2;
    Assert.assertEquals(expectedConnectionsPerNode, maxConnectionsPerNode);
  }

}