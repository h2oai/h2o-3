package water.rapids.ast.prims.mungers;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import water.H2O;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.util.TwoDimTable;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AstFactorTest extends TestUtil {

  @BeforeClass
  static public void setup() { stall_till_cloudsize(1); }

  private Frame fr = null;

  @Test
  public void asFactorForDataSetTest() {

    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA")
            .withVecTypes(Vec.T_NUM)
            .withDataForCol(0, ard(0, 1))
            .build();

    assertFalse(fr.vec(0).isCategorical());

    String tree = "(as.factor testFrame)";
    Val val = Rapids.exec(tree);
    Frame res = val.getFrame();

    assertTrue(res.vec(0).isCategorical());

    res.delete();
  }

  @Test
  public void asFactorForVecTest() {

    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB")
            .withVecTypes(Vec.T_STR, Vec.T_NUM)
            .withDataForCol(0, ar("yes", "no"))
            .withDataForCol(1, ard(1, 2))
            .build();

    assertTrue(fr.vec(0).isString());
    assertFalse(fr.vec(0).isCategorical());
    assertTrue(fr.vec(1).isNumeric());

    String tree = "(:= testFrame (as.factor (cols testFrame [0])) [0] [])";
    Val val = Rapids.exec(tree);
    Frame res = val.getFrame();

    assertTrue(res.vec(0).isCategorical());
    assertTrue(res.vec(1).isNumeric());

    res.delete();
  }

  @After
  public void afterEach() {
    fr.delete();
  }
}
