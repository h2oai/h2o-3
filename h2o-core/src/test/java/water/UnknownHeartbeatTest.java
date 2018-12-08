package water;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
public class UnknownHeartbeatTest extends TestUtil{
  @BeforeClass() public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void testIgnoreUnknownHeartBeat() {
    final int clientsCountBefore = H2O.getClients().length;
    HeartBeat hb = new HeartBeat();
    hb._cloud_name_hash = 777;
    hb._client = true;
    hb._jar_md5 = H2O.SELF._heartbeat._jar_md5;

    AutoBuffer ab = new AutoBuffer(H2O.SELF, UDP.udp.heartbeat._prior);
    ab.putUdp(UDP.udp.heartbeat, 65400); // put different port number to simulate heartbeat from fake node
    hb.write(ab);
    ab.close();

    // Verify that we don't have a new client
    assertEquals(clientsCountBefore, H2O.getClients().length);
  }

  @Test
  public void testIgnoreUnknownShutdownTask(){
    Collection<H2OListenerExtension> listenerExtensions = ExtensionManager.getInstance().getListenerExtensions();
    NodeLocalEventCollectingListener ext = (NodeLocalEventCollectingListener)listenerExtensions.iterator().next();
    ext.clear();

    new AutoBuffer(H2O.SELF, UDP.udp.rebooted._prior)
            .putUdp(UDP.udp.rebooted, 65400)
            .put1(UDPRebooted.MAGIC_SAFE_CLUSTER_KILL_BYTE)
            .put1(UDPRebooted.T.error.ordinal())
            .putInt(777) // 777 is the hashcode of the origin cloud
            .close();

    // Give it time so the packet can arrive
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    ArrayList<Object[]> shutdown_success = ext.getData("shutdown_fail");
    assertEquals(2, shutdown_success.size());
    assertEquals(1, shutdown_success.get(0).length);
    assertEquals(777, (int) shutdown_success.get(0)[0]);
  }

  @Test
  public void testIgnoreUnknownShutdownTaskOldVersion() {
    Collection<H2OListenerExtension> listenerExtensions = ExtensionManager.getInstance().getListenerExtensions();
    NodeLocalEventCollectingListener ext = (NodeLocalEventCollectingListener)listenerExtensions.iterator().next();
    ext.clear();

    // Test that when request comes from the old H2O version ( older than 3.14.0.4 where the fix is implemented )
    AutoBuffer ab = new AutoBuffer(H2O.SELF, UDP.udp.rebooted._prior);
    ab.putUdp(UDP.udp.rebooted, 65400).put1(UDPRebooted.T.error.ordinal()).putInt(777); // 777 is the hashcode of the origin cloud
    ab.close();

    // Give it time so the packet can arrive
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    ArrayList<Object[]> shutdown_success = ext.getData("shutdown_ignored");
    assertEquals(2, shutdown_success.size());
  }

}
