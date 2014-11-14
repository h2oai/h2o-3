package water.api;

import hex.Model;

/**
 * An instance of a ModelOutput schema contains the Model build output (e.g., the cluster centers for KMeans).
 */
abstract public class ModelOutputSchema<O extends Model.Output, S extends ModelOutputSchema<O, S>> extends Schema<O, S> {

  @API(help="Column names.", direction=API.Direction.OUTPUT)
  public String[] names;

  @API(help="Domains for categorical (enum) columns.", direction=API.Direction.OUTPUT)
  public String[][] domains;

  @API(help="Category of the model (e.g., Binomial).", values={"Unknown", "Binomial", "Multinomial", "Regression", "Clustering"})
  Model.ModelCategory model_category;

  public ModelOutputSchema() {
    super();
  }

  public S fillFromImpl( O impl ) {
    super.fillFromImpl(impl);
    this.model_category = impl.getModelCategory();
    return (S)this;
  }
}
