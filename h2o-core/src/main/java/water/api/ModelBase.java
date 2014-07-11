package water.api;

import water.H2O;
import water.Key;
import water.Model;
import water.api.API;
import water.api.Handler;
import water.api.Schema;
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
abstract public class ModelBase<M extends Model, P extends Model.Parameters, O extends Model.Output, S extends ModelBase<M, P, O, S>> extends Schema<M, S> {

  // Input fields
  @API(help="Model key", required=true)
  Key key;

  @API(help="The build parameters for the model (e.g. K for KMeans).")
  protected ModelParametersBase parameters;

  @API(help="The build output for the model (e.g. the clusters for KMeans).")
  protected ModelOutputBase output;

  public ModelBase() {
  }

  public ModelBase(M m) {
    BeanUtils.copyProperties(this.parameters, m.getParms(), BeanUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
    BeanUtils.copyProperties(this.output, m.getOutput(), BeanUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
  }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public M createImpl() {
// TODO:    M dummy = new M();
    return null;
  }

  // Version&Schema-specific filling from the impl
  @Override public S fillFromImpl( M m ) {
    this.key = m._key;
    BeanUtils.copyProperties(this.parameters, m.getParms(), BeanUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
    BeanUtils.copyProperties(this.output, m.getOutput(), BeanUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
    return (S)this; // have to cast because the definition of S doesn't include ModelBase
  }
}
