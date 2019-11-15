package water;

import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;

import static org.junit.Assert.*;

public class PaxosTest extends TestUtil {

  @Before
  public void setUp() {
    stall_till_cloudsize(1);
  }

  @Test
  public void doClientHeartbeatWhenClientsDisabled() {
    Collection<H2OListenerExtension> listenerExtensions = ExtensionManager.getInstance().getListenerExtensions();
    NodeLocalEventCollectingListener ext = (NodeLocalEventCollectingListener)listenerExtensions.iterator().next();
    ext.clear();

    H2ONode clientNode = H2ONode.intern(InetAddress.getLoopbackAddress(), 33333, H2ONodeTimestamp.UNDEFINED);

    HeartBeat clientHeartBeat = new HeartBeat();
    clientHeartBeat._cloud_name_hash = H2O.CLOUD._hash;
    clientHeartBeat._jar_md5 = H2O.SELF._heartbeat._jar_md5;
    clientHeartBeat._client = true;
    clientNode.setHeartBeat(clientHeartBeat);

    // the node is recognized by H2O as a client at this point
    assertArrayEquals(new H2ONode[]{clientNode}, H2O.getClients());

    Paxos.doHeartbeat(clientNode);

    ArrayList<Object[]> events = ext.getData("clients_disabled");
    assertEquals(1, events.size());
    assertArrayEquals(new Object[]{clientNode}, events.get(0));

    // the node was removed from cloud
    assertArrayEquals(new H2ONode[]{}, H2O.getClients());
  }
}
