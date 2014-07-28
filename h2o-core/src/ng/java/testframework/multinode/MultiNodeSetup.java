package testframework.multinode;

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
  final int _numNodes;
  final boolean _multiJvm;

  public MultiNodeSetup(int numNodes) {
    _numNodes = numNodes;
    _multiJvm = false;
  }

  public MultiNodeSetup(int numNodes, boolean multiJvm) {
    _numNodes = numNodes;
    _multiJvm = multiJvm;
  }

  /**
   * Start 1 leader node.
   * Start n-1 secondary nodes.
   *
   * The primary node blocks for cloud initialization and runs the test
   * program flow inside TestNG.
   */
  @BeforeSuite
  public void setupCloud() {
    System.out.println("MultiNodeSetup creating " + _numNodes + " nodes (multiJvm is " + (_multiJvm ? "true" : "false") + ") ...");

    // Leader node, where the tests execute from.
    H2O.main(new String[]{});

    // Secondary nodes, skip if expected to be pre-built
    if( System.getProperty("ai.h2o.skipNodeCreation") == null ) {
      // Let the leader establish himself before starting the secondary nodes.
      H2O.waitForCloudSize(1, 10000);

      for (int i = 0; i < _numNodes - 1; i++) {
        new NodeContainer(i + 1, new String[0], _multiJvm).start();
      }
    }

    H2O.waitForCloudSize(_numNodes, 10000);

    System.out.println("Created");
  }
}
