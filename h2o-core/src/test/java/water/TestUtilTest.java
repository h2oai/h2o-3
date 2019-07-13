package water;

import org.junit.BeforeClass;
import org.junit.Test;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import static org.junit.Assert.assertTrue;

/**
 *  We need to make sure that our tools for testing are reliable as well
 */
public class TestUtilTest extends TestUtil {

  @BeforeClass public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void asFactor() {
    Scope.enter();
    try {
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA")
              .withVecTypes(Vec.T_STR)
              .withDataForCol(0, ar("yes", "no"))
              .build();
      Scope.track(fr);

      assertTrue(fr.vec(0).isString());

      Frame res = asFactor(fr, "ColA");

      assertTrue(res.vec(0).isCategorical());
      Scope.track(res);
    } finally {
      Scope.exit();
    }
  }
}
