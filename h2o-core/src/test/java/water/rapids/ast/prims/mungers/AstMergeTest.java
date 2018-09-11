package water.rapids.ast.prims.mungers;

import org.junit.*;
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
import static org.junit.Assert.assertTrue;

public class AstMergeTest extends TestUtil {

  @BeforeClass
  static public void setup() { stall_till_cloudsize(1); }

  private Frame fr = null;

  @Test
  public void AutoMergeAllLeftTest() {

    fr = new TestFrameBuilder()
            .withName("leftFrame")
            .withColNames("ColA", "ColB")
            .withVecTypes(Vec.T_NUM, Vec.T_STR)
            .withDataForCol(0, ard(1, 2))
            .withDataForCol(1, ar("a", "b"))
            .build();

    Frame frRight = new TestFrameBuilder()
            .withName("rightFrame")
            .withColNames("ColA_R", "ColB_R")
            .withVecTypes(Vec.T_NUM, Vec.T_STR)
            .withDataForCol(0, ard(1, 3))
            .withDataForCol(1, ar("c", "d"))
            .build();

    String tree = "(merge leftFrame rightFrame TRUE FALSE [0.0] [0.0] 'auto' )";
    Val val = Rapids.exec(tree);
    Frame res = val.getFrame();

    assertEquals(2, res.numRows());

    printOutFrameAsTable(res, true, false, res.numRows());

    Vec expected = vec(1, 2);
    assertVecEquals(expected, res.vec(0), 1e-6);
    assertEquals(res.vec(1).stringAt(0L),"a");
    assertEquals(res.vec(1).stringAt(1L),"b");
    assertEquals(res.vec(2).stringAt(0L),"c");
    assertEquals(res.vec(2).stringAt(1L),"null");

    expected.remove();
    frRight.delete();
    res.delete();
  }

  @Ignore
  @Test(timeout = 1000) // This merge is not going to finish in reasonable time. Check if it is n*log(n)
  public void AutoMergeAllLeftStressTest() {

    int numberOfRows = 1000000;
    fr = new TestFrameBuilder()
            .withName("leftFrame")
            .withColNames("ColA", "ColB")
            .withVecTypes(Vec.T_NUM, Vec.T_STR)
            .withRandomIntDataForCol(0, numberOfRows, 0, 100)
            .withRandomBinaryDataForCol(1, numberOfRows)
            .build();

    Frame frRight = new TestFrameBuilder()
            .withName("rightFrame")
            .withColNames("ColA_R", "ColB_R")
            .withVecTypes(Vec.T_NUM, Vec.T_STR)
            .withRandomIntDataForCol(0, numberOfRows, 0, 100)
            .withRandomBinaryDataForCol(1, numberOfRows)
            .build();

    String tree = "(merge leftFrame rightFrame TRUE FALSE [0.0] [0.0] 'auto' )";
    Frame res = Rapids.exec(tree).getFrame();

    res.delete();
    frRight.delete();
  }

  @Test
  public void AutoMergeAllLeftWithDuplicatesOnTheRightTest() {

    fr = new TestFrameBuilder()
            .withName("leftFrame")
            .withColNames("ColA", "ColB")
            .withVecTypes(Vec.T_NUM, Vec.T_STR)
            .withDataForCol(0, ard(1, 2))
            .withDataForCol(1, ar("a", "b"))
            .build();

    Frame frRight = new TestFrameBuilder()
            .withName("rightFrame")
            .withColNames("ColA_R", "ColB_R")
            .withVecTypes(Vec.T_NUM, Vec.T_STR)
            .withDataForCol(0, ard(1, 1))
            .withDataForCol(1, ar("c", "d"))
            .build();

    String tree = "(merge leftFrame rightFrame TRUE FALSE [0.0] [0.0] 'auto' )";
    Val val = Rapids.exec(tree);
    Frame res = val.getFrame();

    assertEquals(3, res.numRows());

    Vec expected = vec(1, 1, 2);
    assertVecEquals(expected, res.vec(0), 1e-6);
    assertEquals("a", res.vec(1).stringAt(0L) );
    assertEquals("a", res.vec(1).stringAt(1L));
    assertEquals("b", res.vec(1).stringAt(2L));
    assertEquals("c", res.vec(2).stringAt(0L));
    assertEquals("d", res.vec(2).stringAt(1L));
    assertEquals("null", res.vec(2).stringAt(2L));

    expected.remove();
    res.delete();
    frRight.delete();
  }

