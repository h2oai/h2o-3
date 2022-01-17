package hex.tree.xgboost;

import hex.Model;
import hex.ModelBuilder;
import hex.genmodel.utils.DistributionFamily;
import org.junit.BeforeClass;
import org.junit.Test;
import water.MetricTest;
import water.Scope;
import water.fvec.Frame;

import java.util.function.Function;

public class XGBoostMetricTest extends MetricTest {
    
    private Function<Model.Parameters, ModelBuilder> xgBoostConstructor =  parameters -> {
        XGBoostModel.XGBoostParameters xgBoostParameters = (XGBoostModel.XGBoostParameters)parameters;
        return new XGBoost(xgBoostParameters);
    };

    @BeforeClass
    public static void setup() { stall_till_cloudsize(1); }

    @Test
    public void testIndependentModelMetricsCalculation_regression() {
        Scope.enter();
        try {
            String response = "AGE";
            Frame dataset = Scope.track(parseTestFile("smalldata/prostate/prostate.csv", new int[]{0}));

            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._ntrees = 10;
            parms._response_column = response;
            parms._distribution = DistributionFamily.gaussian;
            
            double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, parms, xgBoostConstructor, tolerance);
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

            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._ntrees = 10;
            parms._response_column = response;
            parms._distribution = DistributionFamily.bernoulli;
            
            double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, parms, xgBoostConstructor, tolerance);
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

            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._ntrees = 10;
            parms._response_column = response;
            parms._distribution = DistributionFamily.multinomial;
            
            double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, parms, xgBoostConstructor, tolerance);
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

            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._ntrees = 10;
            parms._response_column = response;
            parms._distribution = DistributionFamily.gaussian;
            parms._offset_column = "ID";

            double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, parms, xgBoostConstructor, tolerance);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testIndependentModelMetricsCalculationWithOffsetColumn_binomial() {
        Scope.enter();
        try {
            String response = "CAPSULE";
            Frame dataset = Scope.track(parseTestFile("smalldata/prostate/prostate.csv"));
            dataset.toCategoricalCol(response);

            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._ntrees = 10;
            parms._response_column = response;
            parms._distribution = DistributionFamily.bernoulli;
            parms._offset_column = "ID";

            double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, parms, xgBoostConstructor, tolerance);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testIndependentModelMetricsCalculationWithOffsetColumn_multinomial() {
        Scope.enter();
        try {
            String response = "Angaus";
            Frame dataset = Scope.track(parseTestFile("smalldata/gbm_test/ecology_model.csv"));
            dataset.toCategoricalCol(response);

            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._ntrees = 10;
            parms._response_column = response;
            parms._distribution = DistributionFamily.multinomial;
            parms._offset_column = "Site";

            double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, parms, xgBoostConstructor, tolerance);
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

            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._ntrees = 10;
            parms._response_column = response;
            parms._distribution = DistributionFamily.gaussian;
            parms._weights_column = "ID";

            double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, parms, xgBoostConstructor, tolerance);
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

            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._ntrees = 10;
            parms._response_column = response;
            parms._distribution = DistributionFamily.bernoulli;
            parms._weights_column = "ID";

            double tolerance = 0.000005;
            testIndependentlyCalculatedSupervisedMetrics(dataset, parms, xgBoostConstructor, tolerance);
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

            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._ntrees = 10;
            parms._response_column = response;
            parms._distribution = DistributionFamily.multinomial;
            parms._weights_column = "Site";

            double tolerance = 0.000001;
            testIndependentlyCalculatedSupervisedMetrics(dataset, parms, xgBoostConstructor, tolerance);
        } finally {
            Scope.exit();
        }
    }
}
