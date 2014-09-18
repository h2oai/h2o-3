package water.api;

import hex.Model;
import water.AutoBuffer;
import water.Key;
import water.util.PojoUtils;

/**
 * A Model schema contains all the pieces associated with a Model:
 * <p>
 * <ul>
 * <li> an instance of a ModelParameters schema containing the build parameters
 * <li> an instance of a ModelResults schema containing the f00 b4r b4z
 * <li> an instance of a ModelMetrics schema
 * <ul>
 *
 *
 */
abstract public class ModelSchema<M extends Model, P extends Model.Parameters, O extends Model.Output, S extends ModelSchema<M, P, O, S>> extends Schema<M, S> {

  // Input fields
  @API(help="Model key", required=true)
  protected Key key;

  // Output fields
  @API(help="Unique id")
  protected UniqueIdBase unique_id;

  @API(help="The build parameters for the model (e.g. K for KMeans).")
  protected ModelParametersSchema parameters;

  @API(help="The build output for the model (e.g. the clusters for KMeans).")
  protected ModelOutputSchema output;

  @API(help="Compatible frames", direction = API.Direction.OUTPUT, json=true)
  protected FramesBase compatible_frames; // TODO: create interface or superclass (e.g., FrameBase) for FrameV2

  public ModelSchema() {
  }

  public ModelSchema(M m) {
    PojoUtils.copyProperties(this.parameters, m._parms, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
    PojoUtils.copyProperties(this.output, m._output, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
  }

  //==========================
  // Custom adapters go here

  // TOOD: I think we can implement the following two here, using reflection on the type parameters.

  /** Factory method to create the model-specific parameters schema. */
  abstract public ModelParametersSchema createParametersSchema();

  /** Factory method to create the model-specific output schema. */
  abstract public ModelOutputSchema createOutputSchema();

  // Version&Schema-specific filling into the impl
  @Override public M createImpl() {
// TODO:    M dummy = new M();
    return null;
  }

  // Version&Schema-specific filling from the impl
  @Override public S fillFromImpl( M m ) {
    this.key = m._key;

    parameters = createParametersSchema();
    parameters.fillFromImpl(m._parms);

    output = createOutputSchema();
    output.fillFromImpl(m._output);

    unique_id = new UniqueIdV3();
    unique_id.fillFromImpl(m.getUniqueId());

    return (S)this; // have to cast because the definition of S doesn't include ModelSchema
  }

  @Override
  public AutoBuffer writeJSON_impl( AutoBuffer ab ) {
    ab.putJSONStr("key", key.toString());
    ab.put1(',');

    // Builds ModelParameterSchemaV2 objects for each field, and then calls writeJSON on the array
    ModelParametersSchema.writeParametersJSON(ab, parameters, createParametersSchema());
    ab.put1(',');

    // Let output render itself:
    ab.putJSON ("output", output);
    ab.put1(',');

    // TODO: compatible_frames should only have the list of keys; the containing request should contain all the frames.
    // Let the compatible_frames render themselves:
    ab.putJSON("compatible_frames", compatible_frames);
    return ab;
  }

}
