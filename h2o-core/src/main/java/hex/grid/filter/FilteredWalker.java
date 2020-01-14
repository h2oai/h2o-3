package hex.grid.filter;

import hex.Model;
import hex.grid.HyperSpaceSearchCriteria;
import hex.grid.HyperSpaceWalker;

public class FilteredWalker<MP extends Model.Parameters>
        extends HyperSpaceWalker.BaseWalker<MP, HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria> {

  /**
   * Provides filtering logic to make walking process more efficient.
   */
  private final PermutationFilter<MP> _permutationFilter;
  private final HyperSpaceWalker.BaseWalker<MP, HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria> _walker;

  /**
   * Hyperparameter space walker which visits each combination of hyper parameters randomly.
   *
   * @param permutationFilter Provides a way to filter out redundant permutations.
   *
   *  For users, in order to specify exact rules for filtering, functions {@link FilterFunction}
   *  could be passed to the {@link HyperSpaceWalker} constructor.
   *
   *  For convenience there are two specific implementations of {@link FilterFunction}s interface:
   *          1. {@link KeepOnlyFirstMatchFilterFunction}
   *                          - could be used when it is needed to evaluate only one permutation from the group of matching permutations.
   *                          Typical use case is when we have interdependent(hierarchical) hyper parameters top level one of which is a boolean parameter.
   *                          When parameter;s value is false, we do not care about dependent hyper parameters( as they are disabled)
   *                          and we want to evaluate just this single case when feature is disabled.
   *
   *          2. {@link StrictFilterFunction}
   *                          - could be used when it is needed to specify exact sub-combination of hyper parameters values that in case of matching should be skipped
   *
   *   Usage example:
   *
   *  {@code
   *
   *     FilterFunction filterFunction1 =
   *          new KeepOnlyFirstMatchFilterFunction<Model.Parameters>(permutation -> !permutation._blending);
   *
   *     FilterFunction filterFunction2 =
   *          new StrictFilterFunction<Model.Parameters>(permutation -> !(permutation._k == 3.0 && permutation._f == 1.0));
   *
   *     PermutationFilter<Model.Parameters> defaultPermutationFilter = new AnyMatchPermutationFilter(filterFunction1, filterFunction2);
   *   }
   *   , where {@link Model.Parameters} should be substituted with specific implementation.
   */
  public FilteredWalker(HyperSpaceWalker.BaseWalker<MP, HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria> walker,
                        PermutationFilter<MP> permutationFilter) {
    super(walker.getParams(), walker.getHyperParamsGrid(), walker.getParametersBuilderFactory(), walker.search_criteria());
    _permutationFilter = permutationFilter;
    _walker = walker;
  }

  @Override
  public HyperSpaceIterator<MP> iterator() {
    return new FilteredHyperSpaceIterator<>(_walker.iterator(), _permutationFilter);
  }
}
