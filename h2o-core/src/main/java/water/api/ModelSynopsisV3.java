package water.api;

import hex.Model;
import water.Iced;

/**
 * A Model synopsis contains only the minimal properties a Model: it's ID (key) and algo.
 */
public class ModelSynopsisV3
  extends ModelSchemaBase<Iced,ModelSynopsisV3> {

  public ModelSynopsisV3() {
  }

  public ModelSynopsisV3(Model m) {
    super(m);
  }
}
