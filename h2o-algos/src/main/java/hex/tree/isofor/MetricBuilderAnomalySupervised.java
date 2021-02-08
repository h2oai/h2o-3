package hex.tree.isofor;

import hex.*;
import water.fvec.Frame;

public class MetricBuilderAnomalySupervised extends ModelMetricsBinomial.MetricBuilderBinomial<MetricBuilderAnomalySupervised> {

  public MetricBuilderAnomalySupervised(String[] domain) {
    super(domain);
  }

  /**
   * Create a ModelMetrics for a given model and frame
   * @param m Model
   * @param f Frame
   * @param frameWithExtraColumns Frame that contains extra columns such as weights (not used by MetricBuilderAnomalySupervised)
   * @param preds optional predictions (can be null, not used by MetricBuilderAnomalySupervised)
   * @return ModelMetricsBinomial
   */
  @Override public ModelMetrics makeModelMetrics(final Model m, final Frame f,
                                                 Frame frameWithExtraColumns, final Frame preds) {
    final double sigma;
    final double mse;
    final double logloss;
    final AUC2 auc;
    if (_wcount > 0) {
      sigma = weightedSigma();
      mse = _sumsqe / _wcount;
      logloss = _logloss / _wcount;
      auc = new AUC2(_auc);
    } else {
      sigma = Double.NaN;
      mse = Double.NaN;
      logloss = Double.NaN;
      auc = AUC2.emptyAUC();
    }
    ModelMetricsBinomial mm = new ModelMetricsBinomial(m, f, _count, mse, _domain,
            sigma, auc, logloss, null, null, _customMetric);
    if (m != null) {
      m.addModelMetrics(mm);
    }
    return mm;
  }

  @Override
  public double[] perRow(double[] ds, float[] yact, double w, double o, Model m) {
    adaptPreds(ds);
    return super.perRow(ds, yact, w, o, m);
  }

  private static void adaptPreds(double[] ds) {
    ds[2] = Math.min(ds[1], 1.0);
    ds[1] = 1 - ds[2];
    ds[0] = -1;
  }
}
