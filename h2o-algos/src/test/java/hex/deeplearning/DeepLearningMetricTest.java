package hex.deeplearning;

import hex.Model;
import hex.ModelBuilder;
import hex.genmodel.utils.DistributionFamily;
import water.MetricTest;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.fp.Function;
import water.util.fp.Function2;

public class DeepLearningMetricTest extends MetricTest {
    
    private Function<Model.Parameters, ModelBuilder> dlConstructor = parameters -> {
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
            params._reproducible = true;
            
            final double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, params, dlConstructor, tolerance);
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
            params._reproducible = true;
            
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
            params._reproducible = true;
            
            final double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, params, dlConstructor, tolerance);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testIndependentModelMetricsCalculationWithOffsetColumn_regression() {
        Scope.enter();
        try {
            String response = "AGE";
            String offsetColumn = "offset";
            Frame dataset = Scope.track(parseTestFile("smalldata/prostate/prostate.csv", new int[]{0}));
            addRandomColumn(dataset, offsetColumn);


            DeepLearningModel.DeepLearningParameters params = new DeepLearningModel.DeepLearningParameters();
            params._response_column = response;
            params._distribution = DistributionFamily.gaussian;
            params._offset_column = offsetColumn;
            params._reproducible = true;

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
            Frame dataset = Scope.track(parseTestFile("smalldata/prostate/prostate_NA_weights.csv"));

            DeepLearningModel.DeepLearningParameters params = new DeepLearningModel.DeepLearningParameters();
            params._response_column = response;
            params._distribution = DistributionFamily.gaussian;
            params._weights_column = "variWeight";
            params._ignored_columns = new String[] {"constWeight"};
            params._reproducible = true;

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
            Frame dataset = Scope.track(parseTestFile("smalldata/prostate/prostate_NA_weights.csv"));
            dataset.toCategoricalCol(response);

            DeepLearningModel.DeepLearningParameters params = new DeepLearningModel.DeepLearningParameters();
            params._response_column = response;
            params._distribution = DistributionFamily.bernoulli;
            params._weights_column = "variWeight";
            params._ignored_columns = new String[] {"constWeight"};
            params._reproducible = true;

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
            Frame dataset = Scope.track(parseTestFile("smalldata/prostate/prostate_NA_weights.csv"));
            dataset.toCategoricalCol(response);

            DeepLearningModel.DeepLearningParameters params = new DeepLearningModel.DeepLearningParameters();
            params._response_column = response;
            params._distribution = DistributionFamily.multinomial;
            params._weights_column = "variWeight";
            params._ignored_columns = new String[] {"constWeight"};
            params._reproducible = true;

            final double tolerance = 0.000001;

            testIndependentlyCalculatedSupervisedMetrics(dataset, params, dlConstructor, tolerance);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testIndependentModelMetricsCalculation_autoencoder() {
        Scope.enter();
        try {
            Frame dataset = Scope.track(parseTestFile("smalldata/prostate/prostate.csv", new int[]{0}));

            DeepLearningModel.DeepLearningParameters params = new DeepLearningModel.DeepLearningParameters();
            params._autoencoder = true;
            params._hidden = new int[]{ 3 };
            params._reproducible = true;

            final double tolerance = 0.000001;
            final Function2<Frame, Model, Vec[]> actualVectorsGetter = (frame, model) -> frame.vecs();
            final boolean ignoreTrainingMetrics = false;
            testIndependentlyCalculatedMetrics(dataset, params, dlConstructor, actualVectorsGetter, tolerance, ignoreTrainingMetrics);
        } finally {
            Scope.exit();
        }
    }
}
