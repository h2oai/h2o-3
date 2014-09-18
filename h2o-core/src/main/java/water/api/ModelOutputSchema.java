package water.api;

import hex.Model;

/**
 * An instance of a ModelOutput schema contains the Model build output (e.g., the cluster centers for KMeans).
 */
abstract public class ModelOutputSchema<O extends Model.Output, S extends ModelOutputSchema<O, S>> extends Schema<O, S> {

  @API(help="Column names.")
  public String[] names;

  @API(help="Domains for categorical (enum) columns.")
  public String[][] domains;


  public ModelOutputSchema() {
  }

  public ModelOutputSchema(O p) {
  }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the handler
  abstract public O createImpl();

  // Version&Schema-specific filling from the impl
  abstract public S fillFromImpl( O p );
}
