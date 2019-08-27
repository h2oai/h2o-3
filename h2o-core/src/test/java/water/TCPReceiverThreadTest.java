package water;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;

import static org.junit.Assert.*;

public class TCPReceiverThreadTest extends TestUtil {

  @BeforeClass()
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void testDontShutdownOnGarbageRequests() throws Exception {
    NodeLocalEventCollectingListener ext = NodeLocalEventCollectingListener.getFreshInstance(); 

    // make a bogus request to our H2O PORT (NOT the API PORT!!!)
    // using HTTP is just a cheap trick to send some garbage data that H2O won't recognize
    URL apiURL = new URL("http:/" + H2O.SELF_ADDRESS + ":" + H2O.H2O_PORT + "/");
    try (InputStream is = apiURL.openStream()) {
      assertNull(is); // should never happen
    } catch (IOException e) {
      e.printStackTrace();
    }

    ArrayList<Object[]> protocolFailure = ext.getData("protocol-failure");
    assertEquals(1, protocolFailure.size());
    assertArrayEquals(new Object[]{"handshake"}, protocolFailure.get(0));
  }

}
