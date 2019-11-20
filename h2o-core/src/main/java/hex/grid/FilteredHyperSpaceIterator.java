package hex.grid;

import hex.Model;
import hex.grid.filter.PermutationFilter;

public class FilteredHyperSpaceIterator<MP extends Model.Parameters> implements HyperSpaceWalker.HyperSpaceIterator<MP> {
  private HyperSpaceWalker.HyperSpaceIterator<MP> _hyperSpaceIterator;

  private PermutationFilter<MP> _permutationFilter;

  /**
   * Total number of visited permutations, including those filtered out by {@code _permutationFilter}.
   */
  private long _numberOfVisitedPermutations = 0;

  /**
   * Keeps number of returned to the user permutations as not all visited permutations are considered to be worthy
   * for evaluation due to a {@code _permutationFilter}. 
   */
  private long _numberOfUsedPermutations = 0;
  private final long _maxHyperSpaceSize;

  public FilteredHyperSpaceIterator(HyperSpaceWalker.HyperSpaceIterator<MP> hyperSpaceIterator, PermutationFilter<MP> permutationFilter, long maxHyperSpaceSize) {
    _hyperSpaceIterator = hyperSpaceIterator;
    _permutationFilter = permutationFilter;
    _maxHyperSpaceSize = maxHyperSpaceSize;
  }

  @Override
  public MP nextModelParameters(Model previousModel) {
    MP nextMPs = _hyperSpaceIterator.nextModelParameters(previousModel);
    _numberOfVisitedPermutations++;
    if(_permutationFilter.permutationIsSkipped(nextMPs)) {
      return hasNext(previousModel) ? nextModelParameters(previousModel) : null;
    } else {
      _numberOfUsedPermutations++;
      return nextMPs;
    }
  }

  @Override
  public boolean hasNext(Model previousModel) {
    return _numberOfVisitedPermutations < _maxHyperSpaceSize && ( max_models() == 0 || _numberOfUsedPermutations < max_models());
  }

  @Override
  public void reset() {
    _hyperSpaceIterator.reset();
    _numberOfVisitedPermutations = 0;
    _numberOfUsedPermutations = 0;
    _permutationFilter.reset();
  }

  @Override
  public double max_runtime_secs() {
    return _hyperSpaceIterator.max_runtime_secs();
  }

  @Override
  public int max_models() {
    return _hyperSpaceIterator.max_models();
  }

  @Override
  public double time_remaining_secs() {
    return _hyperSpaceIterator.time_remaining_secs();
  }

  @Override
  public void modelFailed(Model failedModel) {
    _hyperSpaceIterator.modelFailed(failedModel);
    _numberOfVisitedPermutations--;
    _numberOfUsedPermutations--;
  }

  @Override
  public Object[] getCurrentRawParameters() {
    return _hyperSpaceIterator.getCurrentRawParameters();
  }
}
