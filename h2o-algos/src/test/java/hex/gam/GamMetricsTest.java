package hex.gam;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelMetrics;
import hex.SplitFrame;
import hex.genmodel.utils.DistributionFamily;
import hex.glm.GLM;
import hex.glm.GLMModel;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.*;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.RandomUtils;
import water.util.fp.Function;

import java.util.Random;

import static hex.gam.GamTestPiping.massageFrame;
import static hex.glm.GLMModel.GLMParameters.Family.multinomial;
import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class GamMetricsTest extends MetricTest {
    public static final double TOLERANCE = 1e-6;

    // test CV metrics with multinomial
    @Test
    public void testMultinomialValidMetrics() {
        try {
            Scope.enter();
            final String[] ignoredCols = new String[]{"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"};
            final String[][] gamCols = new String[][]{{"C6"}, {"C7"}, {"C8"}};
            final Frame train = massageFrame(
                    Scope.track(parseTestFile("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv")),
                    multinomial);
            DKV.put(train);
            Scope.track(train);
            SplitFrame sf = new SplitFrame(train, new double[] {0.7, 0.3}, null);
            sf.exec().get();
            Key[] splits = sf._destination_frames;
            Frame trainFrame = Scope.track((Frame) splits[0].get());
            Frame testFrame = Scope.track((Frame) splits[1].get());
            GAMModel.GAMParameters params = new GAMModel.GAMParameters();
            params._scale = new double[]{0.1, 0.1, 0.1};
            params._family = multinomial;
            params._response_column = "C11";
            params._max_iterations = 1;
            params._savePenaltyMat = true;
            params._ignored_columns = ignoredCols;
            params._gam_columns = gamCols;
            params._train = trainFrame._key;
            params._valid = testFrame._key;
            params._solver = GLMModel.GLMParameters.Solver.IRLSM;
            final GAMModel gam = new GAM(params).trainModel().get();
            Scope.track_generic(gam);
            assertTrue(Math.abs(gam._output._training_metrics._MSE-gam._output._glm_training_metrics._MSE) < TOLERANCE);
            assertTrue(gam._output._training_metrics._nobs==gam._output._glm_training_metrics._nobs);
            assertTrue(Math.abs(gam._output._validation_metrics._MSE-gam._output._glm_validation_metrics._MSE) < TOLERANCE);
            assertTrue(gam._output._validation_metrics._nobs==gam._output._glm_validation_metrics._nobs);
        } finally {
            Scope.exit();
        }
    }

    /***
     * Test cv metrics for Gaussian family.  
     */
    @Test
    public void testCVMetricsRegression() {
        try {
            Scope.enter();
            Frame train = parseTestFile("smalldata/iris/iris_train.csv");
            DKV.put(train);
            Scope.track(train);

            GAMModel.GAMParameters params = new GAMModel.GAMParameters();
            params._family = GLMModel.GLMParameters.Family.gaussian;
            params._response_column = "sepal_len";
            params._max_iterations = 3;
            params._gam_columns = new String[][]{{"petal_wid"}};
            params._train = train._key;
            params._solver = GLMModel.GLMParameters.Solver.IRLSM;
            params._fold_assignment = Model.Parameters.FoldAssignmentScheme.Random;
            params._nfolds = 3;
            GAMModel gam = new GAM(params).trainModel().get();
            Scope.track_generic(gam);

            assertNotNull(gam._output._cross_validation_metrics);
        } finally {
            Scope.exit();
        }
    }
    
    // test for training metrics
    @Test
    public void testBinomialTrainingMetrics() {
        try {
            Scope.enter();
            Frame train = parseTestFile("smalldata/glm_test/binomial_20_cols_10KRows.csv");
            Random rnd = RandomUtils.getRNG(train.byteSize());
            // change training data frame
            int response_index = train.numCols() - 1;
            train.replace((response_index), train.vec(response_index).toCategoricalVec()).remove();
            String[] enumCnames = new String[]{"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C21"};
            int[] eCol = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, response_index};
            int count = 0;
            for (String cname : enumCnames) {
                train.replace((eCol[count]), train.vec(cname).toCategoricalVec()).remove();
                count++;
            }
            Scope.track(train);
            DKV.put(train);
            double threshold = 0.9;
            GAMModel.GAMParameters params = new GAMModel.GAMParameters();
            params._family = GLMModel.GLMParameters.Family.binomial;
            params._response_column = "C21";
            params._max_iterations = 3;
            params._gam_columns = new String[][]{{"C11"}};
            params._train = train._key;
            params._solver = GLMModel.GLMParameters.Solver.IRLSM;
            GAMModel gam = new GAM(params).trainModel().get();
            Scope.track_generic(gam);
            assertTrue(Math.abs(gam._output._training_metrics._MSE-gam._output._glm_training_metrics._MSE) < TOLERANCE);
            assertTrue(gam._output._training_metrics._nobs==gam._output._glm_training_metrics._nobs);
        } finally {
            Scope.exit();
        }
    }

    private Function<Model.Parameters, ModelBuilder> gamConstructor = parameters -> {
        GAMModel.GAMParameters gamParameters = (GAMModel.GAMParameters)parameters;
        return new GAM(gamParameters);
    };


    @Test
    public void testIndependentModelMetricsCalculation_regression() {
        Scope.enter();
        try {
            String response = "AGE";
            Frame dataset = Scope.track(parseTestFile("smalldata/prostate/prostate.csv", new int[]{0}));

            GAMModel.GAMParameters params = new GAMModel.GAMParameters();
            params._response_column = response;
            params._distribution = DistributionFamily.gaussian;
            params._gam_columns = new String[][]{{"PSA"}};

            double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, params, gamConstructor, tolerance);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testIndependentModelMetricsCalculation_binomial() {
        Scope.enter();
        try {
            String response = "CAPSULE";
            Frame dataset = Scope.track(parseTestFile("smalldata/prostate/prostate.csv", new int[]{0}));
            dataset.toCategoricalCol(response);

            GAMModel.GAMParameters params = new GAMModel.GAMParameters();
            params._response_column = response;
            params._distribution = DistributionFamily.bernoulli;
            params._gam_columns = new String[][]{{"PSA"}};

            double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, params, gamConstructor, tolerance);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testIndependentModelMetricsCalculation_multinomial() {
        Scope.enter();
        try {
            String response = "AGE";
            Frame dataset = Scope.track(parseTestFile("smalldata/prostate/prostate.csv", new int[]{0}));
            dataset.toCategoricalCol(response);

            GAMModel.GAMParameters params = new GAMModel.GAMParameters();
            params._response_column = response;
            params._distribution = DistributionFamily.multinomial;
            params._gam_columns = new String[][]{{"PSA"}};

            double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, params, gamConstructor, tolerance);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testIndependentModelMetricsCalculation_ordinal() {
        Scope.enter();
        try {
            String response = "AGE";
            Frame dataset = Scope.track(parseTestFile("smalldata/prostate/prostate.csv", new int[]{0}));
            dataset.toCategoricalCol(response);

            GAMModel.GAMParameters params = new GAMModel.GAMParameters();
            params._response_column = response;
            params._family = GLMModel.GLMParameters.Family.ordinal;
            params._gam_columns = new String[][]{{"PSA"}};

            double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, params, gamConstructor, tolerance);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testIndependentModelMetricsCalculationWithWeightColumn_regression() {
        Scope.enter();
        try {
            String response = "AGE";
            Frame dataset = Scope.track(parseTestFile("smalldata/prostate/prostate_NA_weights.csv"));

            GAMModel.GAMParameters params = new GAMModel.GAMParameters();
            params._response_column = response;
            params._distribution = DistributionFamily.gaussian;
            params._gam_columns = new String[][]{{"PSA"}};
            params._weights_column = "variWeight";
            params._ignored_columns = new String[] {"constWeight"};

            double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, params, gamConstructor, tolerance);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testIndependentModelMetricsCalculationWithWeightColumn_binomial() {
        Scope.enter();
        try {
            String response = "CAPSULE";
            Frame dataset = Scope.track(parseTestFile("smalldata/prostate/prostate_NA_weights.csv"));
            dataset.toCategoricalCol(response);

            GAMModel.GAMParameters params = new GAMModel.GAMParameters();
            params._response_column = response;
            params._distribution = DistributionFamily.bernoulli;
            params._gam_columns = new String[][]{{"PSA"}};
            params._weights_column = "variWeight";
            params._ignored_columns = new String[] {"constWeight"};

            double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, params, gamConstructor, tolerance);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testIndependentModelMetricsCalculationWithWeightColumn_multinomial() {
        Scope.enter();
        try {
            String response = "AGE";
            Frame dataset = Scope.track(parseTestFile("smalldata/prostate/prostate_NA_weights.csv"));
            dataset.toCategoricalCol(response);

            GAMModel.GAMParameters params = new GAMModel.GAMParameters();
            params._response_column = response;
            params._distribution = DistributionFamily.multinomial;
            params._gam_columns = new String[][]{{"PSA"}};
            params._weights_column = "variWeight";
            params._ignored_columns = new String[] {"constWeight"};

            double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, params, gamConstructor, tolerance);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testIndependentModelMetricsCalculationWithWeightColumn_ordinal() {
        Scope.enter();
        try {
            String response = "AGE";
            Frame dataset = Scope.track(parseTestFile("smalldata/prostate/prostate_NA_weights.csv"));
            dataset.toCategoricalCol(response);

            GAMModel.GAMParameters params = new GAMModel.GAMParameters();
            params._response_column = response;
            params._family = GLMModel.GLMParameters.Family.ordinal;
            params._gam_columns = new String[][]{{"PSA"}};
            params._weights_column = "variWeight";
            params._ignored_columns = new String[] {"constWeight"};

            double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, params, gamConstructor, tolerance);
        } finally {
            Scope.exit();
        }
    }
}
