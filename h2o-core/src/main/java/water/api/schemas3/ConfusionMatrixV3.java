package water.api.schemas3;

import hex.ConfusionMatrix;
import water.api.API;

public class ConfusionMatrixV3 extends SchemaV3<ConfusionMatrix, ConfusionMatrixV3> {

  @API(help="Annotated confusion matrix", direction=API.Direction.OUTPUT)
  public TwoDimTableV3 table;

  // Version&Schema-specific filling into the implementation object
  public ConfusionMatrix createImpl() {
    // TODO: this is bogus. . .
    return new ConfusionMatrix(null, null);
  }

}
