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

  @Before
  public void beforeEach() {

  }

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
    if (val instanceof ValFrame)
      fr = val.getFrame();

    assertEquals(2, fr.numRows());

    assertVecEquals(vec(1, 2), val.getFrame().vec(0), 1e-6);
    assertEquals(fr.vec(1).stringAt(0L), "a");
    assertEquals(fr.vec(1).stringAt(0L),"a");
    assertEquals(fr.vec(1).stringAt(1L),"b");
    assertEquals(fr.vec(2).stringAt(0L),"c");
    assertEquals(fr.vec(2).stringAt(1L),"null");

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
    if (val instanceof ValFrame)
      fr = val.getFrame();


    TwoDimTable twoDimTable = fr.toTwoDimTable();
    System.out.println(twoDimTable.toString());

    assertEquals(3, fr.numRows());

    assertVecEquals(vec(1, 1, 2), val.getFrame().vec(0), 1e-6);
    assertEquals(fr.vec(1).stringAt(0L), "a");
    assertEquals(fr.vec(1).stringAt(1L),"a");
    assertEquals(fr.vec(1).stringAt(2L),"b");
    assertEquals(fr.vec(2).stringAt(0L),"c");
    assertEquals(fr.vec(2).stringAt(1L),"d");
    assertEquals(fr.vec(2).stringAt(2L),"null");

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
    if (val instanceof ValFrame)
      fr = val.getFrame();


    TwoDimTable twoDimTable = fr.toTwoDimTable();
    System.out.println(twoDimTable.toString());

    assertEquals(2, fr.numRows());
    assertEquals(Double.NaN, fr.vec(2).at(0), 1e-6);
    assertEquals(42.0, fr.vec(2).at(1), 1e-6);

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


    TwoDimTable twoDimTable = result.toTwoDimTable();
    System.out.println(twoDimTable.toString());

    assertEquals(result.vec("ColB_R").at(2), 11, 1e-5);
  }

  @Ignore
  @Test
  public void mergeByOnlyEmptyStringsTest() {

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


    TwoDimTable twoDimTable = result.toTwoDimTable();
    System.out.println(twoDimTable.toString());

    //TODO add asserts
//    assertEquals(result.vec("ColB_R").at(2), 11, 1e-5);
  }

  @After
  public void afterEach() {
    H2O.STORE.clear();
  }


}
