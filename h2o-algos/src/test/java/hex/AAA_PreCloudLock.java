package hex;

import hex.schemas.PCAModelV99;
import hex.schemas.PCAV99;
import org.junit.BeforeClass;
import org.junit.Test;
import water.H2O;
import water.Paxos;
import water.TestUtil;
import water.TypeMap;
import water.api.RequestServer;

import java.util.Properties;

import static org.junit.Assert.assertFalse;

/**
 * Test class which is used by h2o-algos/testMultiNode.sh to launch a cloud and assure that it is up.
 * Note that it has the same name as and is almost identical to water.AAA_PreCloudLock.
 * @see water.AAA_PreCloudLock
 */
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
    TypeMap._check_no_locking=true; // Blow a nice assert if locking

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
      // Make some Schemas
      new hex.schemas.CoxPHV3();
      new hex.schemas.DRFModelV3();
      new hex.schemas.DRFV3();
      new hex.schemas.DeepLearningModelV3();
      new hex.schemas.DeepLearningV3();
      new hex.schemas.ExampleModelV3();
      new hex.schemas.ExampleV3();
      new hex.schemas.GBMModelV3();
      new hex.schemas.GBMV3();
      new hex.schemas.GLMModelV3();
      new hex.schemas.GLMV3();
      new hex.schemas.GrepModelV3();
      new hex.schemas.GrepV3();
      new hex.schemas.KMeansModelV3();
      new hex.schemas.KMeansV3();
      new hex.schemas.MakeGLMModelV3();
      new hex.schemas.NaiveBayesModelV3();
      new hex.schemas.NaiveBayesV3();
      new PCAModelV99();
      new PCAV99();
      new hex.schemas.SharedTreeModelV3();
      new hex.schemas.SharedTreeV3();
      new hex.schemas.SynonymV3();
      new hex.schemas.TreeStatsV3();
      new hex.schemas.Word2VecModelV3();
      new hex.schemas.Word2VecV3();
      assertFalse("Check of pre-cloud classes failed.  You likely made a Key before any outside action triggers cloud-lock.  ", Paxos._cloudLocked);
    } finally {
      testRan = true;
      TypeMap._check_no_locking=false;
    }
  }

  private void serve(String s, Properties parms) {
    RequestServer.SERVER.serve(s,"GET",null,parms==null?new Properties():parms);
    assertFalse("Check of pre-cloud classes failed.  You likely added a class to TypeMap.BOOTSTRAP_CLASSES[].  Page: " + s, Paxos._cloudLocked);
  }
}
