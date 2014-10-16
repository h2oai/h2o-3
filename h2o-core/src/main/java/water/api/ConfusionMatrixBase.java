package water.api;

import hex.ConfusionMatrix2;

public class ConfusionMatrixBase extends Schema<ConfusionMatrix2, ConfusionMatrixBase> {
  @API(help="Confusion matrix (Actual/Predicted)", direction=API.Direction.OUTPUT)
  public long[][] confusion_matrix; // [actual][predicted]

  @API(help = "Prediction error by class", direction=API.Direction.OUTPUT)
  public double[] prediction_error_by_class;

  @API(help = "Prediction error", direction=API.Direction.OUTPUT)
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
