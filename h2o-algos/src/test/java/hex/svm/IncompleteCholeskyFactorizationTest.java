package hex.svm;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;

import static org.junit.Assert.*;

public class IncompleteCholeskyFactorizationTest extends TestUtil  {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void testSmallICF() {
    Frame icf = null;
    Frame expected = null;
    Frame fr = parse_test_file("./smalldata/splice/splice.svm");
    try {
      Kernel kernel = new GaussianKernel(0.01);
      icf = IncompleteCholeskyFactorization.icf(fr, "C1", kernel, 3, 1e-6);

      expected = parse_test_file("./smalldata/splice/splice_icf3.csv");
      assertTrue(compareFrames(expected, icf, 1e-6));
    } finally {
      expected.delete();
      if (fr != null)
        fr.delete();
      if (icf != null)
        icf.delete();
    }
  }

}
