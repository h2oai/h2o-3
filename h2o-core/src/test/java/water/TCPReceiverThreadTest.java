package water;

import org.junit.*;
import water.network.SocketChannelFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.util.ArrayList;

import static org.junit.Assert.*;

public class TCPReceiverThreadTest extends TestUtil {

  @BeforeClass()
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Before
  public void sleep() {
    try {
      Thread.sleep(200);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  } 
  
  @Test
  public void testDontShutdownOnGarbageRequests() throws Exception {
    NodeLocalEventCollectingListener ext = NodeLocalEventCollectingListener.getFreshInstance(); 

    // make a bogus request to our H2O PORT (NOT the API PORT!!!)
    // using HTTP is just a cheap trick to send some garbage data that H2O won't recognize
    URL apiURL = new URL("http://" + H2O.SELF_ADDRESS.getHostAddress() + ":" + H2O.H2O_PORT + "/");
    try (InputStream is = apiURL.openStream()) {
      assertNull(is); // should never happen
    } catch (IOException e) {
      e.printStackTrace();
    }

    ArrayList<Object[]> protocolFailure = ext.getData("protocol-failure");
    assertEquals(2, protocolFailure.size(), 1); // usually 1, sometimes 2
    for (Object[] failureInfos : protocolFailure) {
      assertArrayEquals(new Object[]{"handshake"}, failureInfos);
    }
  }

  @Test
  public void testConnectFromClientWhenClientsDisabled() throws Exception {
    NodeLocalEventCollectingListener ext = NodeLocalEventCollectingListener.getFreshInstance();

    H2OSecurityManager security = H2OSecurityManager.instance();
    SocketChannelFactory socketFactory = SocketChannelFactory.instance(security);

    try (ByteChannel channel = H2ONode.openChan((byte) 0, socketFactory, H2O.SELF_ADDRESS, H2O.H2O_PORT, (short) -1)) {
      channel.write(ByteBuffer.wrap("anything".getBytes()));
    }
    ArrayList<Object[]> protocolFailure = ext.getData("connection-failure");
    assertEquals(1, protocolFailure.size());
    assertArrayEquals(new Object[]{"clients-disabled"}, protocolFailure.get(0));
  }


}
