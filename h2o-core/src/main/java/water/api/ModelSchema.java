package water.api;

import water.Key;
import water.Model;
import water.util.BeanUtils;

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
  protected
  Key key;

  @API(help="The build parameters for the model (e.g. K for KMeans).")
  protected ModelParametersSchema parameters;

  @API(help="The build output for the model (e.g. the clusters for KMeans).")
  protected ModelOutputSchema output;

  public ModelSchema() {
  }

  public ModelSchema(M m) {
    BeanUtils.copyProperties(this.parameters, m._parms, BeanUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
    BeanUtils.copyProperties(this.output, m._output, BeanUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
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

    return (S)this; // have to cast because the definition of S doesn't include ModelSchema
  }
}
