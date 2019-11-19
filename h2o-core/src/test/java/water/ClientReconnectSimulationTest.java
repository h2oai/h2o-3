package water;

import org.junit.*;
import water.util.Log;

import java.net.InetAddress;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class ClientReconnectSimulationTest extends TestUtil{

  private InetAddress FAKE_NODE_ADDRESS;

  @BeforeClass() public static void setup() {
    stall_till_cloudsize(1);
  }

  @BeforeClass
  public static void allowClients() {
    H2O.ARGS.allow_clients = true;
  }

  @AfterClass
  public static void disableClients() {
    H2O.ARGS.allow_clients = false;
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
    
    H2ONode node = H2ONode.intern(FAKE_NODE_ADDRESS, 65456, (short)-100);
    node._heartbeat = hb;
    
    assertSame(node, H2ONode.intern(FAKE_NODE_ADDRESS, 65456, (short)-101));

    // Verify number of clients
    assertEquals(1, getClients().length);
  }

  @Test
  public void testClientTimeout() {
    // connect the client, wait for the timeout period and check that afterwards, the client is disconnected
    HeartBeat hb = new HeartBeat();
    hb._cloud_name_hash = H2O.SELF._heartbeat._cloud_name_hash;
    hb._client = true;
    hb._jar_md5 = H2O.SELF._heartbeat._jar_md5;

    H2ONode
            .intern(FAKE_NODE_ADDRESS, 65456, (short)-100)
            ._heartbeat = hb;

    // Verify number of clients
    assertEquals(1, getClients().length);
    waitForClientsDisconnect();
    assertEquals(0, getClients().length);
  }

  @Test
  public void testTwoClientsReconnect() {
    HeartBeat hb = new HeartBeat();
    hb._cloud_name_hash = H2O.SELF._heartbeat._cloud_name_hash;
    hb._client = true;
    hb._jar_md5 = H2O.SELF._heartbeat._jar_md5;

    H2ONode n1 = H2ONode.intern(FAKE_NODE_ADDRESS, 65456, (short)-100);
    n1._heartbeat = hb;
    H2ONode n2 = H2ONode.intern(FAKE_NODE_ADDRESS, 65456, (short)-101);
    assertSame(n1, n2);

    // make sure H2O sees only one client node
    assertEquals(1, getClients().length);

    // second client from another node re-connects
    H2ONode n3 = H2ONode.intern(FAKE_NODE_ADDRESS, 65458, (short)-100);
    n3._heartbeat = hb;
    H2ONode n4 = H2ONode.intern(FAKE_NODE_ADDRESS, 65458, (short)-101);
    assertSame(n3, n4);

    // we should see 2 clients nodes now
    assertEquals(2, getClients().length);
  }

  static H2ONode[] getClients() {
    H2ONode[] clients = H2O.getClients();
    Log.info("Current clients: " + Arrays.toString(clients));
    return clients;
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
