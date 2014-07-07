package water;

import org.testng.annotations.*;

// Not sure what the point of this is?
// Running this test causes the cloud to shutdown and all subsequent tests to not run.  Don't do this.
public class Shutdown extends TestUtil {
  @Test(groups={"NOPASS"}) public void testShutdown() {
    water.UDPRebooted.T.shutdown.send(H2O.SELF);
  }
}
