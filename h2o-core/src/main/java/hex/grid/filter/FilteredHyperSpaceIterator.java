package hex.grid.filter;

import hex.Model;
import hex.grid.HyperSpaceWalker;

public class FilteredHyperSpaceIterator<MP extends Model.Parameters> implements HyperSpaceWalker.HyperSpaceIterator<MP> {
  private final HyperSpaceWalker.HyperSpaceIterator<MP> _hyperSpaceIterator;

  private final PermutationFilter<MP> _permutationFilter;

  public FilteredHyperSpaceIterator(HyperSpaceWalker.HyperSpaceIterator<MP> hyperSpaceIterator, PermutationFilter<MP> permutationFilter) {
    _hyperSpaceIterator = hyperSpaceIterator;
    _permutationFilter = permutationFilter;
  }

  @Override
  public MP nextModelParameters(Model previousModel) {
    MP permutation = _hyperSpaceIterator.nextModelParameters(previousModel);

    while (permutation != null && _permutationFilter.skip(permutation)) {
      permutation = hasNext(previousModel) ? _hyperSpaceIterator.nextModelParameters(previousModel) : null;
    }
    return permutation;
  }

  @Override
  public boolean hasNext(Model previousModel) {
    return _hyperSpaceIterator.hasNext(previousModel);
  }

  @Override
  public void reset() {
    _hyperSpaceIterator.reset();
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
  }

  @Override
  public Object[] getCurrentRawParameters() {
    return _hyperSpaceIterator.getCurrentRawParameters();
  }
}
