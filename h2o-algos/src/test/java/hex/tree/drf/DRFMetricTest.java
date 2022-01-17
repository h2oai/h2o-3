package hex.tree.drf;

import hex.Model;
import hex.ModelBuilder;
import hex.genmodel.utils.DistributionFamily;
import water.MetricTest;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.fvec.Frame;

import java.util.function.Function;

public class DRFMetricTest extends MetricTest {
    
    private Function<Model.Parameters, ModelBuilder> drfConstructor =  parameters -> {
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
            String response = "Angaus";
            Frame dataset = Scope.track(parseTestFile("smalldata/gbm_test/ecology_model.csv", new int[]{0}));
            dataset.toCategoricalCol(response);

            DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
            parms._ntrees = 10;
            parms._response_column = response;
            parms._distribution = DistributionFamily.multinomial;

            final double tolerance = 0.000001;
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
            Frame dataset = Scope.track(parseTestFile("smalldata/prostate/prostate.csv"));

            DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
            parms._ntrees = 10;
            parms._response_column = response;
            parms._distribution = DistributionFamily.gaussian;
            parms._weights_column = "ID";

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
            Frame dataset = Scope.track(parseTestFile("smalldata/prostate/prostate.csv"));
            dataset.toCategoricalCol(response);

            DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
            parms._ntrees = 10;
            parms._response_column = response;
            parms._distribution = DistributionFamily.bernoulli;
            parms._weights_column = "ID";

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
            String response = "Angaus";
            Frame dataset = Scope.track(parseTestFile("smalldata/gbm_test/ecology_model.csv"));
            dataset.toCategoricalCol(response);

            DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
            parms._ntrees = 10;
            parms._response_column = response;
            parms._distribution = DistributionFamily.multinomial;
            parms._weights_column = "Site";

            final double tolerance = 0.000001;
            final boolean ignoreTrainingMetrics = true;
            testIndependentlyCalculatedSupervisedMetrics(dataset, parms, drfConstructor, tolerance, ignoreTrainingMetrics);
        } finally {
            Scope.exit();
        }
    }
}