  @Test
  public void AutoMergeAllLeftByStringColumnTest() {

    fr = new TestFrameBuilder()
            .withName("leftFrame")
            .withColNames("ColA", "ColB")
            .withVecTypes(Vec.T_CAT, Vec.T_NUM)
            .withDataForCol(0, ar("y", "z"))
            .withDataForCol(1, ar(1, 2))
            .build();

    Frame frRight = new TestFrameBuilder()
            .withName("rightFrame")
            .withColNames("ColA_R", "ColB_R", "ColC_R")
            .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_NUM )
            .withDataForCol(0, ar("z", "z"))
            .withDataForCol(1, ar(2, 3))
            .withDataForCol(2, ar(42, 42))
            .build();

    String tree = "(merge leftFrame rightFrame TRUE FALSE [0.0, 1.0] [0.0, 1.0] 'auto' )";
    Val val = Rapids.exec(tree);
    Frame res = val.getFrame();

    assertEquals(2, res.numRows());
    assertEquals(Double.NaN, res.vec(2).at(0), 1e-6);
    assertEquals(42.0, res.vec(2).at(1), 1e-6);

    frRight.delete();
    res.delete();
  }

  @Test
  public void mergeByEmptyOrComplexStringsTest() {

    fr = new TestFrameBuilder()
            .withName("leftFrame")
            .withColNames("ColA", "ColB")
            .withVecTypes(Vec.T_CAT, Vec.T_NUM)
            .withDataForCol(0, ar("y", "z", "", "?Havana  Cuba", "Aberdeen / Portland  OR"))
            .withDataForCol(1, ar(1, 2, 3, 4, 5))
            .build();

    Frame frRight = new TestFrameBuilder()
            .withName("rightFrame")
            .withColNames("ColA_R", "ColB_R")
            .withVecTypes(Vec.T_CAT, Vec.T_NUM )
            .withDataForCol(0, ar("", "?Havana  Cuba", "Aberdeen / Portland  OR"))
            .withDataForCol(1, ar(11, 22, 33))
            .build();

    String tree = "(merge leftFrame rightFrame TRUE FALSE [0.0] [0.0] 'auto' )";
    Val val = Rapids.exec(tree);
    Frame result = val.getFrame();

    assertEquals(result.vec("ColB_R").at(2), 11, 1e-5);

    frRight.delete();
    result.delete();
  }

  @Ignore
  @Test
  public void mergeByOnlyNAStringsTest() {

    fr = new TestFrameBuilder()
            .withName("leftFrame")
            .withColNames("ColA", "ColB")
            .withVecTypes(Vec.T_CAT, Vec.T_NUM)
            .withDataForCol(0, ar("y", "z", null, "?Havana  Cuba", "Aberdeen / Portland  OR"))
            .withDataForCol(1, ar(1, 2, 3, 4, 5))
            .build();

    Frame frRight = new TestFrameBuilder()
            .withName("rightFrame")
            .withColNames("ColA_R", "ColB_R")
            .withVecTypes(Vec.T_CAT, Vec.T_NUM )
            .withDataForCol(0, new String[]{null, null, null})
            .withDataForCol(1, ar(11, 22, 33))
            .build();

    String tree = "(merge leftFrame rightFrame TRUE FALSE [0.0] [0.0] 'auto' )";
    Val val = Rapids.exec(tree);
    Frame result = val.getFrame();

    //TODO add asserts
//    assertEquals(result.vec("ColB_R").at(2), 11, 1e-5);

    frRight.delete();
    result.delete();
  }

  @After
  public void afterEach() {
    fr.delete();
  }

}
