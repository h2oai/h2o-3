package hex.rulefit;

import hex.Model;
import hex.ModelBuilder;
import hex.genmodel.utils.DistributionFamily;
import hex.glm.GLM;
import hex.glm.GLMModel;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.MetricTest;
import water.Scope;
import water.fvec.Frame;
import water.util.fp.Function;


public class RuleFitMetricTest extends MetricTest {
    
    private Function<Model.Parameters, ModelBuilder> ruleFitConstructor = parameters -> {
        RuleFitModel.RuleFitParameters ruleFitParameters = (RuleFitModel.RuleFitParameters)parameters;
        return new RuleFit(ruleFitParameters);
    };

    @BeforeClass
    public static void setup() { stall_till_cloudsize(1); }

    @Test
    public void testIndependentModelMetricsCalculation_regression() {
        Scope.enter();
        try {
            String response = "AGE";
            Frame dataset = Scope.track(parseTestFile("smalldata/prostate/prostate.csv", new int[]{0}));

            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._response_column = response;
            params._distribution = DistributionFamily.gaussian;
            
            double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, params, ruleFitConstructor, tolerance);
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

            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._response_column = response;
            params._distribution = DistributionFamily.bernoulli;
            
            double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, params, ruleFitConstructor, tolerance);
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

            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._response_column = response;
            params._distribution = DistributionFamily.multinomial;
            
            double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, params, ruleFitConstructor, tolerance);
        } finally {
            Scope.exit();
        }
    }

    @Ignore // Offset column seems to be ignored by h2o runtime
    public void testIndependentModelMetricsCalculationWithOffsetColumn_regression() {
        Scope.enter();
        try {
            String response = "AGE";
            String offsetColumn = "offset";
            Frame dataset = Scope.track(parseTestFile("smalldata/prostate/prostate.csv", new int[]{0}));
            addRandomColumn(dataset, offsetColumn);

            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._response_column = response;
            params._distribution = DistributionFamily.gaussian;
            params._offset_column = offsetColumn;

            double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, params, ruleFitConstructor, tolerance);
        } finally {
            Scope.exit();
        }
    }

    @Ignore // Offset column seems to be ignored by h2o runtime
    public void testIndependentModelMetricsCalculationWithOffsetColumn_binomial() {
        Scope.enter();
        try {
            String response = "CAPSULE";
            String offsetColumn = "offset";
            Frame dataset = Scope.track(parseTestFile("smalldata/prostate/prostate.csv", new int[]{0}));
            addRandomColumn(dataset, offsetColumn);

            dataset.toCategoricalCol(response);

            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._response_column = response;
            params._distribution = DistributionFamily.bernoulli;
            params._offset_column = offsetColumn;

            double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, params, ruleFitConstructor, tolerance);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testIndependentModelMetricsCalculationWithOffsetColumn_multinomial() {
        Scope.enter();
        try {
            String response = "AGE";
            String offsetColumn = "offset";
            Frame dataset = Scope.track(parseTestFile("smalldata/prostate/prostate.csv", new int[]{0}));
            addRandomColumn(dataset, offsetColumn);

            dataset.toCategoricalCol(response);

            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._response_column = response;
            params._distribution = DistributionFamily.multinomial;
            params._offset_column = offsetColumn;

            double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, params, ruleFitConstructor, tolerance);
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

            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._response_column = response;
            params._distribution = DistributionFamily.gaussian;
            params._weights_column = "variWeight";
            params._ignored_columns = new String[] {"constWeight"};

            double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, params, ruleFitConstructor, tolerance);
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

            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._response_column = response;
            params._distribution = DistributionFamily.bernoulli;
            params._weights_column = "variWeight";
            params._ignored_columns = new String[] {"constWeight"};

            double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, params, ruleFitConstructor, tolerance);
        } finally {
            Scope.exit();
        }
    }

    @Ignore // https://h2oai.atlassian.net/browse/PUBDEV-8544 RuleFitMOJOWriter throws NPE
    public void testIndependentModelMetricsCalculationWithWeightColumn_multinomial() {
        Scope.enter();
        try {
            String response = "AGE";
            Frame dataset = Scope.track(parseTestFile("smalldata/prostate/prostate_NA_weights.csv"));
            dataset.toCategoricalCol(response);

            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._response_column = response;
            params._distribution = DistributionFamily.multinomial;
            params._weights_column = "variWeight";
            params._ignored_columns = new String[] {"constWeight"};

            double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, params, ruleFitConstructor, tolerance);
        } finally {
            Scope.exit();
        }
    }
}
