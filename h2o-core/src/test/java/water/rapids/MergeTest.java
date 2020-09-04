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

  @Test public void test_merge_on_categorical_column_orders_rows_according_to_right_domain() {
    Scope.enter();
    try {
      Frame left = new TestFrameBuilder()
              .withName("leftFrame")
              .withColNames("ColA", "ColB")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "c", "e"))
              .withDataForCol(1, ard(-1, 2, 3, 4))
              .build();

      Frame right = new TestFrameBuilder()
              .withName("rightFrame")
              .withColNames("ColA", "ColC")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "c", "e", "c"))
              .withDataForCol(1, ard(2, 3, 4, 5))
              .withDomain(0, ar("c", "a", "e"))
              .build();

      int[][] levelMaps = {CategoricalWrappedVec.computeMap(left.vec(0).domain(), right.vec(0).domain())};
      // merging according to "colA" (left) and "colA" (right)
      Frame res = Merge.merge(left, right, new int[]{0}, new int[]{0}, false, levelMaps);
      Scope.track(res);
      printOutFrameAsTable(res, false, res.numRows());
      assertStringVecEquals(cvec("c", "c", "a", "e"), res.vec("ColA"));  // all entries from right "colA" sorted according to the column domain 
      assertVecEquals(vec(3, 3, -1, 4), res.vec("ColB"), 0);  // all entries from right "colA" mapped to value from left "colB"
      assertVecEquals(vec(3, 5, 2, 4), res.vec("ColC"), 0);  // all entries from right "colA" mapped to value from right "colC"
    } finally {
      Scope.exit();
    }
  } 
}
