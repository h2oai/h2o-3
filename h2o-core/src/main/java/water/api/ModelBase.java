package water.api;

import water.H2O;
import water.Key;
import water.Model;
import water.api.API;
import water.api.Handler;
import water.api.Schema;

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

  // TODO: should contain a ModelParametersBase and ModelOutputBase
  @API(help="A model.")
  protected M _model;

  public ModelBase() {
  }

  public ModelBase(M m) {
//    this.parameters = m.getParms();
    this._model = m;
  }

  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override public M createImpl() {
    return (M)this._model; // have to cast because the definition of S doesn't include ModelBase
  }

  // Version&Schema-specific filling from the handler
  @Override public S fillFromImpl( M m ) {
    this._model = m;
    this.key = m._key;
    return (S)this; // have to cast because the definition of S doesn't include ModelBase
  }

  //@Override public HTML writeHTML_impl( HTML ab ) {
  //  ab.title("KMeansModel Viewer");
  //  return ab;
  //}
}
