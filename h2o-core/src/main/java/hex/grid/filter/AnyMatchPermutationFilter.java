package hex.grid.filter;

import hex.Model;

import java.util.Arrays;
import java.util.List;

public class AnyMatchPermutationFilter<MP extends Model.Parameters> implements PermutationFilter<MP> {

  private List<FilterFunction<MP>> _filterFunctions;

  @SafeVarargs
  public AnyMatchPermutationFilter(FilterFunction<MP>... filterFunctions) {
    _filterFunctions = Arrays.asList(filterFunctions);
  }

  @Override
  public boolean skip(MP permutation) {
    boolean skipPermutation = false;

    for (FilterFunction<MP> fun : _filterFunctions) {
      if (!fun.test(permutation)) {
        skipPermutation = true;
        break;
      }
    }
    final boolean activateGlobalDecision = !skipPermutation; // have to do this as lambda expression accept only effectively final variables

    _filterFunctions.forEach(ff -> ff.activate(activateGlobalDecision, permutation));

    return skipPermutation;
  }

  @Override
  public void reset() {
    _filterFunctions.forEach(Resettable::reset);
  }
}
