package water.api;

import water.Key;
import water.Model;
import water.util.BeanUtils;

/**
 * An instance of a ModelParameters schema contains the Model build parameters (e.g., K and max_iters for KMeans).
 */
abstract public class ModelParametersSchema<P extends Model.Parameters, S extends ModelParametersSchema<P, S>> extends Schema<P, S> {
  ////////////////////////////////////////
  // NOTE:
  // Parameters must be ordered for the UI
  ////////////////////////////////////////

  /** List of fields in the order in which we want them serialized.  This is the order they will be presented in the UI. */
  abstract public String[] fields();

  // Parameters common to all models:
  @API(help="Training frame.")
  public Key src;              // Training Frame

  public ModelParametersSchema() {
  }

  public S fillFromImpl(P parms) {
    BeanUtils.copyProperties(this, parms, BeanUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
    return (S)this;
  }
}
