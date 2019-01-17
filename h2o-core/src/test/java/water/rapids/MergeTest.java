package water.rapids;

import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.CategoricalWrappedVec;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

public class MergeTest extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  @Test public void testMergeIsChangingOrderOfRecordsBasedOnRightFrame() {
    Scope.enter();
    try {
      Frame fr = new TestFrameBuilder()
              .withName("leftFrame")
              .withColNames("ColA", "ColB")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "c", "e"))
              .withDataForCol(1, ard(-1, 2, 3, 4))
              .build();

      Frame holdoutEncodingMap = new TestFrameBuilder()
              .withName("holdoutEncodingMap")
              .withColNames("ColB", "ColC")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("c", "a", "e"))
              .withDataForCol(1, ard(2, 3, 4))
              .build();

      //Note: we end up with the order from the `right` frame
      int[][] levelMaps = {CategoricalWrappedVec.computeMap(fr.vec(0).domain(), holdoutEncodingMap.vec(0).domain())};
      Frame res = Merge.merge(fr, holdoutEncodingMap, new int[]{0}, new int[]{0}, false, levelMaps);
      printOutFrameAsTable(res, false, res.numRows());
      assertStringVecEquals(cvec("c", "a", "e"), res.vec("ColA"));
    } finally {
      Scope.exit();
    }
    } 
}
