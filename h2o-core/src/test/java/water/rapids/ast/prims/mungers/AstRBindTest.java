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

  @Before
  public void beforeEach() {

  }

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
    Frame unionFrame = null;
    if (val instanceof ValFrame)
      unionFrame = val.getFrame();

    TwoDimTable twoDimTable = unionFrame.toTwoDimTable();

    assertEquals(2, unionFrame.numRows());
    assertEquals(5L, twoDimTable.get(5, 2));
    assertEquals(6L, twoDimTable.get(6, 2));
  }

  @After
  public void afterEach() {
    H2O.STORE.clear();
  }


}
