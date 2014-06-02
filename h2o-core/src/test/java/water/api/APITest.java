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
      RequestServer.SERVER.serve("/Cloud.html","GET",null,new Properties());
      assertFalse(Paxos._cloudLocked);
    } finally {
      TypeMap._check_no_locking=false;
    }
  }
}
