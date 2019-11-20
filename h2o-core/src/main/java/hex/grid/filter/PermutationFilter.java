package hex.grid.filter;

import hex.Model;

/**
 * Provides interface required to enable filtering within {@link hex.grid.HyperSpaceWalker}
 * 
 * @param <MP> as {@link hex.grid.HyperSpaceWalker} is working only with descendants of the {@link Model.Parameters},
 *            this interface is generic with corresponding type parameter
 */
public interface PermutationFilter<MP extends Model.Parameters> {

  /**
   * Main method that decides whether given {@code permutation} should be skipped
   * @param permutation instance of {@link Model.Parameters} that represents particular
   *                   combination of hyper parameters during grid search
   * @return `true` when permutation should be skipped
   */
  boolean permutationIsSkipped(MP permutation);

  /**
   * As filtering logic might be stateful we need an ability to reset it to an initial state
   */
  void reset();
}
