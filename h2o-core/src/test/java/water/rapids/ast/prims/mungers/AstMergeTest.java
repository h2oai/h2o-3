package water.rapids.ast.prims.mungers;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;

/***
 * This test is written by Andrey Spiridonov in JIRA PUBDEV-5924.
 */
public class AstMergeTest extends TestUtil {

  @BeforeClass
  static public void setup() {
    stall_till_cloudsize(1);
  }


  @Ignore
  @Test(timeout = 100000) // This merge is not going to finish in reasonable time. Check if it is n*log(n)
  public void AutoMergeAllLeftStressTest() {

    long seed = 42L;

    int numberOfRows = 1000000;
    Frame fr = new TestFrameBuilder()
            .withName("leftFrame")
            .withColNames("ColA", "ColB")
            .withVecTypes(Vec.T_NUM, Vec.T_STR)
            .withRandomIntDataForCol(0, numberOfRows, 0, 5, seed)
            .withRandomBinaryDataForCol(1, numberOfRows, seed)
            .build();

    Frame frRight = new TestFrameBuilder()
            .withName("rightFrame")
            .withColNames("ColA_R", "ColB_R")
            .withVecTypes(Vec.T_NUM, Vec.T_STR)
            .withRandomIntDataForCol(0, numberOfRows, 0, 5, seed)
            .withRandomBinaryDataForCol(1, numberOfRows, seed)
            .build();

    String tree = "(merge leftFrame rightFrame TRUE FALSE [0.0] [0.0] 'auto' )";
    Rapids.exec(tree);
    fr.delete();
  }

