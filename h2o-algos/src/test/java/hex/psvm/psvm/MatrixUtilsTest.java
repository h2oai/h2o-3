package hex.psvm.psvm;

import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import static org.junit.Assert.assertEquals;

public class MatrixUtilsTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }
  
  @Test
  public void productMtDM() {
    try {
      Scope.enter();
      Frame h = new TestFrameBuilder()
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ard(1.0, 2.0))
              .withDataForCol(1, ard(0.0, 3.0))
              .withDataForCol(2, ard(4.0, 5.0))
              .build();
      Scope.track(h);
      Vec d = h.remove(2);

      LLMatrix m = MatrixUtils.productMtDM(h, d);

      assertEquals(24, m.get(0, 0), 0);
      assertEquals(30, m.get(1, 0), 0);
      assertEquals(45, m.get(1, 1), 0);
    } finally {
      Scope.exit();
    }
  }

}
