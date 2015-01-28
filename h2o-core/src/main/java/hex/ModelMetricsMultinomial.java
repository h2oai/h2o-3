package hex;

import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;

public class ModelMetricsMultinomial extends ModelMetrics {
  public final ConfusionMatrix _cm;
  public final HitRatio _hr;

  public ModelMetricsMultinomial(Model model, Frame frame) {
    super(model, frame);
    _cm=null;
    _hr=null;
  }
  public ModelMetricsMultinomial(Model model, Frame frame, ConfusionMatrix cm, HitRatio hr) {
    super(model, frame);
    _cm = cm;
    _hr = hr;
  }

  @Override public ConfusionMatrix cm() {
    return _cm;
  }
  @Override public HitRatio hr() {
    return _hr;
  }

  public static ModelMetricsMultinomial getFromDKV(Model model, Frame frame) {
    ModelMetrics mm = ModelMetrics.getFromDKV(model, frame);

    if (! (mm instanceof ModelMetricsMultinomial))
      throw new H2OIllegalArgumentException("Expected to find a Multinomial ModelMetrics for model: " + model._key.toString() + " and frame: " + frame._key.toString(),
              "Expected to find a ModelMetricsMultinomial for model: " + model._key.toString() + " and frame: " + frame._key.toString() + " but found a: " + mm.getClass());

    return (ModelMetricsMultinomial) mm;
  }
}
