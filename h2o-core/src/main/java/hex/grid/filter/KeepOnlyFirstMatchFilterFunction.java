package hex.grid.filter;

import java.util.Map;
import java.util.function.Function;

/**
 * This is a wrapper class for filtering function to make it possible to keep state.
 * This class is intended to be used to reduce number of grid items grid search process is going to evaluate.
 * Use this class to mark group of grid items that are equivalent in terms of their evaluation values and to only evaluate a single representative.
 * 
 *  Usage: pass the function to the constructor which will return `true` for the grid items of type <Map<String, Object>
 *  that we will want to consider equal.
 */
public class KeepOnlyFirstMatchFilterFunction implements Function<Map<String, Object>, Boolean>{

  // Function that should return `true` for grid items that we want to evaluate only one representative from.
  public final Function<Map<String, Object>, Boolean> _baseMatchFunction;
  private int _maxNumberOfMatchesToApply = 1;
  
  public KeepOnlyFirstMatchFilterFunction(Function<Map<String, Object>, Boolean> fun) {
    _baseMatchFunction = fun;
  }

  @Override
  public Boolean apply(Map<String, Object> stringObjectMap) {
    if(_maxNumberOfMatchesToApply == 0) {
      return  !_baseMatchFunction.apply(stringObjectMap);
    } else {
      return true;
    }
  }
  
  public void decrementCounter() {
    if (_maxNumberOfMatchesToApply > 0) _maxNumberOfMatchesToApply--;
  }
  
  public void reset() {
    _maxNumberOfMatchesToApply = 1;
  }

  @Override
  public <V> Function<V, Boolean> compose(Function<? super V, ? extends Map<String, Object>> before) {
    return _baseMatchFunction.compose(before);
  }

  @Override
  public <V> Function<Map<String, Object>, V> andThen(Function<? super Boolean, ? extends V> after) {
    return _baseMatchFunction.andThen(after);
  }
}
