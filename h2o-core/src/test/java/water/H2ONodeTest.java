package water;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.net.InetAddress;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class H2ONodeTest extends TestUtil {

  @Before
  public void setUp() {
    stall_till_cloudsize(1);
  }

  @Rule
  public transient ExpectedException ee = ExpectedException.none();
  
  @Test
  public void internClientWhenClientsDisabled() {
    ee.expectMessage("Client connections are not allowed, source 127.0.0.1:22221");
    H2ONode.intern(InetAddress.getLoopbackAddress(), 22222, (short) -1);
  }

  @Test
  public void sendMessageToClientWhenClientsDisabled() {
    // node was first discovered without a timestamp 
    H2ONode clientNode = H2ONode.intern(InetAddress.getLoopbackAddress(), 22224, H2ONodeTimestamp.UNDEFINED);
    // we later learned it is a client
    HeartBeat clientHeartBeat = new HeartBeat();
    clientHeartBeat._cloud_name_hash = H2O.CLOUD._hash;
    clientHeartBeat._jar_md5 = H2O.SELF._heartbeat._jar_md5;
    clientHeartBeat._client = true;
    clientNode.setHeartBeat(clientHeartBeat);

    ee.expectMessage("Attempt to communicate with client 127.0.0.1:22223 blocked. Client connections are not allowed in this cloud.");
    clientNode.sendMessage(null, (byte) -1);
  }

  @Test
  public void testSelf() {
    assertTrue(H2O.SELF.isSelf());
    for (H2ONode node : H2O.CLOUD.members()) {
      Assert.assertEquals(H2O.SELF == node, node.isSelf());
    }
  }
  
}
