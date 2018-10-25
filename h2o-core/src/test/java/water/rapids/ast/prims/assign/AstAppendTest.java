package water.rapids.ast.prims.assign;

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

public class AstAppendTest extends TestUtil {

  @BeforeClass
  static public void setup() { stall_till_cloudsize(1); }

  private Frame fr = null;

  @Test
  public void AppendColumnTest() {

    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB")
            .withVecTypes(Vec.T_NUM, Vec.T_NUM)
            .withDataForCol(0, ard(1, 2))
            .withDataForCol(1, ard(2, 5))
            .build();

    String tree = "( append testFrame ( / (cols testFrame [0]) (cols testFrame [1])) 'appended' )";
    Val val = Rapids.exec(tree);
    Frame res = val.getFrame();

    assertEquals(2, res.numRows());

    assertEquals(0.5, res.vec(2).at(0L), 1e-6);
    assertEquals(0.4, res.vec(2).at(1L), 1e-6);

    res.delete();
  }

  @After
  public void afterEach() {
    fr.delete();
  }
}
