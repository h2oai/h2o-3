package water.api;

import hex.ConfusionMatrix;
import water.util.TwoDimTable;

public class ConfusionMatrixBase<I extends ConfusionMatrix, S extends ConfusionMatrixBase> extends Schema<I, ConfusionMatrixBase<I, S>> {
  @API(help="Confusion matrix (Actual/Predicted)", direction=API.Direction.OUTPUT)
  public long[][] confusion_matrix; // [actual][predicted]

  @API(help = "Prediction error by class", direction=API.Direction.OUTPUT)
  public double[] prediction_error_by_class;

  @API(help = "Prediction error", direction=API.Direction.OUTPUT)
  public double prediction_error;

  @API(help="Annotated confusion matrix", direction=API.Direction.OUTPUT)
  public TwoDimTableV1 table;

  // Version&Schema-specific filling into the implementation object
  public I createImpl() {
    // TODO: this is bogus. . .
    ConfusionMatrix cm = new ConfusionMatrix(this.confusion_matrix, null);
    return (I)cm;
  }
}
