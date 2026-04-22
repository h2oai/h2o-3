package hex.tree.isofor;

import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import static org.junit.Assert.*;

/**
 * Regression test for GH-16460: H2O fails to predict on dataset with
 * an ENUM column containing only one unique value (plus NAs).
 */
public class IsolationForestSingleValueEnumTest extends TestUtil {

  @BeforeClass()
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void testPredictWithSingleValueEnumColumn() {
    try {
      Scope.enter();

      // Build a frame with a single-value categorical column and some NAs, mimicking
      // the original issue where column "Букинг" had only "booking" values and blanks.
      Frame train = new TestFrameBuilder()
              .withColNames("num1", "cat_single", "num2")
              .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, new double[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
                      11, 12, 13, 14, 15, 16, 17, 18, 19, 20})
              .withDataForCol(1, new String[]{
                      "alpha", "alpha", "alpha", null, "alpha", "alpha",
                      null, "alpha", "alpha", "alpha",
                      null, null, "alpha", "alpha", null, "alpha",
                      "alpha", null, "alpha", "alpha"})
              .withDataForCol(2, new double[]{0.1, 0.5, 0.3, 0.7, 0.2, 0.9, 0.4, 0.6, 0.8, 0.1,
                      0.3, 0.5, 0.7, 0.2, 0.4, 0.6, 0.8, 0.9, 0.1, 0.5})
              .build();
      Scope.track(train);

      IsolationForestModel.IsolationForestParameters p = new IsolationForestModel.IsolationForestParameters();
      p._train = train._key;
      p._seed = 42;
      p._ntrees = 10;

      IsolationForest isofor = new IsolationForest(p);
      IsolationForestModel model = isofor.trainModel().get();
      assertNotNull(model);
      Scope.track_generic(model);

      // This predict call fails with the bug (GH-16460)
      Frame preds = Scope.track(model.score(train));
      assertNotNull(preds);
      assertEquals(train.numRows(), preds.numRows());

      // Also verify MOJO/POJO scoring consistency
      assertTrue(model.testJavaScoring(train, preds, 1e-8));
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testPredictWithMultipleSingleValueEnumColumns() {
    try {
      Scope.enter();

      // Test with multiple single-value enum columns and a mix of regular columns
      Frame train = new TestFrameBuilder()
              .withColNames("num1", "cat_single_a", "cat_single_b", "cat_multi", "num2")
              .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, new double[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
                      11, 12, 13, 14, 15, 16, 17, 18, 19, 20})
              .withDataForCol(1, new String[]{
                      "only", null, "only", "only", null,
                      "only", "only", null, "only", "only",
                      null, "only", "only", "only", null,
                      "only", null, "only", "only", "only"})
              .withDataForCol(2, new String[]{
                      null, "val", "val", null, "val",
                      "val", null, null, "val", "val",
                      "val", null, "val", "val", null,
                      "val", "val", null, "val", null})
              .withDataForCol(3, new String[]{
                      "A", "B", "C", "A", "B",
                      "C", "A", "B", "C", "A",
                      "B", "C", "A", "B", "C",
                      "A", "B", "C", "A", "B"})
              .withDataForCol(4, new double[]{0.1, 0.5, 0.3, 0.7, 0.2, 0.9, 0.4, 0.6, 0.8, 0.1,
                      0.3, 0.5, 0.7, 0.2, 0.4, 0.6, 0.8, 0.9, 0.1, 0.5})
              .build();
      Scope.track(train);

      IsolationForestModel.IsolationForestParameters p = new IsolationForestModel.IsolationForestParameters();
      p._train = train._key;
      p._seed = 42;
      p._ntrees = 10;

      IsolationForest isofor = new IsolationForest(p);
      IsolationForestModel model = isofor.trainModel().get();
      assertNotNull(model);
      Scope.track_generic(model);

      Frame preds = Scope.track(model.score(train));
      assertNotNull(preds);
      assertEquals(train.numRows(), preds.numRows());

      assertTrue(model.testJavaScoring(train, preds, 1e-8));
    } finally {
      Scope.exit();
    }
  }
}
