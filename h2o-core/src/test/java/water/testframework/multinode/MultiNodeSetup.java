package water.testframework.multinode;

import org.testng.annotations.BeforeSuite;
import water.H2O;

public class MultiNodeSetup {
  /**
   * Number of total H2O nodes to start.
   *
   * Note that all nodes are started within one process by this harness.
   * This approach is good for unit testing.
   * You would not want to deploy to production like this.
   *
   * Each H2O node is wrapped in it's own classloader.
   * The H2O nodes communicate with each other using sockets even though
   * they all live in the same process.
   */
  int _numNodes;

  public MultiNodeSetup(int numNodes) {
    _numNodes = numNodes;
  }

  /**
   * Start 1 leader node.
   * Start n-1 secondary nodes.
   *
   * The primary node blocks for cloud initialization and runs the test
   * program flow inside TestNG.
   */
  @BeforeSuite(groups={"multi-node"})
  public void setupCloud() {
    System.out.println("MultiNodeSetup creating " + _numNodes + " nodes...");

    // Leader node, where the tests execute from.
    H2O.main(new String[]{});

    // Secondary nodes, skip if expected to be pre-built
    if( System.getProperty("ai.h2o.skipNodeCreation") == null )
      for( int i = 0; i < _numNodes-1; i++ )
        new NodeContainer(new String[0]).start();

    H2O.waitForCloudSize(_numNodes, 10000);

    System.out.println("Created");
  }
}
