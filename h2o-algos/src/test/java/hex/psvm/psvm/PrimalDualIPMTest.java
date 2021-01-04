package hex.psvm.psvm;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;

public class PrimalDualIPMTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void testSolve_splice() {
    Frame icf = parseTestFile("./smalldata/splice/splice_icf100.csv.gz");
    Frame fr = null;
    Frame expected = null;
    Vec response = null;
    Vec result = null;
    try {
      fr = parseTestFile("./smalldata/splice/splice.svm");
      expected = parseTestFile("./smalldata/splice/splice_icf100_x.csv");

      response = icf.anyVec().align(fr.vec("C1")); // make sure the response has the same layout as the ICF Frame
      
      // run PD-IPM with default params
      result = PrimalDualIPM.solve(icf, response, new PrimalDualIPM.Parms(1, 1), null);
      
      assertVecEquals(expected.vec(0), result, 1e-6);
    } finally {
      icf.remove();
      if (fr != null)
        fr.remove();
      if (response != null)
        response.remove();
      if (expected != null)
        expected.remove();
      if (result != null)
        result.remove();
    }
  }

}
