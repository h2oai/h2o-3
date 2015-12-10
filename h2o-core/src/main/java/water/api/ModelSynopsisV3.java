package water.api;

import hex.Model;

/**
 * A Model synopsis contains only the minimal properties a Model: it's ID (key) and algo.
 */
public class ModelSynopsisV3
  extends ModelSchemaBase<Model,ModelSynopsisV3> {

  public ModelSynopsisV3() {
  }

  public ModelSynopsisV3(Model m) {
    super(m);
  }
}
