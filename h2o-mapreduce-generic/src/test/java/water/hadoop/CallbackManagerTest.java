package water.hadoop;

import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

import org.junit.rules.ExpectedException;
import water.hadoop.h2odriver.CallbackManager;

import java.net.Socket;
import java.util.Collections;

public class CallbackManagerTest {

  @Rule
  public ExpectedException ee = ExpectedException.none();
  
  @Test
  public void testRegisterNode_retry() {
    CallbackManager cm = new h2odriver().new CallbackManager(5);

    cm.registerNode("localhost", 54, 0, new Socket());
    Socket s = new Socket();
    cm.registerNode("localhost", 54, 1, s);
    
    assertEquals(cm._socks, Collections.singletonList(s));
    assertEquals(cm._nodes, Collections.singletonList("localhost:54"));
  }

  @Test
  public void testRegisterNode_outOfOrder() {
    CallbackManager cm = new h2odriver().new CallbackManager(5);

    Socket s = new Socket();
    cm.registerNode("localhost", 54, 1, s);
    cm.registerNode("localhost", 54, 0, new Socket());

    assertEquals(cm._socks, Collections.singletonList(s));
    assertEquals(cm._nodes, Collections.singletonList("localhost:54"));
  }

  @Test
  public void testRegisterNode_duplicate() {
    CallbackManager cm = new h2odriver().new CallbackManager(5) {
      @Override
      protected void fatalError(String message) {
        throw new IllegalStateException(message);
      }
    };

    ee.expectMessage("Duplicate node registered (localhost:54), exiting");
    
    cm.registerNode("localhost", 54, 0, new Socket());
    cm.registerNode("localhost", 54, 0, new Socket());
  }

  @Test
  public void testRegisterNode_inconsistent() {
    CallbackManager cm = new h2odriver().new CallbackManager(5) {
      @Override
      protected void fatalError(String message) {
        throw new IllegalStateException(message);
      }
    };

    ee.expectMessage("Inconsistency found: old node entry for a repeated register node attempt doesn't exist, entry: localhost:54");

    cm.registerNode("localhost", 54, 0, new Socket());
    cm._nodes.clear();
    cm.registerNode("localhost", 54, 1, new Socket());
  }

  @Test
  public void testAnnounceCloudReadyNode_cloudingFinalized() throws Exception {
    h2odriver driver = new h2odriver();
    driver.clusterIsUp = false;
    driver.clusterIp = "unknown";
    driver.clusterPort = -1;

    driver.new CallbackManager(1)
            .announceCloudReadyNode("testClusterIp", 42);

    assertTrue(driver.clusterIsUp);
    assertEquals(driver.clusterIp, "testClusterIp");
    assertEquals(driver.clusterPort, 42);
  }

  @Test
  public void testAnnounceCloudReadyNode_cloudingNotFinalized() throws Exception {
    h2odriver driver = new h2odriver();
    driver.clusterIsUp = true;
    driver.clusterIp = "testClusterIp";
    driver.clusterPort = 42;

    driver.new CallbackManager(2)
            .announceCloudReadyNode("differentIp", 7);

    // cluster info remains the same
    assertTrue(driver.clusterIsUp);
    assertEquals(driver.clusterIp, "testClusterIp");
    assertEquals(driver.clusterPort, 42);
  }

  @Test
  public void testAnnounceCloudReadyNode_cloudAlreadyUp() throws Exception {
    h2odriver driver = new h2odriver();
    driver.clusterIsUp = true;
    driver.clusterIp = "testClusterIp";
    driver.clusterPort = 42;

    driver.new CallbackManager(1)
            .announceCloudReadyNode("differentIp", 7);

    // cluster info remains the same
    assertTrue(driver.clusterIsUp);
    assertEquals(driver.clusterIp, "testClusterIp");
    assertEquals(driver.clusterPort, 42);
  }

}
