package water;

import org.junit.*;

import java.net.InetAddress;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class ClientReconnectSimulationTest extends TestUtil{

  private InetAddress FAKE_NODE_ADDRESS;

  @BeforeClass() public static void setup() {
    stall_till_cloudsize(1);
  }

  @Before
  public void setupFakeAddress() throws Exception {
    FAKE_NODE_ADDRESS = InetAddress.getByAddress(new byte[]{(byte) 23, (byte) 185, (byte)0, (byte)4});
  }
  
  @Test
  public void testClientReconnect() {
    HeartBeat hb = new HeartBeat();
    hb._cloud_name_hash = H2O.SELF._heartbeat._cloud_name_hash;
    hb._client = true;
    hb._jar_md5 = H2O.SELF._heartbeat._jar_md5;

    H2ONode.intern(FAKE_NODE_ADDRESS, 65456, (short)-100);
    H2ONode.intern(FAKE_NODE_ADDRESS, 65456, (short)-101);
    // Verify number of clients
    System.out.println(Arrays.toString(H2O.getClients()));
    assertEquals(1, H2O.getClients().length);
  }

  @Test
  public void testClientTimeout() {
    // connect the client, wait for the timeout period and check that afterwards, the client is disconnected
    HeartBeat hb = new HeartBeat();
    hb._cloud_name_hash = H2O.SELF._heartbeat._cloud_name_hash;
    hb._client = true;
    hb._jar_md5 = H2O.SELF._heartbeat._jar_md5;

    H2ONode.intern(FAKE_NODE_ADDRESS, 65456, (short)-100);
    // Verify number of clients
    assertEquals(1, H2O.getClients().length);
    waitForClientsDisconnect();
    System.out.println(Arrays.toString(H2O.getClients()));
    assertEquals(0, H2O.getClients().length);
  }

  @Test
  public void testTwoClientsReconnect() {
    HeartBeat hb = new HeartBeat();
    hb._cloud_name_hash = H2O.SELF._heartbeat._cloud_name_hash;
    hb._client = true;
    hb._jar_md5 = H2O.SELF._heartbeat._jar_md5;

    H2ONode.intern(FAKE_NODE_ADDRESS, 65456, (short)-100);
    H2ONode.intern(FAKE_NODE_ADDRESS, 65456, (short)-101);

    // second client from another node re-connects
    H2ONode.intern(FAKE_NODE_ADDRESS, 65458, (short)-100);
    H2ONode.intern(FAKE_NODE_ADDRESS, 65458, (short)-101);
    // Verify number of clients
    assertEquals(2, H2O.getClients().length);
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
