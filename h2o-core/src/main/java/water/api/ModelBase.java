package water.api;

import water.H2O;
import water.Key;
import water.Model;
import water.api.API;
import water.api.Handler;
import water.api.Schema;

/**
 * TODO: this and its current subclasses include the model directly.  That's great for
 * getting stuff working quickly, but defeats the purpose of having schemas.
 * */
abstract public class ModelBase<M extends Model, S extends ModelBase<M, S>> extends Schema<M, S> {

  // Input fields
  @API(help="Model key", required=true)
  Key key;

  // TODO: should contain a ModelParametersSchema
  @API(help="A model.")
  protected
  M _model;

  public ModelBase() {
  }

  public ModelBase(M m) {
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
