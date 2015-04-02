package water.api;

import hex.ConfusionMatrix;
import water.util.TwoDimTable;

public class ConfusionMatrixBase<I extends ConfusionMatrix, S extends ConfusionMatrixBase> extends Schema<I, ConfusionMatrixBase<I, S>> {
  @API(help="Annotated confusion matrix", direction=API.Direction.OUTPUT)
  public TwoDimTableV1 table;

  // Version&Schema-specific filling into the implementation object
  public I createImpl() {
    // TODO: this is bogus. . .
    ConfusionMatrix cm = new ConfusionMatrix(null, null);
    return (I)cm;
  }
}
