package water;

import org.junit.BeforeClass;
import org.junit.Test;
import water.api.RequestServer;

import java.util.Properties;

import static org.junit.Assert.assertFalse;

public class AAA_PreCloudLock extends TestUtil {
  static boolean testRan = false;
  static final int CLOUD_SIZE = 5;
  static final int PARTIAL_CLOUD_SIZE = 2;

  @BeforeClass() public static void setup() { stall_till_cloudsize(CLOUD_SIZE); }

  private static void stall() {
    stall_till_cloudsize(PARTIAL_CLOUD_SIZE);
    // Start Nano server; block for starting
    H2O.finalizeRegistration();
  }

  // ---
  // Should be able to load basic status pages without locking the cloud.
  @Test public void testBasicStatusPages() {
    // Serve some pages and confirm cloud does not lock
    try {
      TypeMap._check_no_locking=true; // Blow a nice assert if locking

      assertFalse(testRan);
      assertFalse(Paxos._cloudLocked);
      stall();
      assertFalse(Paxos._cloudLocked);

      serve("/",null);
      serve("/3/Cloud",null);
      serve("/junk",null);
      serve("/HTTP404", null);
      Properties parms = new Properties();
      parms.setProperty("src","./smalldata/iris");
      serve("/3/Typeahead/files", parms);
      water.util.Log.info("Testing that logging will not lock a cloud");
      serve("/3/ModelBuilders", null); // Note: no modelbuilders registered yet, so this is a vacuous result
      serve("/3/About", null);
      serve("/3/NodePersistentStorage/categories/environment/names/clips/exists", null); // Flow check for prior Flow clips
      assertFalse("Check of pre-cloud classes failed.  You likely made a Key before any outside action triggers cloud-lock.  ", Paxos._cloudLocked);
    } finally {
      TypeMap._check_no_locking=false;
      testRan = true;
    }
  }

  private void serve(String s, Properties parms) {
    RequestServer.serve(s,"GET",null,parms==null?new Properties():parms,null);
    assertFalse("Check of pre-cloud classes failed.  You likely added a class to TypeMap.BOOTSTRAP_CLASSES[].  Page: " + s, Paxos._cloudLocked);
  }
}
