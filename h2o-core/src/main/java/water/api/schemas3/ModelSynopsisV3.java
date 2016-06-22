package water.api.schemas3;

import hex.Model;

/**
 * A Model synopsis contains only the minimal properties a Model: it's ID (key) and algo.
 */
public class ModelSynopsisV3
  extends ModelSchemaBaseV3<Model,ModelSynopsisV3> {

  public ModelSynopsisV3() {
  }

  public ModelSynopsisV3(Model m) {
    super(m);
  }
}
