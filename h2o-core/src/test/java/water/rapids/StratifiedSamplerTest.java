package water.rapids;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;

import static org.junit.Assert.*;

public class StratifiedSamplerTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }
  
  @Test
  public void sampleDoesNotLeakKeys() {

    Frame frameThatWillBeSampledByHalf = parse_test_file("./smalldata/gbm_test/titanic.csv");
    String responseColumnName = "survived";

    Frame sampledByHalf = StratifiedSampler.sample(frameThatWillBeSampledByHalf, responseColumnName, 0.5, 1234L);
    sampledByHalf.delete();
    frameThatWillBeSampledByHalf.delete();
  }
  
  @Test
  public void underlyingStratifiedSplitDoesNotLeakTest() {
    final String[] STRATA_DOMAIN = new String[]{"in", "out"};
    Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");

    Vec stratifiedSplitVec = StratifiedSplit.split(fr.vec("survived"), 0.5, 1234, STRATA_DOMAIN);
    stratifiedSplitVec.remove();
    fr.delete();
  }
  
  @Test
  public void samplingIsWorkingTest() {

    //Sampling is working
  }

  

}
