package water.api;

import water.ConfusionMatrix2;

public class ConfusionMatrixBase extends Schema<ConfusionMatrix2, ConfusionMatrixBase> {
  @API(help="Confusion matrix (Actual/Predicted)")
  public long[][] confusion_matrix; // [actual][predicted]

  @API(help = "Prediction error by class")
  public double[] prediction_error_by_class;

  @API(help = "Prediction error")
  public double prediction_error;

  // Version&Schema-specific filling into the implementation object
  public ConfusionMatrix2 createImpl() {
    ConfusionMatrix2 cm = new ConfusionMatrix2(this.confusion_matrix);
    return cm;
  }

  // Version&Schema-specific filling from the implementation object
  public ConfusionMatrixBase fillFromImpl(ConfusionMatrix2 cm) {
    this.confusion_matrix = cm._arr;
    this.prediction_error_by_class = cm._classErr;
    this.prediction_error = cm._predErr;
    return this;
  }
}
