package water.testframework.multinode;

import org.testng.annotations.BeforeSuite;
import water.H2O;

/**
 * Run cookbook examples.
 *
 * Runs all the cookbook examples.
 * Does not require any arguments.
 */
public class MultiNodeSetup {
  /**
   * Number of total H2O nodes to start.
   *
   * Note that all nodes are started within one process by this harness.
   * This approach is good for unit testing.  You would not want to deploy to production like this.
   *
   * Each H2O node is wrapped in it's own classloader.  The H2O nodes communicate with each other
   * using sockets even though they all live in the same process.  This gives very realistic test
   * behavior.
   */
  int _numNodes;

  public MultiNodeSetup(int numNodes) {
    _numNodes = numNodes;
  }

  /**
   * Main program.
   *
   * No args required.  Exit code 0 means all tests passed, nonzero otherwise.
   * Each H2O node needs two ports, n and n+1.
   */
  @BeforeSuite(groups={"multi-node"})
  public void setupCloud() {
    System.out.println("MultiNodeSetup creating " + _numNodes + " secondary nodes...");

    // Leader node, where the tests execute from.
    H2O.main(new String[]{});

    // Secondary nodes.
    for( int i = 0; i < _numNodes-1; i++ ) {
      String[] arr = new String[0];
      NodeContainer n = new NodeContainer(arr);
      n.start();
    }

    H2O.waitForCloudSize(_numNodes, 10000);

    System.out.println("Created");
  }
}
