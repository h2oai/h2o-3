package hex;

import water.fvec.Frame;

public class ModelMetricsBinomialGLM extends ModelMetricsBinomial {

  public ModelMetricsBinomialGLM(Model model, Frame frame) {
    super(model, frame);
  }

  public ModelMetricsBinomialGLM(Model model, Frame frame, AUCData aucdata, double sigma, double mse) {
    super(model, frame, aucdata, sigma, mse);
  }
}
