package hex.grid.filter;

import hex.Model;
import java.util.function.Function;

/**
 * This is a wrapper class for filtering function to make it possible to keep state.
 * This class is intended to be used to reduce number of grid items grid search process is going to evaluate.
 * Use this class to mark group of grid items that are equivalent in terms of their evaluation values and to only evaluate a single representative.
 * 
 *  Usage: pass the function to the constructor which will return `true` for the grid items of type <Map<String, Object>
 *  that we will want to consider equal.
 */
public class KeepOnlyFirstMatchFilterFunction<MP extends Model.Parameters> implements PermutationFilterFunction<MP> {

  // Function that should return `true` for permutations that we want to evaluate only one representative from.
  public final Function<MP, Boolean> _baseMatchFunction;
  private int _maxNumberOfMatchesToApply = 1;
  
  public KeepOnlyFirstMatchFilterFunction(Function<MP, Boolean> fun) {
    _baseMatchFunction = fun;
  }

  @Override
  public Boolean apply(MP permutation) {
    if(_maxNumberOfMatchesToApply == 0) {
      return  !test(permutation);
    } else {
      return true;
    }
  }

  @Override
  public boolean test(MP permutation) {
    return _baseMatchFunction.apply(permutation);
  }

  @Override
  public void activate() {
    if (_maxNumberOfMatchesToApply > 0) _maxNumberOfMatchesToApply--;
  }

  @Override
  public void reset() {
    _maxNumberOfMatchesToApply = 1;
  }
}
