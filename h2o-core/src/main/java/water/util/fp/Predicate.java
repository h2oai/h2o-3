package water.util.fp;

import water.util.Java7;

import static water.util.Java7.*;
import static water.util.fp.FP.*;
import java.util.*;

/**
 * Represents a single-argument function
 */
public abstract class Predicate<X> {
  abstract public Boolean apply(X x);
  
  public static Predicate<Object> NOT_NULL = new Predicate<Object>() {
    //@Override 
    public Boolean apply(Object x) { return x != null; }
  };

  private <Y extends X, C extends Collection<Y>> C addAll(Collection<Y> from, C to) {
    for (Y x : from) if (apply(x)) to.add(x);

    return to;
  }

  public Option<X> find(Iterable<X> xs) {
    for (X x : xs) if (apply(x)) return Some(x);

    return none();
  }
  
  public <Y extends X> List<Y> filter(List<Y> xs) {
    List<Y> result = new LinkedList<>();
    return addAll(xs, result);
  }
  
  public <Y extends X> TreeSet<Y> filter(TreeSet<Y> xs) {
    TreeSet<Y> result = new TreeSet<>();
    return addAll(xs, result);
  }

  public <Y extends X> HashSet<Y> filter(Set<Y> xs) {
    HashSet<Y> result = new HashSet<>();
    return addAll(xs, result);
  }
  
  public Predicate<X> not() {
    return new Predicate<X>() {
      //@Override 
      public Boolean apply(X x) { return !Predicate.this.apply(x); }
    };
  }
  
  public static <X> Predicate<X> equal(final X x0) {
    return new Predicate<X>() {
      //@Override 
      public Boolean apply(X x) { return Java7.Objects.equals(x0, x); }
    };
  }
}
