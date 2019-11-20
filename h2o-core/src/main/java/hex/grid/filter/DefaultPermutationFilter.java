package hex.grid.filter;

import hex.Model;

import java.util.List;

public class DefaultPermutationFilter<MP extends Model.Parameters> implements PermutationFilter<MP> {

  private List<PermutationFilterFunction<MP>> _filterFunctions;

  public DefaultPermutationFilter(List<PermutationFilterFunction<MP>> filterFunctions) {
    _filterFunctions = filterFunctions;
  }
  
  @Override
  public boolean permutationIsSkipped(MP permutation) {
    boolean skipPermutation = false;

    for (PermutationFilterFunction<MP> fun : _filterFunctions) {
      if (!fun.apply(permutation)) {
        skipPermutation = true;
        break;
      }
    }

    if (!skipPermutation) {
      _filterFunctions.stream()
              .filter(ff -> ff.test(permutation))
              .forEach(Activatable::activate);
    }
    return skipPermutation;
  }

  @Override
  public void reset() {
    _filterFunctions.forEach(Activatable::reset);
  }
}
