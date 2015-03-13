package water.api;

import hex.Model;
import hex.ModelBuilder;
import water.AutoBuffer;
import water.H2O;
import water.api.KeyV1.ModelKeyV1;
import water.exceptions.H2OIllegalArgumentException;
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
public class ModelSchema<M extends Model<M, P, O>,
                                  S extends ModelSchema<M, S, P, PS, O, OS>,
                                  P extends Model.Parameters,
                                  PS extends ModelParametersSchema<P, PS>,
                                  O extends Model.Output,
                                  OS extends ModelOutputSchema<O, OS>>
    extends Schema<M, S> {
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // CAREFUL: This class has its own JSON serializer.  If you add a field here you probably also want to add it to the serializer!
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Input fields
  @API(help="Model key", required=true, direction=API.Direction.INOUT)
  public ModelKeyV1 key;

  // Output fields
  @API(help="The algo name for this Model.", direction=API.Direction.OUTPUT)
  public String algo;

  @API(help="The build parameters for the model (e.g. K for KMeans).", direction=API.Direction.OUTPUT)
  public PS parameters;

  @API(help="The build output for the model (e.g. the cluster centers for KMeans).", direction=API.Direction.OUTPUT)
  public OS output;

  @API(help="Compatible frames, if requested", direction=API.Direction.OUTPUT)
  String[] compatible_frames;

  @API(help="Checksum for all the things that go into building the Model.", direction=API.Direction.OUTPUT)
  protected long checksum;

  public ModelSchema() {
    super();
  }

  public ModelSchema(M m) {
    this();
    PojoUtils.copyProperties(this.parameters, m._parms, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
    PojoUtils.copyProperties(this.output, m._output, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
  }

  //==========================
  // Custom adapters go here

  // TODO: I think we can implement the following two here, using reflection on the type parameters.

  /** Factory method to create the model-specific parameters schema. */
  public PS createParametersSchema() { throw H2O.fail("createParametersSchema() must be implemented in class: " + this.getClass()); }

  /** Factory method to create the model-specific output schema. */
  public OS createOutputSchema() { throw H2O.fail("createOutputSchema() must be implemented in class: " + this.getClass()); }

  // Version&Schema-specific filling from the impl
  @Override public S fillFromImpl( M m ) {
    this.algo = ModelBuilder.getAlgo(m);
    // Key<? extends Model> k = m._key;
    this.key = new ModelKeyV1(m._key);
    this.checksum = m.checksum();

    parameters = createParametersSchema();
    parameters.fillFromImpl(m._parms);

    output = createOutputSchema();
    output.fillFromImpl(m._output);

    return (S)this; // have to cast because the definition of S doesn't include ModelSchema
  }

  @Override
  public AutoBuffer writeJSON_impl( AutoBuffer ab ) {
    ab.put1(','); // the schema and version fields get written before we get called
    ab.putJSONStr("algo", algo);
    ab.put1(',');
    ab.putJSON("key", key);
    ab.put1(',');

    // Builds ModelParameterSchemaV2 objects for each field, and then calls writeJSON on the array
    try {
      PS defaults = createParametersSchema().fillFromImpl(parameters.getImplClass().newInstance());
      ModelParametersSchema.writeParametersJSON(ab, parameters, defaults);
      ab.put1(',');
    }
    catch (Exception e) {
      String msg = "Error creating an instance of ModelParameters for algo: " + algo;
      String dev_msg = "Error creating an instance of ModelParameters for algo: " + algo + ": " + this.getImplClass();
      throw new H2OIllegalArgumentException(msg, dev_msg);
    }

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
