package ai.h2o.targetencoding;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.*;
import water.rapids.Merge;

import static org.junit.Assert.*;

public class BroadcastJoinTest extends TestUtil {


  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  private Frame fr = null;

  @Test
  public void joinPerformsWithoutLoosingOriginalOrderTest() {

    Frame rightFr = null;
    Vec emptyNumerator = null;
    Vec emptyDenominator = null;
    try {

      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "fold")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "c", "b"))
              .withDataForCol(1, ar(1, 0, 1))
              .build();

      rightFr = new TestFrameBuilder()
              .withName("testFrame2")
              .withColNames("ColA", "fold", "numerator", "denominator")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "c"))
              .withDataForCol(1, ar(1, 0, 0))
              .withDataForCol(2, ar(22, 33, 42))
              .withDataForCol(3, ar(44, 66, 84))
              .build();

      emptyNumerator = Vec.makeZero(fr.numRows());
      fr.add("numerator", emptyNumerator);
      emptyDenominator = Vec.makeZero(fr.numRows());
      fr.add("denominator", emptyDenominator);
      
      Frame joined = BroadcastJoinForTargetEncoder.join(fr, new int[]{0}, 1, rightFr, new int[]{0}, 1);

      Scope.enter();
      assertStringVecEquals(cvec("a", "c", "b"), joined.vec("ColA"));
      assertEquals(22, joined.vec("numerator").at(0), 1e-5);
      assertEquals(42, joined.vec("numerator").at(1), 1e-5);
      assertEquals(44, joined.vec("denominator").at(0), 1e-5);
      assertEquals(84, joined.vec("denominator").at(1), 1e-5);
      assertTrue(joined.vec("numerator").isNA(2));
      assertTrue(joined.vec("denominator").isNA(2));
      Scope.exit();
      printOutFrameAsTable(fr, false, fr.numRows());
    } finally {
      if(rightFr != null) rightFr.delete();
    }
  }

  @Test
  public void joinWithoutFoldColumnTest() {

    Frame rightFr = null;
    Vec emptyNumerator = null;
    Vec emptyDenominator = null;
    try {

      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, ar("a", "c", "b"))
              .build();

      rightFr = new TestFrameBuilder()
              .withName("testFrame2")
              .withColNames("ColA", "numerator", "denominator")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "c"))
              .withDataForCol(1, ar(22, 33, 42))
              .withDataForCol(2, ar(44, 66, 84))
              .build();

      emptyNumerator = Vec.makeZero(fr.numRows());
      fr.add("numerator", emptyNumerator);
      emptyDenominator = Vec.makeZero(fr.numRows());
      fr.add("denominator", emptyDenominator);

      Frame joined = BroadcastJoinForTargetEncoder.join(fr, new int[]{0}, -1, rightFr, new int[]{0}, -1);

      Scope.enter();
      assertStringVecEquals(cvec("a", "c", "b"), joined.vec("ColA"));
      assertVecEquals(vec(22, 42, 33), joined.vec("numerator"), 1e-5);
      assertVecEquals(vec(44, 84, 66), joined.vec("denominator"), 1e-5);
      Scope.exit();
      printOutFrameAsTable(fr, false, fr.numRows());
    } finally {
      if(rightFr != null) rightFr.delete();
    }
  }

  // Shows that we will loose original order due to grouping otherwise this(swapping left and right frames) would be a possible workaround 
  @Test(expected = AssertionError.class)
  public void mergeWillUseRightFramesOrderAndGroupByValues() {
    Scope.enter();
    try {
      Frame fr = new TestFrameBuilder()
              .withName("leftFrame")
              .withColNames("ColA", "ColB")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "c", "e", "a"))
              .withDataForCol(1, ard(-1, 2, 3, 4, 7))
              .build();

      Frame holdoutEncodingMap = new TestFrameBuilder()
              .withName("holdoutEncodingMap")
              .withColNames("ColB", "ColC")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("c", "a", "e", "b"))
              .withDataForCol(1, ard(2, 3, 4, 5))
              .build();

      //Note: we end up with the order from the `right` frame
      int[][] levelMaps = {CategoricalWrappedVec.computeMap(holdoutEncodingMap.vec(0).domain(), fr.vec(0).domain())};
      Frame res = Merge.merge(holdoutEncodingMap, fr, new int[]{0}, new int[]{0}, false, levelMaps);
      printOutFrameAsTable(res, false, res.numRows());
      
      //We expect this assertion to fail
      assertStringVecEquals(cvec("a", "b", "c", "e", "a"), res.vec("ColB"));
    } finally {
      Scope.exit();
    }
  }
    
  @After
  public void afterEach() {
    if (fr != null) fr.delete();
  }

}
