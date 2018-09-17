package water.rapids.ast.prims.mungers;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;

public class AstMergeTest extends TestUtil {

  @BeforeClass
  static public void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void mergingWithNaOnTheRightMapsToEverythingTest() {

    Frame fr = new TestFrameBuilder()
            .withName("leftFrame")
            .withColNames("ColA", "ColB")
            .withVecTypes(Vec.T_CAT, Vec.T_NUM)
            .withDataForCol(0, ar("a", "b", "c", "e"))
            .withDataForCol(1, ar(1, 2, 3, 4))
            .build();

    Frame holdoutEncodingMap = new TestFrameBuilder()
            .withName("holdoutEncodingMap")
            .withColNames("ColA", "ColC")
            .withVecTypes(Vec.T_CAT, Vec.T_STR)
            .withDataForCol(0, ar(null, "a", "b"))
            .withDataForCol(1, ar("str42", "no", "yes"))
            .build();


    String tree = "(merge leftFrame holdoutEncodingMap TRUE FALSE [0.0] [0.0] 'auto')";
    Val val = Rapids.exec(tree);
    Frame result = val.getFrame();

    printOutFrameAsTable(result);

    result.delete();
    fr.delete();
    holdoutEncodingMap.delete();
  }

  private void printOutFrameAsTable(Frame fr) {
    System.out.println(fr.toTwoDimTable(0, (int) fr.numRows()).toString(5, true));
  }

}
