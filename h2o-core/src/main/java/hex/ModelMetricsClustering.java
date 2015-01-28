package hex;

import water.fvec.Frame;

public class ModelMetricsClustering extends ModelMetrics {
  public final double[] within_mse; //TODO: add Clustering specific members
  public ModelMetricsClustering(Model model, Frame frame) {
    super(model, frame);
    within_mse = null;
  }
  public ModelMetricsClustering(Model model, Frame frame, double[] mse) {
    super(model, frame);
    within_mse = mse;
  }
}
