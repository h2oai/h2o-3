package water;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.*;

public class PaxosTest extends TestUtil {

  @BeforeClass
  public static void setUp() {
    stall_till_cloudsize(1);
  }

  private H2ONode[] oldClients;

  @Before
  public void saveOldClients() {
    oldClients = H2O.getClients();
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
    assertArrayEquals(new H2ONode[]{clientNode}, getNewClients());

    Paxos.doHeartbeat(clientNode);

    ArrayList<Object[]> events = ext.getData("clients_disabled");
    assertEquals(1, events.size());
    assertArrayEquals(new Object[]{clientNode}, events.get(0));

    // the node was removed from cloud
    assertArrayEquals(new H2ONode[]{}, getNewClients());
  }

  private H2ONode[] getNewClients() {
    List<H2ONode> newClients = new ArrayList<>();
    for (H2ONode client : H2O.getClients()) {
      if (! isOldClient(client))
        newClients.add(client);
    }
    return newClients.toArray(new H2ONode[0]);
  }

  private boolean isOldClient(H2ONode client) {
    for (H2ONode oldClient : oldClients) {
      if (oldClient == client)
        return true;
    }
    return false;
  }
  
}
