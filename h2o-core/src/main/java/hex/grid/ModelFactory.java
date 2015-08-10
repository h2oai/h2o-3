package hex.grid;

import hex.Model;
import hex.ModelBuilder;

/** A model factory interface producing model builders of given
 * type.
 *
 * @param <MP> model builder input parameter type
 */
public interface ModelFactory<MP extends Model.Parameters> {

  /**
   * Returns model name produced by this factory.
   * @return model name
   */
  public String getModelName();

  /**
   * Returns model builder building a model for given parameters.
   * @param params model parameters.
   * @return model builder for given parameters.
   */
  public ModelBuilder buildModel(MP params);
}
