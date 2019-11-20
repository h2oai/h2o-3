package hex.grid.filter;

import hex.Model;

import java.util.function.Function;

public class StrictFilterFunction<MP extends Model.Parameters> implements PermutationFilterFunction<MP> {

  public final Function<MP, Boolean> _baseMatchFunction;
  
  public StrictFilterFunction(Function<MP, Boolean> fun) {
    _baseMatchFunction = fun;
  }

  @Override
  public Boolean apply(MP permutation) {
    return test(permutation);
  }

  @Override
  public boolean test(MP permutation) {
    return _baseMatchFunction.apply(permutation);
  }

  /**
   *  {@link StrictFilterFunction} is considered to be a stateless function, 
   *  which could be seen as activated as well - so no need to do anything.
   */
  public void activate() {}
  
  public void reset() { }
}
