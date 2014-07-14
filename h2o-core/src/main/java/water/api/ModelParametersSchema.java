package water.api;

import water.Key;
import water.Model;
import water.util.BeanUtils;

/**
 * An instance of a ModelParameters schema contains the Model build parameters (e.g., K for KMeans).
 */
abstract public class ModelParametersSchema<P extends Model.Parameters, S extends ModelParametersSchema<P, S>> extends Schema<P, S> {
  // Parameters common to all models:
  @API(help="Training frame.")
  public Key src;              // Training Frame

  public ModelParametersSchema() {
  }

  public ModelParametersSchema(P p) {
  }

  public S fillFromImpl(P parms) {
    BeanUtils.copyProperties(this, parms, BeanUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
    return (S)this;
  }
}
