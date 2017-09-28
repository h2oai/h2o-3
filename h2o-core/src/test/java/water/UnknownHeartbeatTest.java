package water;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UnknownHeartbeatTest extends TestUtil{
  @BeforeClass() public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void testIgnoreUnknownHeartBeat() {
    HeartBeat hb = new HeartBeat();
    hb._cloud_name_hash = 777;
    hb._client = true;
    hb._jar_md5 = H2O.SELF._heartbeat._jar_md5;

    AutoBuffer ab = new AutoBuffer(H2O.SELF, UDP.udp.heartbeat._prior);
    ab.putUdp(UDP.udp.heartbeat, 65400); // put different port number to simulate heartbeat from fake node
    hb.write(ab);
    ab.close();

    // Verify that we don't have a new client
    assertEquals(0, H2O.getClients().size());
  }

  @Test
  public void testIgnoreUnknownShutdownTask(){
    AutoBuffer ab = new AutoBuffer(H2O.SELF, UDP.udp.rebooted._prior);
    ab.putUdp(UDP.udp.rebooted, 65400).put1(UDPRebooted.T.error.ordinal()).putInt(777); // 777 is the hashcode of the origin cloud
    ab.close();

    // Give it time so the packet can arrive
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    // If we got here without exception we're good
  }
}
