package hex;

import org.junit.BeforeClass;
import org.junit.Test;
import water.H2O;
import water.Paxos;
import water.TestUtil;
import water.api.RequestServer;

import java.util.Properties;

import static org.junit.Assert.assertFalse;

public class AAA_PreCloudLock extends TestUtil {
  static boolean testRan = false;

  @BeforeClass() public static void setup() { stall_till_cloudsize(5); }

  private static void stall() {
    stall_till_cloudsize(2);
    // Start Nano server; block for starting
    Runnable run = H2O.finalizeRegistration();
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
    assertFalse("Check of pre-cloud classes failed.  You likely added a class to TypeMap.BOOTSTRAP_CLASSES[].", Paxos._cloudLocked);
  }
}
