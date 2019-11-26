package hex.grid.filter;

import hex.Model;

import java.util.Arrays;
import java.util.List;

public class DefaultPermutationFilter<MP extends Model.Parameters> implements PermutationFilter<MP> {

  private List<FilterFunction<MP>> _filterFunctions;

  @SafeVarargs
  public DefaultPermutationFilter(FilterFunction<MP>... filterFunctions) {
    _filterFunctions = Arrays.asList(filterFunctions);
  }
  
  @Override
  public boolean skip(MP permutation) {
    boolean skipPermutation = false;

    for (FilterFunction<MP> fun : _filterFunctions) {
      if (!fun.apply(permutation)) {
        skipPermutation = true;
        break;
      }
    }

    if (!skipPermutation) {
      _filterFunctions.stream()
              .forEach(ff -> ff.activate(permutation));
    }
    return skipPermutation;
  }

  @Override
  public void reset() {
    _filterFunctions.forEach(Resetable::reset);
  }
}
