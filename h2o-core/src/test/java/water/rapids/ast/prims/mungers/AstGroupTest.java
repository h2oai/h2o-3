package water.rapids.ast.prims.mungers;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.*;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;

@RunWith(Parameterized.class)
public class AstGroupTest extends TestUtil {

  @Parameterized.Parameters
  public static Object[] data() {
    return new Object[]{
            "GB",
            "GBSafe" // available in tests only - testing workaround for TE
    };
  }

  @Parameterized.Parameter
  public String groupByOp;
  
  @BeforeClass
  static public void setup() { stall_till_cloudsize(1); }

  @Test
  public void testGroupBy() {
    Scope.enter();
    try {
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB", "ColC")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ard(1, 1, 1, 2, 2, 2))
              .withDataForCol(1, ard(1, 1, 2, 1, 2, 2))
              .withDataForCol(2, ar(4, 5, 6, 7, 2, 6))
              .withChunkLayout(2, 2, 2)
              .build();

      String rapidEx = "("+ groupByOp + " " + fr._key + " [0, 1] sum 2 \"all\" nrow 1 \"all\")";
      Val val = Rapids.exec(rapidEx);
      Frame res = val.getFrame();
      Scope.track(res);

      Vec resVec = res.vec(2);
      Vec expected = Scope.track(vec(9, 6, 7, 8));
      assertVecEquals(expected, resVec, 1e-5);
    } finally {
      Scope.exit();
    }
  }

  public static class AstGroupSafe extends AstGroup {
    public AstGroupSafe() {
      super(false);
    }

    @Override
    public String str() {
      return "GBSafe";
    }
  }

}
