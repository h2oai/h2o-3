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

import static org.junit.Assert.assertEquals;

public class AstRBindTest extends TestUtil {

  @BeforeClass
  static public void setup() { stall_till_cloudsize(1); }

  private Frame fr = null;

  @Test
  public void TestRBind() {

    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB", "ColC")
            .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
            .withDataForCol(0, ard(1))
            .withDataForCol(1, ard(1))
            .withDataForCol(2, ar(5))
            .build();
    Frame fr2 = new TestFrameBuilder()
            .withName("testFrame2")
            .withColNames("ColA", "ColB", "ColC")
            .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
            .withDataForCol(0, ard(2))
            .withDataForCol(1, ard(2))
            .withDataForCol(2, ar(6))
            .build();
    String tree = "(rbind testFrame testFrame2)";
    Val val = Rapids.exec(tree);
    Frame unionFrame = val.getFrame();

    Vec resVec = unionFrame.vec(2);

    printOutFrameAsTable(fr, false, 10);

    assertEquals(2, unionFrame.numRows());
    assertEquals(5L, resVec.at(0), 1e-5);
    assertEquals(6L, resVec.at(1), 1e-5);

    resVec.remove();
    fr.delete();
    fr2.delete();
    unionFrame.delete();
  }

}
