package hex.grid.filter;

import java.util.Map;
import java.util.function.Function;

// by returning false we mark items to be skipped
public class KeepOnlyFirstMatchFilterFunction implements Function<Map<String, Object>, Boolean>{

  public final Function<Map<String, Object>, Boolean> _fun;
  private int _maxNumberOfApplications = 1;
  
  // Function that should return true for grid items that we want to keep only first occurrence
  public KeepOnlyFirstMatchFilterFunction(Function<Map<String, Object>, Boolean> fun) {
    _fun = fun;
  }

  @Override
  public Boolean apply(Map<String, Object> stringObjectMap) {
    Boolean applyResult = _fun.apply(stringObjectMap);
    return applyResult ? !(_maxNumberOfApplications == 0) : true;
  }
  
  public void decrementCounter() {
    if(_maxNumberOfApplications > 0)_maxNumberOfApplications--;
  }

  @Override
  public <V> Function<V, Boolean> compose(Function<? super V, ? extends Map<String, Object>> before) {
    return _fun.compose(before);
  }

  @Override
  public <V> Function<Map<String, Object>, V> andThen(Function<? super Boolean, ? extends V> after) {
    return _fun.andThen(after);
  }
}
