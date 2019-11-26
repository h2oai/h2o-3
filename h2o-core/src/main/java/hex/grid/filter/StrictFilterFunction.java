package hex.grid.filter;

import hex.Model;

import java.util.function.Function;

public class StrictFilterFunction<MP extends Model.Parameters> implements PermutationFilterFunction<MP> {

  public final Function<MP, Boolean> _baseMatchFunction;
  
  public StrictFilterFunction(Function<MP, Boolean> fun) {
    _baseMatchFunction = fun;
  }

  @Override
  public boolean test(MP permutation) {
    return _baseMatchFunction.apply(permutation);
  }

}
