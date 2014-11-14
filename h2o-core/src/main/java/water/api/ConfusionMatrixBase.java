package water.api;

import hex.ConfusionMatrix2;

public class ConfusionMatrixBase<I extends ConfusionMatrix2, S extends ConfusionMatrixBase> extends Schema<I, ConfusionMatrixBase<I, S>> {
  @API(help="Confusion matrix (Actual/Predicted)", direction=API.Direction.OUTPUT)
  public long[][] confusion_matrix; // [actual][predicted]

  @API(help = "Prediction error by class", direction=API.Direction.OUTPUT)
  public double[] prediction_error_by_class;

  @API(help = "Prediction error", direction=API.Direction.OUTPUT)
  public double prediction_error;

  // Version&Schema-specific filling into the implementation object
  public I createImpl() {
    // TODO: this is bogus. . .
    ConfusionMatrix2 cm = new ConfusionMatrix2(this.confusion_matrix);
    return (I)cm;
  }

  // Version&Schema-specific filling from the implementation object
  public S fillFromImpl(I cm) {
    this.confusion_matrix = cm._arr;
    this.prediction_error_by_class = cm._classErr;
    this.prediction_error = cm._predErr;
    return (S)this;
  }
}
