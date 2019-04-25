package hex.svm;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;

import static org.junit.Assert.*;

public class PrimalDualIPMTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void testSolve_splice() {
    Frame icf = parse_test_file("./smalldata/splice/splice_icf100.csv");
    Frame fr = null;
    Frame expected = null;
    Vec response = null;
    Vec result = null;
    try {
      fr = parse_test_file("./smalldata/splice/splice.svm");
      expected = parse_test_file("./smalldata/splice/splice_icf100_x.csv");

      response = icf.anyVec().align(fr.vec("C1")); // make sure the response has the same layout as the ICF Frame
      
      // run PD-IPM with default params
      result = PrimalDualIPM.solve(icf, response, new PrimalDualIPM.Params());
      
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
