package water;

import org.junit.*;

public class Shutdown extends TestUtil {
  @Test public void testShutdown() {
    water.UDPRebooted.T.shutdown.send(H2O.SELF);
  }
}
