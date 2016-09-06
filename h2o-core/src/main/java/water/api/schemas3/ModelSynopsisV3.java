package water.api.schemas3;

import hex.Model;

/**
 * A Model synopsis contains only the minimal properties a Model: it's ID (key) and algo.
 */
public class ModelSynopsisV3<M extends Model<M, ?, ?>> extends ModelSchemaBaseV3<M, ModelSynopsisV3<M>> {

  public ModelSynopsisV3() {
  }

  public ModelSynopsisV3(M m) {
    super(m);
  }
}
