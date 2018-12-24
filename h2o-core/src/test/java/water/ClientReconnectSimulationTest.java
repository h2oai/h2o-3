package water;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.assertEquals;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class ClientReconnectSimulationTest{

  @Test
  public void testClientReconnect() {
    HeartBeat hb = new HeartBeat();
    hb._cloud_name_hash = H2O.SELF._heartbeat._cloud_name_hash;
    hb._client = true;
    hb._jar_md5 = H2O.SELF._heartbeat._jar_md5;

    H2ONode.intern(H2O.SELF_ADDRESS, 65456, (short)-100);
    H2ONode.intern(H2O.SELF_ADDRESS, 65456, (short)-101);
    // Verify number of clients
    assertEquals(1, H2O.getClients().length);

  }

  @Test
  public void testClientTimeout() {
    // connect the client, wait for the timeout period and check that afterwards, the client is disconnected
    HeartBeat hb = new HeartBeat();
    hb._cloud_name_hash = H2O.SELF._heartbeat._cloud_name_hash;
    hb._client = true;
    hb._jar_md5 = H2O.SELF._heartbeat._jar_md5;

    H2ONode.intern(H2O.SELF_ADDRESS, 65456, (short)-100);
    // Verify number of clients
    assertEquals(1, H2O.getClients().length);
    waitForClientsDisconnect();
    assertEquals(0, H2O.getClients().length);
  }

  @Test
  public void testTwoClientsReconnect() {
    HeartBeat hb = new HeartBeat();
    hb._cloud_name_hash = H2O.SELF._heartbeat._cloud_name_hash;
    hb._client = true;
    hb._jar_md5 = H2O.SELF._heartbeat._jar_md5;

    H2ONode.intern(H2O.SELF_ADDRESS, 65456, (short)-100);
    H2ONode.intern(H2O.SELF_ADDRESS, 65456, (short)-101);

    // second client from another node re-connects
    H2ONode.intern(H2O.SELF_ADDRESS, 65458, (short)-100);
    H2ONode.intern(H2O.SELF_ADDRESS, 65458, (short)-101);
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
