package water.test.dummy;

import hex.Model;
import hex.ModelMetrics;
import hex.ModelMetricsBinomial;
import hex.ModelMetricsRegression;
import water.Futures;
import water.Key;
import water.fvec.Frame;

public class DummyModel extends Model<DummyModel, DummyModelParameters, DummyModelOutput> {
  public DummyModel(Key<DummyModel> selfKey, DummyModelParameters parms, DummyModelOutput output) {
    super(selfKey, parms, output);
  }
  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    if (domain == null) return new ModelMetricsRegression.MetricBuilderRegression();
    return new ModelMetricsBinomial.MetricBuilderBinomial(domain);
  }
  @Override
  protected double[] score0(double[] data, double[] preds) { return preds; }

  @Override
  public Frame transform(Frame fr) {
    return fr == null ? null : fr.toTwoDimTable(0, 10, true).asFrame(Key.make(fr._key+"_stats"), true); 
  }

  @Override
  protected Futures remove_impl(Futures fs, boolean cascade) {
    super.remove_impl(fs, cascade);
    if (_parms._action != null) {
      _parms._action.cleanUp();
    }
    return fs;
  }
}
