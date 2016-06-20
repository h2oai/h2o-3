package water.api;

import hex.ConfusionMatrix;

public class ConfusionMatrixBase<I extends ConfusionMatrix, S extends ConfusionMatrixBase>
    extends SchemaV3<I, ConfusionMatrixBase<I,S>> {
  @API(help="Annotated confusion matrix", direction=API.Direction.OUTPUT)
  public TwoDimTableV3 table;

  // Version&Schema-specific filling into the implementation object
  public I createImpl() {
    // TODO: this is bogus. . .
    ConfusionMatrix cm = new ConfusionMatrix(null, null);
    return (I)cm;
  }
}
