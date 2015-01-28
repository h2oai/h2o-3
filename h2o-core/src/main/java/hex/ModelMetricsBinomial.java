package hex;

import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;

public class ModelMetricsBinomial extends ModelMetrics {

  public final AUCData _aucdata;
  public final ConfusionMatrix _cm;
  public final HitRatio _hr;


  public ModelMetricsBinomial(Model model, Frame frame) {
    super(model, frame);

    _aucdata = null;
    _cm = null;
    _hr = null;
  }

  // TODO: make private
  public ModelMetricsBinomial(Model model, Frame frame, AUCData aucdata, ConfusionMatrix cm, HitRatio hr, double sigma, double mse) {
    super(model, frame, sigma, mse);

    _aucdata = aucdata;
    _cm = cm;
    _hr = hr;
  }

  public static ModelMetricsBinomial getFromDKV(Model model, Frame frame) {
    ModelMetrics mm = ModelMetrics.getFromDKV(model, frame);

    if (! (mm instanceof ModelMetricsBinomial))
      throw new H2OIllegalArgumentException("Expected to find a Binomial ModelMetrics for model: " + model._key.toString() + " and frame: " + frame._key.toString(),
              "Expected to find a ModelMetricsBinomial for model: " + model._key.toString() + " and frame: " + frame._key.toString() + " but found a: " + mm.getClass());

    return (ModelMetricsBinomial) mm;
  }
}
