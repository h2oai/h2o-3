package water.api;

import water.api.Schema;
import water.Model;

/**
 * An instance of a ModelParameters schema contains the Model build parameters (e.g., K for KMeans).
 */
abstract public class ModelParametersBase<P extends Model.Parameters, S extends ModelParametersBase<P, S>> extends Schema<P, S> {

  public ModelParametersBase() {
  }

  public ModelParametersBase(P p) {
  }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the handler
  abstract public P createImpl();

  // Version&Schema-specific filling from the impl
  abstract public S fillFromImpl( P p );
}
