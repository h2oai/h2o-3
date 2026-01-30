package hex.isotonic;

import hex.genmodel.algos.isotonic.IsotonicRegressionMojoModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestFrameCatalog;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.H2ORunner;
import water.util.RandomUtils;

import java.util.Random;

import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
public class IsotonicRegressionTest extends TestUtil {

    @Test
    public void testRandom() {
        final int N = 10_000;
        Random r = RandomUtils.getRNG(42);
        try {
            double[] xs = new double[N];
            double[] ys = new double[N];
            for (int i = 0; i < N; i++) {
                xs[i] = r.nextDouble();
                ys[i] = r.nextDouble();
            }
            Scope.enter();
            Frame train = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_NUM)
                    .withColNames("x", "y")
                    .withDataForCol(0, xs)
                    .withDataForCol(1, ys)
                    .build();
            IsotonicRegressionModel.IsotonicRegressionParameters parms = new IsotonicRegressionModel.IsotonicRegressionParameters();
            parms._train = train._key;
            parms._response_column = "y";
            IsotonicRegressionModel ir = new IsotonicRegression(parms).trainModel().get();
            assertNotNull(ir);
            Scope.track_generic(ir);
            assertTrue(ir._output._thresholds_x.length > 1);
            double x = Double.NEGATIVE_INFINITY;
            double y = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < ir._output._thresholds_x.length; i++) {
                double x_cur = ir._output._thresholds_x[i];
                double y_cur = ir._output._thresholds_y[i];
                assertTrue(x_cur > x);
                assertTrue(y_cur >= y);
                x = x_cur;
                y = y_cur;
            }
            Frame scored = ir.score(train);
            Scope.track(scored);
            // check MOJO consistency using testJavaScoring...
            assertTrue(ir.testJavaScoring(train, scored, 1e-8));
            // ...and explicitly using mojo API 
            IsotonicRegressionMojoModel mojo = (IsotonicRegressionMojoModel) toMojo(
                    ir, "randomIsotonic", true);
            final double[] row = {Double.NaN};
            final double[] preds = {Double.NaN};
            for (int i = 0; i < N; i++) {
                row[0] = xs[i];
                double calib = mojo.score0(row, preds)[0];
                assertEquals(calib, preds[0], 0);
                assertEquals(scored.vec(0).at(i), calib, 0);
            }
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testFeatureValidations() {
        try {
            Scope.enter();
            Frame fr = TestFrameCatalog.oneChunkFewRows();
            IsotonicRegressionModel.IsotonicRegressionParameters p = new IsotonicRegressionModel.IsotonicRegressionParameters();
            p._response_column = fr.lastVecName();
            p._train = fr._key;
            IsotonicRegression ir = new IsotonicRegression(p);
            try (Scope.Safe s = Scope.safe()) { // ensure init(true) is executed in its own scope
              ir.init(true);
              assertEquals("ERRR on field: _train: Training frame for Isotonic Regression can only have a single feature column, "+
                      "training frame columns: [\"col_0\", \"col_1\", \"col_2\", \"col_3\"]\n", ir.validationErrors());
            }
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testClipping() {
        try {
            double[] xs = {0.1, 0.2, 0.3};
            double[] ys = {0, 0, 1};
            Scope.enter();
            Frame train = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_NUM)
                    .withColNames("x", "y")
                    .withDataForCol(0, xs)
                    .withDataForCol(1, ys)
                    .build();
            // first with default (no clipping)
            {
                IsotonicRegressionModel.IsotonicRegressionParameters parms = new IsotonicRegressionModel.IsotonicRegressionParameters();
                parms._train = train._key;
                parms._response_column = "y";
                IsotonicRegressionModel ir = new IsotonicRegression(parms).trainModel().get();
                assertNotNull(ir);
                Scope.track_generic(ir);
                assertArrayEquals(new double[]{Double.NaN}, ir.score0(new double[]{0.05}, new double[1]), 0);
                assertArrayEquals(new double[]{Double.NaN}, ir.score0(new double[]{0.35}, new double[1]), 0);
                assertArrayEquals(new double[]{0.5}, ir.score0(new double[]{0.25}, new double[1]), 0);
            }
            // now with clipping enabled
            {
                IsotonicRegressionModel.IsotonicRegressionParameters parms = new IsotonicRegressionModel.IsotonicRegressionParameters();
                parms._train = train._key;
                parms._response_column = "y";
                parms._out_of_bounds = IsotonicRegressionModel.OutOfBoundsHandling.Clip;
                IsotonicRegressionModel ir = new IsotonicRegression(parms).trainModel().get();
                assertNotNull(ir);
                Scope.track_generic(ir);
                assertArrayEquals(new double[]{0.0}, ir.score0(new double[]{0.05}, new double[1]), 0);
                assertArrayEquals(new double[]{1.0}, ir.score0(new double[]{0.35}, new double[1]), 0);
                assertArrayEquals(new double[]{0.5}, ir.score0(new double[]{0.25}, new double[1]), 0);
            }
        } finally {
            Scope.exit();
        }
    }

}
