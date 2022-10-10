package hex.isotonic;

import hex.*;
import hex.genmodel.algos.isotonic.IsotonicCalibrator;
import hex.genmodel.algos.isotonic.IsotonicRegressionUtils;
import water.Job;
import water.Key;
import water.fvec.Frame;
import water.udf.CFuncRef;

public class IsotonicRegressionModel extends Model<IsotonicRegressionModel, 
        IsotonicRegressionModel.IsotonicRegressionParameters, IsotonicRegressionModel.IsotonicRegressionOutput> {

    public IsotonicRegressionModel(Key<IsotonicRegressionModel> selfKey, 
                                   IsotonicRegressionParameters parms, IsotonicRegressionOutput output) {
        super(selfKey, parms, output);
    }

    @Override
    protected Model<IsotonicRegressionModel, IsotonicRegressionParameters, IsotonicRegressionOutput>.BigScore makeBigScoreTask(String[][] domains, String[] names, Frame adaptFrm, boolean computeMetrics, boolean makePrediction, Job j, CFuncRef customMetricFunc) {
        return super.makeBigScoreTask(domains, names, adaptFrm, computeMetrics, makePrediction, j, customMetricFunc);
    }

    public enum OutOfBoundsHandling {
        NA, Clip
    }

    public static class IsotonicRegressionParameters extends Model.Parameters {
        public String algoName() {
            return "IsotonicRegression";
        }

        public String fullName() {
            return "Isotonic Regression";
        }

        public String javaName() {
            return IsotonicRegressionModel.class.getName();
        }

        @Override
        public long progressUnits() {
            return 1;
        }

        public OutOfBoundsHandling _out_of_bounds = OutOfBoundsHandling.NA;

    }

    public static class IsotonicRegressionOutput extends Model.Output {

        public long _nobs;
        public double[] _thresholds_y;
        public double[] _thresholds_x;
        public double _min_x;
        public double _max_x;
        
        public IsotonicRegressionOutput(IsotonicRegression b) {
            super(b);
        }

        @Override
        public ModelCategory getModelCategory() {
            return ModelCategory.Regression;
        }

        @Override
        public String[] classNames() {
            return null;
        }

    }

    @Override
    public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
        return new ModelMetricsRegression.MetricBuilderRegression<>();
    }

    @Override
    protected double[] score0(double[] data, double[] preds) {
        final double x = _parms._out_of_bounds == OutOfBoundsHandling.Clip ? clip(data[0]) : data[0];
        preds[0] = IsotonicRegressionUtils.score(x, 
                _output._min_x, _output._max_x, _output._thresholds_x, _output._thresholds_y);
        return preds;
    }

    private double clip(double x) {
        return IsotonicRegressionUtils.clip(x, _output._min_x, _output._max_x);
    }

    @Override
    protected String[] makeScoringNames() {
        return new String[]{"predict"};
    }

    @Override
    protected String[][] makeScoringDomains(Frame adaptFrm, boolean computeMetrics, String[] names) {
        return new String[1][];
    }

    public IsotonicCalibrator toIsotonicCalibrator() {
        return new IsotonicCalibrator(
                _output._min_x, _output._max_x,
                _output._thresholds_x, _output._thresholds_y
        );
    }

    @Override
    public boolean haveMojo() {
        return true;
    }

    @Override
    public IsotonicRegressionMojoWriter getMojo() {
        return new IsotonicRegressionMojoWriter(this);
    }

}