  @Test
  public void mergeWithNaOnTheRightMapsToEverythingTest4() {
    Scope.enter();

    try {
      Frame fr = new TestFrameBuilder()
              .withName("leftFrame")
              .withColNames("ColA",  "ColB")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "c", "e", null))
              .withDataForCol(1, ard(-1,2,3,4,Double.NaN))
              .build();
      Scope.track(fr);
      Frame holdoutEncodingMap = new TestFrameBuilder()
              .withName("holdoutEncodingMap")
              .withColNames( "ColB", "ColC")
              .withVecTypes(Vec.T_NUM, Vec.T_STR)
              .withDataForCol(0, ard(0,-1,2,Double.NaN))
              .withDataForCol(1, ar("str42", "no", "yes", "WTF"))
              .build();
      Frame answer = new TestFrameBuilder()
              .withColNames("ColB",  "ColA", "ColC")
              .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_STR)
              .withDataForCol(0, ard(Double.NaN, -1, 2, 3, 4))
              .withDataForCol(1, ar(null, "a", "b", "c", "e"))
              .withDataForCol(2, ar(null, "no", "yes", null, null))
              .build();
      Scope.track(answer);
      Scope.track(holdoutEncodingMap);
      String tree = "(merge leftFrame holdoutEncodingMap TRUE FALSE [1.0] [0.0] 'auto')";
      Val val = Rapids.exec(tree);
      Frame result = val.getFrame();
      Scope.track(result);
      System.out.println("\n\nLeft frame: ");
      printFrames(fr);
      System.out.println("\n\nRight frame: ");
      printFrames(holdoutEncodingMap);
      System.out.println("\n\nMerged frame with command (merge leftFrame holdoutEncodingMap TRUE FALSE [0.0] [0.0]" +
              " 'auto'): ");
      printFrames(result);
      System.out.println("\n\nMerged frame with command (merge leftFrame holdoutEncodingMap TRUE FALSE [0.0] [0.0]" +
              " 'auto'): ");
      printFrames(result);
      System.out.println("\n\nCorrect merged frame should be");
      printFrames(answer);
      assert isBitIdentical(result, answer):"The two frames are not the same.";
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void mergeWithNaOnTheRightMapsToEverythingTest3() {
    Scope.enter();

    try {
      Frame fr = new TestFrameBuilder()
              .withName("leftFrame")
              .withColNames("ColA",  "ColB")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "c", "e", null))
              .withDataForCol(1, ar(1, 2, 3, 4, 5))
              .build();
      Scope.track(fr);
      Frame holdoutEncodingMap = new TestFrameBuilder()
              .withName("holdoutEncodingMap")
              .withColNames( "ColA", "ColC")
              .withVecTypes(Vec.T_CAT, Vec.T_STR)
              .withDataForCol(0, ar(null, "c", null, "g"))
              .withDataForCol(1, ar("str42", "no", "yes", "WTF"))
              .build();
      Frame answer = new TestFrameBuilder()
              .withColNames("ColA",  "ColB", "ColC")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_STR)
              .withDataForCol(0, ar(null, "a", "b", "c", "e"))
              .withDataForCol(1, ar(5, 1, 2, 3, 4))
              .withDataForCol(2, ar(null, null, null, "no", null))
              .build();
      Scope.track(answer);
      Scope.track(holdoutEncodingMap);
      String tree = "(merge leftFrame holdoutEncodingMap TRUE FALSE [0.0] [0.0] 'auto')";
      Val val = Rapids.exec(tree);
      Frame result = val.getFrame();
      Scope.track(result);
      Frame sortedResult = result.sort(new int[]{0});
      Scope.track(sortedResult);
      System.out.println("\n\nLeft frame: ");
      printFrames(fr);
      System.out.println("\n\nRight frame: ");
      printFrames(holdoutEncodingMap);
      System.out.println("\n\nMerged frame with command (merge leftFrame holdoutEncodingMap TRUE FALSE [0.0] [0.0]" +
              " 'auto'): ");
      printFrames(sortedResult);
      System.out.println("\n\nCorrect merged frame should be");
      printFrames(answer);
      assert isBitIdentical(sortedResult, answer):"The two frames are not the same!";
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void mergeWithNaOnTheRightMapsToEverythingTest2() {
    Scope.enter();

    try {
      Frame fr = new TestFrameBuilder()
              .withName("leftFrame")
              .withColNames("ColA",  "ColB")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "c", "e", null))
              .withDataForCol(1, ar(1, 2, 3, 4, 5))
              .build();
      Scope.track(fr);
      Frame holdoutEncodingMap = new TestFrameBuilder()
              .withName("holdoutEncodingMap")
              .withColNames( "ColA", "ColC")
              .withVecTypes(Vec.T_CAT, Vec.T_STR)
              .withDataForCol(0, ar(null, "c", null))
              .withDataForCol(1, ar("str42", "no", "yes"))
              .build();
      Frame answer = new TestFrameBuilder()
              .withColNames("ColA",  "ColB", "ColC")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_STR)
              .withDataForCol(0, ar(null, "a", "b", "c", "e"))
              .withDataForCol(1, ar(5, 1, 2, 3, 4))
              .withDataForCol(2, ar(null, null, null, "no", null))
              .build();
      Scope.track(answer);
      Scope.track(holdoutEncodingMap);
      String tree = "(merge leftFrame holdoutEncodingMap TRUE FALSE [0.0] [0.0] 'auto')";
      Val val = Rapids.exec(tree);
      Frame result = val.getFrame();
      Scope.track(result);
      Frame sortedResult = result.sort(new int[]{0});
      Scope.track(sortedResult);
      System.out.println("\n\nLeft frame: ");
      printFrames(fr);
      System.out.println("\n\nRight frame: ");
      printFrames(holdoutEncodingMap);
      System.out.println("\n\nMerged frame with command (merge leftFrame holdoutEncodingMap TRUE FALSE [0.0] [0.0]" +
              " 'auto'): ");
      printFrames(sortedResult);
      System.out.println("\n\nCorrect merged frame should be");
      printFrames(answer);
      assert isBitIdentical(sortedResult, answer):"The two frames are not the same.";
    } finally {
      Scope.exit();
    }
  }
  @Test
  public void mergeWithNaOnTheRightMapsToEverythingTest0() {
    Scope.enter();

    try {
      Frame fr = new TestFrameBuilder()
              .withName("leftFrame")
              .withColNames("ColA",  "ColB")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "c", "e"))
              .withDataForCol(1, ar(1, 2, 3, 4))
              .build();
      Scope.track(fr);
      Frame holdoutEncodingMap = new TestFrameBuilder()
              .withName("holdoutEncodingMap")
              .withColNames( "ColA", "ColC")
              .withVecTypes(Vec.T_CAT, Vec.T_STR)
              .withDataForCol(0, ar(null, "a", "b"))
              .withDataForCol(1, ar("str42", "no", "yes"))
              .build();
      Frame answer = new TestFrameBuilder()
              .withColNames("ColA",  "ColB", "ColC")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_STR)
              .withDataForCol(0, ar( "a", "b", "c", "e"))
              .withDataForCol(1, ar(1, 2, 3, 4))
              .withDataForCol(2, ar("no", "yes", null, null))
              .build();
      Scope.track(answer);
      Scope.track(holdoutEncodingMap);
      String tree = "(merge leftFrame holdoutEncodingMap TRUE FALSE [0.0] [0.0] 'auto')";
      Val val = Rapids.exec(tree);
      Frame result = val.getFrame();
      Scope.track(result);
      Frame sortedResult = result.sort(new int[]{0});
      Scope.track(sortedResult);
      System.out.println("\n\nLeft frame: ");
      printFrames(fr);
      System.out.println("\n\nRight frame: ");
      printFrames(holdoutEncodingMap);
      System.out.println("\n\nMerged frame with command (merge leftFrame holdoutEncodingMap TRUE FALSE [0.0] [0.0]" +
              " 'auto'): ");
      printFrames(sortedResult);
      System.out.println("\n\nCorrect merged frame should be");
      printFrames(answer);
      assert isBitIdentical(sortedResult, answer):"The two frames are not the same.";
    } finally {
      Scope.exit();
    }
  }


  @Test
  public void mergeWithNaOnTheRightMapsToEverythingTest() {
    Scope.enter();

    try {
      Frame fr = new TestFrameBuilder()
              .withName("leftFrame")
              .withColNames("ColA",  "ColB")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "c", "e"))
              .withDataForCol(1, ar(1, 2, 3, 4))
              .build();
      Scope.track(fr);
      Frame holdoutEncodingMap = new TestFrameBuilder()
              .withName("holdoutEncodingMap")
              .withColNames( "ColA", "ColC")
              .withVecTypes(Vec.T_CAT, Vec.T_STR)
              .withDataForCol(0, ar(null, "c", null))
              .withDataForCol(1, ar("str42", "no", "yes"))
              .build();
      Frame answer = new TestFrameBuilder()
              .withColNames("ColA",  "ColB", "ColC")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_STR)
              .withDataForCol(0, ar("a", "b", "c", "e"))
              .withDataForCol(1, ar(1, 2, 3, 4))
              .withDataForCol(2, ar(null, null, "no", null))
              .build();
      Scope.track(answer);
      Scope.track(holdoutEncodingMap);
      String tree = "(merge leftFrame holdoutEncodingMap TRUE FALSE [0.0] [0.0] 'auto')";
      Val val = Rapids.exec(tree);
      Frame result = val.getFrame();
      Scope.track(result);
      Frame sortedResult = result.sort(new int[]{0});
      Scope.track(sortedResult);
      System.out.println("\n\nLeft frame: ");
      printFrames(fr);
      System.out.println("\n\nRight frame: ");
      printFrames(holdoutEncodingMap);
      System.out.println("\n\nMerged frame with command (merge leftFrame holdoutEncodingMap TRUE FALSE [0.0] [0.0]" +
              " 'auto'): ");
      printFrames(sortedResult);
      System.out.println("\n\nCorrect merged frame should be");
      printFrames(answer);
      assert isBitIdentical(sortedResult, answer):"The two frames are not the same.";
    } finally {
      Scope.exit();
    }
  }

  public void printFrames(Frame fr) {
    int numRows = (int) fr.numRows();
    int numCols = fr.numCols();
    String[] colTypes = fr.typesStr();
    for (String cname: fr.names()) {
      System.out.print(cname+"\t");
    }
    System.out.println("");
    for (int rindex = 0; rindex < numRows; rindex++) {
      for (int cindex = 0; cindex < numCols; cindex++) {
        if (colTypes[cindex].equals("Numeric"))
          System.out.print(fr.vec(cindex).at(rindex)+"\t\t");
        else
          System.out.print(fr.vec(cindex).stringAt(rindex)+"\t\t");
      }
      System.out.println("");
    }
  }
}
