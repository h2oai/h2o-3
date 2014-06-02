package water.api;

import static org.junit.Assert.*;
import java.util.Properties;
import org.junit.*;
import water.*;

public class APITest extends TestUtil {

  @BeforeClass public static void stall() { 
    stall_till_cloudsize(1); 
    // Start Nano server
    H2O.finalizeRequest();
  }

  // ---
  // Should be able to load basic status pages without locking the cloud.
  @Test public void testBasicStatusPages() {
    // Serve some pages and confirm cloud does not lock
    try {
      TypeMap._check_no_locking=true; // Blow a nice assert if locking
      serve("/");
      serve("/Cloud.html");
      serve("/junk");
      serve("/HTTP404");
    } finally {
      TypeMap._check_no_locking=false;
    }
  }

  private void serve(String s) {
    RequestServer.SERVER.serve(s,"GET",null,new Properties());
    assertFalse(Paxos._cloudLocked);
  }
}
