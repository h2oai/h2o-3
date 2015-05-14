package water.api;

import hex.Model;

/**
 * A Model synopsis contains only the minimal properties a Model: it's ID (key) and algo.
 */
public class ModelSynopsisV3<M extends Model<M, P, O>,
        S extends ModelSchema<M, S, P, PS, O, OS>,
        P extends Model.Parameters,
        PS extends ModelParametersSchema<P, PS>,
        O extends Model.Output,
        OS extends ModelOutputSchema<O, OS>>
  extends ModelSchemaBase<M,S,P,PS,O,OS> {

  public ModelSynopsisV3() {
  }

  public ModelSynopsisV3(M m) {
    super(m);
  }
}
