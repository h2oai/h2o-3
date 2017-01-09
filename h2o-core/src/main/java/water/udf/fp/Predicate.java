package water.udf.fp;

import java.util.LinkedList;
import java.util.List;

/**
 * Represents a predicate, that is, a function with Boolean codomain
 * Allows to filter data
 */
public abstract class Predicate<X> implements Function<X, Boolean> {

  public static Predicate<Object> NOT_NULL = new Predicate<Object>() {
    @Override public Boolean apply(Object x) { return x != null; }
  };

  /**
   * Filters a list, selecting only those values that satisfy the predicate.
   * Not lazy; the result is materialized right away.
   * @param xs source list
   * @param <Y> type of list values
   * @return filtered list
   */
  public <Y extends X> List<Y> filter(List<Y> xs) {
    List<Y> result = new LinkedList<>();
    for (Y x : xs) if (apply(x)) result.add(x);
    
    return result;
  }
}
