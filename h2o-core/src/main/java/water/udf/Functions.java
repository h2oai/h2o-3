package water.udf;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Operations on functions
 */
public class Functions {

  public static <X,Y,Z> Function<X, Z> compose(final Function<X,Y> f, final Function<Y, Z> g) {
    return new Function<X, Z>() {
      @Override public Z apply(X x) { return g.apply(f.apply(x)); }
    };
  }
  
  public static final Function<Double, Double> SQUARE = new Function<Double, Double>() {
    @Override public Double apply(Double x) { return x*x; }
  };

  public static final Function2<Double, Double, Double> PLUS = new Function2<Double, Double, Double>() {
    @Override public Double apply(Double x, Double y) { return x+y; }
  };

  public static final Function2<Double, Double, Double> PROD = new Function2<Double, Double, Double>() {
    @Override public Double apply(Double x, Double y) { return x*y; }
  };

  public static final Function2<Double, Double, Double> X2_PLUS_Y2 = new Function2<Double, Double, Double>() {
    @Override public Double apply(Double x, Double y) { return x*x + y*y; }
  };

  public static final Function3<Double, Double, Double, Double> X2_PLUS_Y2_PLUS_Z2 = new Function3<Double, Double, Double, Double>() {
    @Override public Double apply(Double x, Double y, Double z) { return x*x + y*y + z*z; }
  };
  
  public static final Foldable<Double, Double> SUM = new Foldable<Double, Double>() {
    @Override public Double initial() { return 0.; }
    @Override public Double apply(Double sum, Double x) {
      return sum == null || x == null ? null : sum+x;
    }
  };

  public static final Foldable<Double, Double> SUM_OF_SQUARES = new Foldable<Double, Double>() {
    @Override public Double initial() { return 0.; }

    @Override public Double apply(Double sum, Double x) {
      return sum == null || x == null ? null : sum+x*x;
    }
  };

  public static final Foldable<Double, Double> PRODUCT = new Foldable<Double, Double>() {
    @Override public Double initial() { return 1.; }

    @Override public Double apply(Double sum, Double x) {
      return sum == null || x == null ? null : sum*x;
    }
  };
  
  public static Unfoldable<String, String> splitBy(final String separator) {
    return new Unfoldable<String, String>() {

      @Override public List<String> apply(String s) {
        return Arrays.asList(s.split(separator));
      }
    };
  }

  public static <T> Function<Long, T> onList(final List<T> list) {
    return new Function<Long, T>() {
      public T apply(Long i) { return list.get(i.intValue()); }
    };
  }
  
  public static <X, Y> Iterable<Y> map(Iterable<X> xs, Function<X, Y> f) {
    List<Y> ys = new LinkedList<>();
    for (X x : xs) ys.add(f.apply(x));

    return ys;
  }

  public static <T> Function<Long,T> constant(final T x) {
    return new Function<Long, T>() {
      public T apply(Long i) { return x; }
    };
  }
}
