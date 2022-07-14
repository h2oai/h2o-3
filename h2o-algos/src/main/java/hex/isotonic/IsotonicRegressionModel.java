package hex.isotonic;

import hex.Model;
import hex.ModelCategory;
import hex.ModelMetrics;
import hex.ModelMetricsRegression;
import water.Key;

import java.util.Arrays;

public class IsotonicRegressionModel extends Model<IsotonicRegressionModel, 
        IsotonicRegressionModel.IsotonicRegressionParameters, IsotonicRegressionModel.IsotonicRegressionOutput> {

    public IsotonicRegressionModel(Key<IsotonicRegressionModel> selfKey, 
                                   IsotonicRegressionParameters parms, IsotonicRegressionOutput output) {
        super(selfKey, parms, output);
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
    }

    @Override
    public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
        return new ModelMetricsRegression.MetricBuilderRegression<>();
    }

    @Override
    protected double[] score0(double[] data, double[] preds) {
        final double x = data[0];
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

    static double interpolate(double x, double xLo, double xHi, double yLo, double yHi) {
        final double slope = (yHi - yLo) / (xHi - xLo);
        return yLo + slope * (x - xLo);
    }

}
