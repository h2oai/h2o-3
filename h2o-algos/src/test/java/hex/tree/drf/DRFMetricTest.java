package hex.tree.drf;

import hex.Model;
import hex.ModelBuilder;
import hex.genmodel.utils.DistributionFamily;
import org.junit.Ignore;
import water.MetricTest;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.fvec.Frame;
import water.util.fp.Function;


public class DRFMetricTest extends MetricTest {
    
    private Function<Model.Parameters, ModelBuilder> drfConstructor = parameters -> {
        DRFModel.DRFParameters drfParameters = (DRFModel.DRFParameters)parameters;
        return new DRF(drfParameters);
    };

    @BeforeClass
    public static void setup() { stall_till_cloudsize(1); }

    @Test
    public void testIndependentModelMetricsCalculation_regression() {
        Scope.enter();
        try {
            String response = "AGE";
            Frame dataset = Scope.track(parseTestFile("smalldata/prostate/prostate.csv", new int[]{0}));

            DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
            parms._ntrees = 10;
            parms._response_column = response;
            parms._distribution = DistributionFamily.gaussian;

            final double tolerance = 0.000001;
            final boolean ignoreTrainingMetrics = true;
            testIndependentlyCalculatedSupervisedMetrics(dataset, parms, drfConstructor, tolerance, ignoreTrainingMetrics);
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

            DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
            parms._ntrees = 10;
            parms._response_column = response;
            parms._distribution = DistributionFamily.bernoulli;

            final double tolerance = 0.000001;
            final boolean ignoreTrainingMetrics = true;
            testIndependentlyCalculatedSupervisedMetrics(dataset, parms, drfConstructor, tolerance, ignoreTrainingMetrics);
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

            DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
            parms._ntrees = 10;
            parms._response_column = response;
            parms._distribution = DistributionFamily.multinomial;

            final double tolerance = 0.00001;
            final boolean ignoreTrainingMetrics = true;
            testIndependentlyCalculatedSupervisedMetrics(dataset, parms, drfConstructor, tolerance, ignoreTrainingMetrics);
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

            DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
            parms._ntrees = 10;
            parms._response_column = response;
            parms._distribution = DistributionFamily.gaussian;
            parms._weights_column = "variWeight";
            parms._ignored_columns = new String[] {"constWeight"};

            final double tolerance = 0.000001;
            final boolean ignoreTrainingMetrics = true;
            testIndependentlyCalculatedSupervisedMetrics(dataset, parms, drfConstructor, tolerance, ignoreTrainingMetrics);
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

            DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
            parms._ntrees = 10;
            parms._response_column = response;
            parms._distribution = DistributionFamily.bernoulli;
            parms._weights_column = "variWeight";
            parms._ignored_columns = new String[] {"constWeight"};

            final double tolerance = 0.000001;
            final boolean ignoreTrainingMetrics = true;
            testIndependentlyCalculatedSupervisedMetrics(dataset, parms, drfConstructor, tolerance, ignoreTrainingMetrics);
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

            DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
            parms._ntrees = 10;
            parms._response_column = response;
            parms._distribution = DistributionFamily.multinomial;
            parms._weights_column = "variWeight";
            parms._ignored_columns = new String[] {"constWeight"};

            final double tolerance = 0.000001;
            final boolean ignoreTrainingMetrics = true;
            testIndependentlyCalculatedSupervisedMetrics(dataset, parms, drfConstructor, tolerance, ignoreTrainingMetrics);
        } finally {
            Scope.exit();
        }
    }
}
