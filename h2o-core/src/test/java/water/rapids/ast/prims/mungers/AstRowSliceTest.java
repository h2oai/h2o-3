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

public class AstRowSliceTest extends TestUtil {

  @BeforeClass
  static public void setup() { stall_till_cloudsize(1); }

  private Frame fr = null;

  @Before
  public void beforeEach() {
    System.out.println("Before each setup");
  }

  @Test
  public void GettingLogicMaskTest() {

    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB", "ColC")
            .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_STR)
            .withDataForCol(0, ard(1, 1))
            .withDataForCol(1, ard(1, 1))
            .withDataForCol(2, ar(null, "6"))
            .build();
    String tree = "(!! (is.na (cols testFrame [2.0] ) ) )";
    Val val = Rapids.exec(tree);
    if (val instanceof ValFrame)
      fr = val.getFrame();

    TwoDimTable twoDimTable = fr.toTwoDimTable();

    // Check that row with NA in target column will be mapped into 0, otherwise 1
    assertEquals(0L, twoDimTable.get(5, 0));
    assertEquals(1L, twoDimTable.get(6, 0));


  }

  @Test
  public void FilteringOutByBooleanMaskTest() {

    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB", "ColC")
            .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_STR)
            .withDataForCol(0, ard(1, 1))
            .withDataForCol(1, ard(1, 1))
            .withDataForCol(2, ar(null, "6"))
            .build();
    String tree = "(rows testFrame (!! (is.na (cols testFrame [2.0] ) ) ) )";
    Val val = Rapids.exec(tree);
    System.out.println(val.toString());
    if (val instanceof ValFrame)
      fr = val.getFrame();

    TwoDimTable twoDimTable = fr.toTwoDimTable();

    System.out.println(twoDimTable.toString());

    assertEquals(1, fr.numRows());
    assertEquals("6", twoDimTable.get(5, 2));
  }

  @Test
  public void RowSliceWithRangeTest() {

    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB", "ColC")
            .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_STR)
            .withDataForCol(0, ard(1, 1))
            .withDataForCol(1, ard(1, 1))
            .withDataForCol(2, ar(null, "6"))
            .build();
    String tree = "(rows testFrame [1:2] )";
    Val val = Rapids.exec(tree);
    System.out.println(val.toString());
    if (val instanceof ValFrame)
      fr = val.getFrame();

    TwoDimTable twoDimTable = fr.toTwoDimTable();

    System.out.println(twoDimTable.toString());

    assertEquals(1, fr.numRows());
    assertEquals("6", twoDimTable.get(5, 2));

  }

  @After
  public void afterEach() {
    System.out.println("After each setup");
    H2O.STORE.clear();
  }


}
