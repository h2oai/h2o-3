package hex.deeplearning;

import hex.Model;
import hex.ModelBuilder;
import hex.genmodel.utils.DistributionFamily;
import water.MetricTest;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.fvec.Frame;

import java.util.function.Function;

public class DeepLearningMetricTest extends MetricTest {
    
    private Function<Model.Parameters, ModelBuilder> dlConstructor =  parameters -> {
        DeepLearningModel.DeepLearningParameters dlParameters = (DeepLearningModel.DeepLearningParameters)parameters;
        return new DeepLearning(dlParameters);
    };

    @BeforeClass
    public static void setup() { stall_till_cloudsize(1); }

    @Test
    public void testIndependentModelMetricsCalculation_regression() {
        Scope.enter();
        try {
            String response = "AGE";
            Frame dataset = Scope.track(parseTestFile("smalldata/prostate/prostate.csv", new int[]{0}));

            DeepLearningModel.DeepLearningParameters params = new DeepLearningModel.DeepLearningParameters();
            params._response_column = response;
            params._distribution = DistributionFamily.gaussian;
            
            final double tolerance = 0.000001;
            final boolean skipTrainingDataset = true;
            testIndependentlyCalculatedSupervisedMetrics(dataset, params, dlConstructor, tolerance, skipTrainingDataset);
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

            DeepLearningModel.DeepLearningParameters params = new DeepLearningModel.DeepLearningParameters();
            params._response_column = response;
            params._distribution = DistributionFamily.bernoulli;
            
            final double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, params, dlConstructor, tolerance);
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

            DeepLearningModel.DeepLearningParameters params = new DeepLearningModel.DeepLearningParameters();
            params._response_column = response;
            params._distribution = DistributionFamily.multinomial;
            
            final double tolerance = 0.000001;
            final boolean skipTrainingDataset = true; // TODO: investigate why AUC is different on training dataset.
            testIndependentlyCalculatedSupervisedMetrics(dataset, params, dlConstructor, tolerance, skipTrainingDataset);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testIndependentModelMetricsCalculationWithOffsetColumn_regression() {
        Scope.enter();
        try {
            String response = "AGE";
            Frame dataset = Scope.track(parseTestFile("smalldata/prostate/prostate.csv"));

            DeepLearningModel.DeepLearningParameters params = new DeepLearningModel.DeepLearningParameters();
            params._response_column = response;
            params._distribution = DistributionFamily.gaussian;
            params._offset_column = "ID";

            final double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, params, dlConstructor, tolerance);
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

            DeepLearningModel.DeepLearningParameters params = new DeepLearningModel.DeepLearningParameters();
            params._response_column = response;
            params._distribution = DistributionFamily.gaussian;
            params._weights_column = "ID";

            final double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, params, dlConstructor, tolerance);
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

            DeepLearningModel.DeepLearningParameters params = new DeepLearningModel.DeepLearningParameters();
            params._response_column = response;
            params._distribution = DistributionFamily.bernoulli;
            params._weights_column = "ID";

            final double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, params, dlConstructor, tolerance);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testIndependentModelMetricsCalculationWithWeightColumn_multinomial() {
        Scope.enter();
        try {
            String response = "AGE";
            Frame dataset = Scope.track(parseTestFile("smalldata/prostate/prostate.csv"));
            dataset.toCategoricalCol(response);

            DeepLearningModel.DeepLearningParameters params = new DeepLearningModel.DeepLearningParameters();
            params._response_column = response;
            params._distribution = DistributionFamily.multinomial;
            params._weights_column = "ID";

            final double tolerance = 0.000001;
            final boolean skipTrainingDataset = true; // TODO: investigate why AUC is different on training dataset.
            testIndependentlyCalculatedSupervisedMetrics(dataset, params, dlConstructor, tolerance, skipTrainingDataset);
        } finally {
            Scope.exit();
        }
    }
}
