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

  /**
   *
   * @param permutation instance of {@link Model.Parameters} that represents particular
   *                   combination of hyper parameters during grid search
   * @return false if permutation is considered to be skipped by any of filter functions
   */
  @Override
  public boolean test(MP permutation) {
    boolean evaluatePermutation = true;

    for (FilterFunction<MP> fun : _filterFunctions) {
      if (!fun.test(permutation)) {
        evaluatePermutation = false;
        break;
      }
    }
    final boolean activateGlobalDecision = evaluatePermutation; // have to do this as lambda expression accept only effectively final variables

    _filterFunctions.forEach(ff -> ff.activate(activateGlobalDecision, permutation));

    return evaluatePermutation;
  }

  @Override
  public void reset() {
    _filterFunctions.forEach(Resetable::reset);
  }
}
