package water.test.dummy;

import hex.Model;
import hex.ModelMetrics;
import hex.ModelMetricsBinomial;
import water.Futures;
import water.Key;

public class DummyModel extends Model<DummyModel, DummyModelParameters, DummyModelOutput> {
  public DummyModel(Key<DummyModel> selfKey, DummyModelParameters parms, DummyModelOutput output) {
    super(selfKey, parms, output);
  }
  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    return new ModelMetricsBinomial.MetricBuilderBinomial(domain);
  }
  @Override
  protected double[] score0(double[] data, double[] preds) { return preds; }
  @Override
  protected Futures remove_impl(Futures fs, boolean cascade) {
    super.remove_impl(fs, cascade);
    if (_parms._action != null) {
      _parms._action.cleanUp();
    }
    return fs;
  }
}
