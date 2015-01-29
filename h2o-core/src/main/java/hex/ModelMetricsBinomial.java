package hex;

import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;

public class ModelMetricsBinomial extends ModelMetrics {

  public final AUCData _aucdata;
  public final ConfusionMatrix _cm;

  public ModelMetricsBinomial(Model model, Frame frame) {
    super(model, frame);
    _aucdata = null;
    _cm = null;
  }

  public ModelMetricsBinomial(Model model, Frame frame, AUCData aucdata, ConfusionMatrix cm, HitRatio hr) {
    super(model, frame);
    _aucdata = aucdata;
    _cm = cm;
  }

  @Override public ConfusionMatrix cm() {
    return _cm;
  }
  @Override public AUCData auc() {
    return _aucdata;
  }

  public static ModelMetricsBinomial getFromDKV(Model model, Frame frame) {
    ModelMetrics mm = ModelMetrics.getFromDKV(model, frame);

    if (! (mm instanceof ModelMetricsBinomial))
      throw new H2OIllegalArgumentException("Expected to find a Binomial ModelMetrics for model: " + model._key.toString() + " and frame: " + frame._key.toString(),
              "Expected to find a ModelMetricsBinomial for model: " + model._key.toString() + " and frame: " + frame._key.toString() + " but found a: " + mm.getClass());

    return (ModelMetricsBinomial) mm;
  }
}
