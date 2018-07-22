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
import water.util.TwoDimTable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AstEqTest extends TestUtil {

  @BeforeClass
  static public void setup() { stall_till_cloudsize(1); }

  private Frame fr = null;

  @Test
  public void IsNaTest() {
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB")
            .withVecTypes(Vec.T_NUM, Vec.T_STR)
            .withDataForCol(0, ard(1, 2))
            .withDataForCol(1, ar("1", "3"))
            .build();
    String tree = "(== (cols testFrame [0.0] ) 1 )";
    Val val = Rapids.exec(tree);
    Frame results = val.getFrame();

    assertTrue(results.numRows() == 2);
    assertEquals(1, results.vec(0).at(0), 1e-5);
    assertEquals(0, results.vec(0).at(1), 1e-5);

    results.delete();
  }

  @After
  public void afterEach() {
    fr.delete();
  }


}
