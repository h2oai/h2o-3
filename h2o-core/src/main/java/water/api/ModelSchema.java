package water.api;

import hex.Model;
import water.AutoBuffer;
import water.Key;
import water.util.PojoUtils;

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
abstract public class ModelSchema<M extends Model, P extends Model.Parameters, O extends Model.Output, S extends ModelSchema<M, P, O, S>> extends Schema<M, S> {
  // Input fields
  @API(help="Model key", required=true, direction=API.Direction.INOUT)
  protected Key key;

  // Output fields
  @API(help="The build parameters for the model (e.g. K for KMeans).", direction=API.Direction.OUTPUT)
  protected ModelParametersSchema parameters;

  @API(help="The build output for the model (e.g. the clusters for KMeans).", direction=API.Direction.OUTPUT)
  protected ModelOutputSchema output;

  @API(help="Compatible frames, if requested", direction=API.Direction.OUTPUT)
  String[] compatible_frames;

  @API(help="Checksum for all the things that go into building the Model.", direction=API.Direction.OUTPUT)
  protected long checksum;

  public ModelSchema() {
  }

  /* Key-only constructor, for the times we only want to return the key. */
  ModelSchema( Key key ) { this.key = key; }

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

  // Version&Schema-specific filling from the impl
  @Override public S fillFromImpl( M m ) {
    this.key = m._key;
    this.checksum = m.checksum();

    parameters = createParametersSchema();
    parameters.fillFromImpl(m._parms);

    output = createOutputSchema();
    output.fillFromImpl(m._output);

    return (S)this; // have to cast because the definition of S doesn't include ModelSchema
  }

  @Override
  public AutoBuffer writeJSON_impl( AutoBuffer ab ) {
    ab.putJSONStr("key", key.toString());
    ab.put1(',');

    // Builds ModelParameterSchemaV2 objects for each field, and then calls writeJSON on the array
    ModelParametersSchema.writeParametersJSON(ab, parameters, createParametersSchema());
    ab.put1(',');

    if (null == output) { // allow key-only output
      output = createOutputSchema();
    }
    // Let output render itself:
    ab.putJSON("output", output);
    ab.put1(',');

    ab.putJSONAStr("compatible_frames", compatible_frames);
    return ab;
  }

}
