package hex.isotonic;

import hex.Model;
import hex.ModelCategory;
import hex.ModelMetrics;
import hex.ModelMetricsRegression;
import water.Job;
import water.Key;
import water.fvec.Frame;
import water.udf.CFuncRef;

import java.util.Arrays;

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
        if (Double.isNaN(x) || x < _output._min_x || x > _output._max_x) {
            preds[0] = Double.NaN;
            return preds;
        }
        final int pos = Arrays.binarySearch(_output._thresholds_x, x);
        final double y;
        if (pos >= 0) {
            y = _output._thresholds_y[pos];
        } else {
            final int lo = -pos - 2;
            final int hi = lo + 1;
            assert lo >= 0;
            assert hi < _output._thresholds_x.length;
            assert x > _output._thresholds_x[lo];
            assert x < _output._thresholds_x[hi];
            y = interpolate(x, _output._thresholds_x[lo], _output._thresholds_x[hi],
                    _output._thresholds_y[lo], _output._thresholds_y[hi]);
        }
        preds[0] = y;
        return preds;
    }

    private double clip(double x) {
        return clip(x, _output._min_x, _output._max_x);
    }

    static double clip(double x, double min, double max) {
        final double clipped;
        if (Double.isNaN(x))
            clipped = Double.NaN;
        else if (x < min)
            clipped = min;
        else
            clipped = Math.min(x, max);
        return clipped;
    }

    static double interpolate(double x, double xLo, double xHi, double yLo, double yHi) {
        final double slope = (yHi - yLo) / (xHi - xLo);
        return yLo + slope * (x - xLo);
    }

    @Override
    protected String[] makeScoringNames() {
        return new String[]{"predict"};
    }

    @Override
    protected String[][] makeScoringDomains(Frame adaptFrm, boolean computeMetrics, String[] names) {
        return new String[1][];
    }

}
