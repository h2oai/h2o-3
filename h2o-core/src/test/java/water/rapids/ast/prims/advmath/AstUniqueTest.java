package water.rapids.ast.prims.advmath;

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

public class AstUniqueTest extends TestUtil {

  @BeforeClass
  static public void setup() { stall_till_cloudsize(1); }

  private Frame fr = null;

  @Before
  public void beforeEach() {
    System.out.println("Before each setup");
  }

  @Test
  public void UniqueCategoricalTest() {

    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB", "ColC")
            .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
            .withDataForCol(0, ard(1, 1))
            .withDataForCol(1, ard(1, 1))
            .withDataForCol(2, ar("3", "6"))
            .build();
    String tree = "(unique (cols testFrame [2]))";
    Val val = Rapids.exec(tree);
    Frame res = val.getFrame();

    assertEquals(2, res.numRows());
    assertEquals("3", res.vec(0).stringAt(0));
    assertEquals("6", res.vec(0).stringAt(1));

    res.delete();
  }

  @Test
  public void UniqueNumericalTest() {

    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB", "ColC")
            .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
            .withDataForCol(0, ard(1, 1))
            .withDataForCol(1, ard(1, 1))
            .withDataForCol(2, ar(3, 6))
            .build();
    String tree = "(unique (cols testFrame [2]))";
    Val val = Rapids.exec(tree);
    Frame res = val.getFrame();

    Vec expected = vec(6, 3);
    assertVecEquals(expected, res.vec(0), 1e-6); // TODO Why order of 6 and 3 is as if it is desc. sorted ?

    expected.remove();
    res.delete();
  }

  @After
  public void afterEach() {
    fr.delete();
  }
}
