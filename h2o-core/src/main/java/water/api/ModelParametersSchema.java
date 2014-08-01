package water.api;

import water.AutoBuffer;
import water.H2O;
import water.Key;
import water.Model;
import water.util.BeanUtils;

import java.lang.reflect.Field;

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

  /**
   * Write the parameters, including their metadata, into an AutoBuffer.  Used by
   * ModelBuilderSchema#writeJSON_impl and ModelSchema#writeJSON_impl.
   */
  public static final AutoBuffer writeParametersJSON( AutoBuffer ab, ModelParametersSchema parameters, ModelParametersSchema default_parameters) {
    String[] fields = parameters.fields();

    // Build ModelParameterSchemaV2 objects for each field, and the call writeJSON on the array
    ModelParameterSchemaV2[] metadata = new ModelParameterSchemaV2[fields.length];

    String field_name = null;
    try {
      for (int i = 0; i < fields.length; i++) {
        field_name = fields[i];
        Field f = parameters.getClass().getField(field_name);

        // TODO: cache a default parameters schema
        ModelParameterSchemaV2 schema = new ModelParameterSchemaV2(parameters, default_parameters, f);
        metadata[i] = schema;
      }
    } catch (NoSuchFieldException e) {
      throw H2O.fail("Caught exception accessing field: " + field_name + " for schema object: " + parameters + ": " + e.toString());
    }

    ab.putJSONA("parameters", metadata);
    return ab;
  }


}
