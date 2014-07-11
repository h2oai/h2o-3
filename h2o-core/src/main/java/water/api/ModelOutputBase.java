package water.api;

import water.Model;
import water.api.Schema;

/**
 * An instance of a ModelOutput schema contains the Model build output (e.g., the cluster centers for KMeans).
 */
abstract public class ModelOutputBase<O extends Model.Output, S extends ModelOutputBase<O, S>> extends Schema<O, S> {

  public ModelOutputBase() {
  }

  public ModelOutputBase(O p) {
  }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the handler
  abstract public O createImpl();

  // Version&Schema-specific filling from the impl
  abstract public S fillFromImpl( O p );
}
