package water.util.fp;

import java.util.LinkedList;
import java.util.List;

/**
 * Represents a single-argument function
 */
public abstract class Predicate<X> implements Function<X, Boolean> {

  public static Predicate<Object> NOT_NULL = new Predicate<Object>() {
    @Override public Boolean apply(Object x) { return x != null; }
  };

  public <Y extends X> List<Y> filter(List<Y> xs) {
    List<Y> result = new LinkedList<>();
    for (Y x : xs) if (apply(x)) result.add(x);
    
    return result;
  }
}
