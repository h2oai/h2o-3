package water.rapids.ast.prims.mungers;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;

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
    Frame res = val.getFrame();

    // Check that row with NA in target column will be mapped into 0, otherwise 1
    assertEquals(0, res.vec(0).at(0), 1e-5);
    assertEquals(1, res.vec(0).at(1), 1e-5);

    res.delete();
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
    Frame res = val.getFrame();

    assertEquals(1, res.numRows());
    assertEquals("6", res.vec(2).stringAt(0));

    res.delete();
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
    Frame res = val.getFrame();

    printOutFrameAsTable(res, false, 100);
    assertEquals(1, res.numRows());
    assertEquals("6", res.vec(2).stringAt(0));

    res.delete();
  }

  @Test
  public void filterByTest() {
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA")
            .withVecTypes(Vec.T_STR)
            .withDataForCol(0, ar("SAN", "SFO"))
            .build();
    Frame res = filterBy(fr, 0, "SAN");
    assertEquals("SAN", res.vec(0).stringAt(0));
    res.delete();
  }

  private Frame filterBy(Frame data, int columnIndex, String value)  {
    String tree = String.format("(rows %s  (==(cols %s [%s] ) '%s' ) )", data._key, data._key, columnIndex, value);
    Val val = Rapids.exec(tree);
    Frame res = val.getFrame();
    res._key = data._key;
    DKV.put(res);
    return res;
  }

  @Test
  public void filterOutByTest() {
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA")
            .withVecTypes(Vec.T_STR)
            .withDataForCol(0, ar("SAN", "SFO"))
            .build();
    Frame res = filterOutBy(fr, 0, "SAN");
    assertEquals("SFO", res.vec(0).stringAt(0));
    res.delete();
  }

  private Frame filterOutBy(Frame data, int columnIndex, String value)  {
    String tree = String.format("(rows %s  (!= (cols %s [%s] ) '%s' )  )", data._key, data._key, columnIndex, value);
    Val val = Rapids.exec(tree);
    Frame res = val.getFrame();
    res._key = data._key;
    DKV.put(res._key , res);
    return res;
  }

  @After
  public void afterEach() {
    fr.delete();
  }


}
