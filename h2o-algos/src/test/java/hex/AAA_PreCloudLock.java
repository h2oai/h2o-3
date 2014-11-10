package hex;

import static org.junit.Assert.*;
import org.junit.*;

import java.util.Properties;
import water.*;
import water.api.*;

public class AAA_PreCloudLock extends TestUtil {
  static boolean testRan = false;

  @BeforeClass() public static void setup() { stall_till_cloudsize(5); }

  private static void stall() {
    stall_till_cloudsize(2);
    // Start Nano server; block for starting
    Runnable run = H2O.finalizeRequest();
    if( run != null ) 
      synchronized(run) {
        while( water.api.RequestServer.SERVER==null ) 
          try { run.wait(); }
          catch( InterruptedException ignore ) {}
      }
  }

  // ---
  // Should be able to load basic status pages without locking the cloud.
  @Test public void testBasicStatusPages() {
    assertFalse(testRan);
    assertFalse(Paxos._cloudLocked);
    stall();
    assertFalse(Paxos._cloudLocked);

    // Serve some pages and confirm cloud does not lock
    try {
      serve("/",null);
      serve("/Cloud.json",null);
      serve("/junk",null);
      serve("/HTTP404",null);
      Properties parms = new Properties();
      parms.setProperty("src","./smalldata/iris");
      serve("/Typeahead/files",parms);
    } finally {
      testRan = true;
    }
  }

  private void serve(String s, Properties parms) {
    RequestServer.SERVER.serve(s,"GET",null,parms==null?new Properties():parms);
    assertFalse(Paxos._cloudLocked);
  }
}
