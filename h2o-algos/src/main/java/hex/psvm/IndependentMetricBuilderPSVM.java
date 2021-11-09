package hex.psvm;

import hex.AUC2;
import hex.ModelMetrics;
import hex.ModelMetricsBinomial;
import hex.ModelMetricsSupervised;
import water.util.ArrayUtils;
import water.util.MathUtils;

public class IndependentMetricBuilderPSVM<T extends IndependentMetricBuilderPSVM<T>> extends ModelMetricsSupervised.IndependentMetricBuilderSupervised<T> {
    protected AUC2.AUCBuilder _auc;

    public IndependentMetricBuilderPSVM(String[] domain) {
        super(2, domain);
        _auc = new AUC2.AUCBuilder(AUC2.NBINS);
    }

    // Passed a float[] sized nclasses+1; ds[0] must be a prediction.  ds[1...nclasses-1] must be a class
    // distribution;
    @Override
    public double[] perRow(double ds[], float[] yact) {
        return perRow(ds, yact, 1, 0);
    }

    @Override
    public double[] perRow(double ds[], float[] yact, double w, double o) {
        if (Float.isNaN(yact[0])) return ds; // No errors if   actual   is missing
        if (ArrayUtils.hasNaNs(ds)) return ds;  // No errors if prediction has missing values
        if (w == 0 || Double.isNaN(w)) return ds;
        int iact = (int) yact[0];
        if (iact != 0 && iact != 1) return ds; // The actual is effectively a NaN
        _wY += w * iact;
        _wYY += w * iact * iact;
        // Compute error
        double err = iact + 1 < ds.length ? 1 - ds[iact + 1] : 1;  // Error: distance from predicting ycls as 1.0
        _sumsqe += w * err * err;           // Squared error
        _count++;
        _wcount += w;
        assert !Double.isNaN(_sumsqe);
        _auc.perRow(ds[2], iact, w);
        return ds;                // Flow coding
    }

    @Override
    public void reduce(T mb) {
        super.reduce(mb); // sumseq, count
        _auc.reduce(mb._auc);
    }

    @Override
    public ModelMetrics makeModelMetrics() {
        double mse = Double.NaN;
        double sigma = Double.NaN;
        final AUC2 auc;
        if (_wcount > 0) {
            sigma = weightedSigma();
            mse = _sumsqe / _wcount;
            auc = AUC2.make01AUC(_auc);
        } else {
            auc = AUC2.emptyAUC();
        }
        ModelMetricsBinomial mm = new ModelMetricsBinomial(null, null, _count, mse, _domain, sigma, auc, Double.NaN, null, _customMetric);
        return mm;
    }

    public String toString() {
        if (_wcount == 0) return "empty, no rows";
        return "mse = " + MathUtils.roundToNDigits(_sumsqe / _wcount, 3);
    }
}
