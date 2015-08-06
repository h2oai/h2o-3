package hex.grid;

import hex.Model;
import hex.ModelBuilder;

/** FIXME
 *
 * @param <MP>
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
