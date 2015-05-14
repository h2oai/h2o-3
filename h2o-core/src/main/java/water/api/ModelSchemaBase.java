package water.api;

import hex.Model;
import hex.ModelBuilder;
import water.api.KeyV3.ModelKeyV3;

/**
 * A Model schema contains all the pieces associated with a Model:
 * <p>
 * <ul>
 * <li> an instance of a ModelParameters schema containing the build parameters</li>
 * <li> an instance of a ModelResults schema containing the f00 b4r b4z</li>
 * <li> an instance of a ModelMetrics schema</li>
 * </ul>
 *
 */
abstract public class ModelSchemaBase<M extends Model<M, P, O>,
                                  S extends ModelSchema<M, S, P, PS, O, OS>,
                                  P extends Model.Parameters,
                                  PS extends ModelParametersSchema<P, PS>,
                                  O extends Model.Output,
                                  OS extends ModelOutputSchema<O, OS>>
    extends Schema<M, S> {

  public ModelSchemaBase() {

  }

  // Input fields
  @API(help="Model key", required=true, direction=API.Direction.INOUT)
  public ModelKeyV3 model_id;

  // Output fields
  @API(help="The algo name for this Model.", direction=API.Direction.OUTPUT)
  public String algo;

  @API(help="The pretty algo name for this Model (e.g., Generalized Linear Model, rather than GLM).", direction=API.Direction.OUTPUT)
  public String algo_full_name;

  public ModelSchemaBase(M m) {
    super();
    this.model_id = new ModelKeyV3(m._key);
    this.algo = ModelBuilder.getAlgo(m);
    this.algo_full_name = ModelBuilder.getAlgoFullName(this.algo);
  }
}
