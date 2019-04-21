package water;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
public class ClientJarMD5IgnoreTest extends TestUtil {
  @BeforeClass() public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void testDontCheckMD5ForClient() {
    Collection<H2OListenerExtension> listenerExtensions = ExtensionManager.getInstance().getListenerExtensions();
    NodeLocalEventCollectingListener ext = (NodeLocalEventCollectingListener)listenerExtensions.iterator().next();
    ext.clear();

    HeartBeat hb = new HeartBeat();
    hb._cloud_name_hash = H2O.SELF._heartbeat._cloud_name_hash;
    hb._client = true;
    byte[] fake_hash = new byte[1];
    fake_hash[0] = 0;
    hb._jar_md5 = fake_hash;

    // send client heartbeat with different md5
    AutoBuffer ab = new AutoBuffer(H2O.SELF, UDP.udp.heartbeat._prior);
    ab.putUdp(UDP.udp.heartbeat, 65400); // put different port number to simulate heartbeat from fake node
    hb.write(ab);
    ab.close();

    // Give it time so the packet can arrive
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    ArrayList<Object[]> client_wrong_md5 = ext.getData("client_wrong_md5");
    assertEquals(1, client_wrong_md5.size());

    byte[] received_hash= (byte[])client_wrong_md5.get(0)[0];
    assertArrayEquals(fake_hash, received_hash);
  }

  @After
  public void waitForClientsDisconnect(){
    try {
      Thread.sleep(H2O.ARGS.clientDisconnectTimeout *2); // sleep double the time the timeout for client disconnect is reached
      // to be somehow sure the client is gone

    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

}
