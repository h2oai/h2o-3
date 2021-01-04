package hex.psvm.psvm;

import org.junit.BeforeClass;
import org.junit.Test;
import water.H2O;
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
    Frame fr = parseTestFile("./smalldata/splice/splice.svm");
    try {
      icf = H2O.runOnLeaderNode(new ICFTask(fr))._icf;
      expected = parseTestFile("./smalldata/splice/splice_icf3.csv");
      assertTrue(compareFrames(expected, icf, 1e-6));
    } finally {
      if (expected != null)
        expected.delete();
      if (fr != null)
        fr.delete();
      if (icf != null)
        icf.delete();
    }
  }

  private static class ICFTask extends H2O.RemoteRunnable<ICFTask> {
    // IN
    private final Frame _fr;
    // OUT
    private Frame _icf;

    ICFTask(Frame fr) {
      _fr = fr;
    }

    @Override
    public void run() {
      Kernel kernel = new GaussianKernel(0.01);
      _icf = IncompleteCholeskyFactorization.icf(_fr, "C1", kernel, 3, 1e-6);
    }
  }
  
}
