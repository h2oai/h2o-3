package water.api;

import static org.testng.Assert.*;
import org.testng.annotations.*;

import java.util.Properties;
import water.*;
import water.testframework.priority.Priority;

public class APITest extends TestUtil {
  static boolean testRan = false;

  private static void stall() {
    if (testRan) {
      return;
    }

    stall_till_cloudsize(1);
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
  @Test(priority=Priority.API_TEST_PRIORITY)
  public void testBasicStatusPages() {
    if (testRan) {
      return;
    }

    assertFalse(Paxos._cloudLocked);
    stall();
    assertFalse(Paxos._cloudLocked);

    // Serve some pages and confirm cloud does not lock
    try {
      TypeMap._check_no_locking=true; // Blow a nice assert if locking
      serve("/",null);
      serve("/Cloud.html",null);
      serve("/junk",null);
      serve("/HTTP404",null);
      Properties parms = new Properties();
      parms.setProperty("src","./smalldata/iris");
      serve("/Typeahead/files",parms);
    } finally {
      TypeMap._check_no_locking=false;
      testRan = true;
    }
  }

  private void serve(String s, Properties parms) {
    RequestServer.SERVER.serve(s,"GET",null,parms==null?new Properties():parms);
    assertFalse(Paxos._cloudLocked);
  }
}
