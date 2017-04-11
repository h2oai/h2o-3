package water.util.fp;

import water.util.Pair;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static water.util.Java7.Objects;

/**
 * Operations on functions
 */
public class Functions {

  static class Composition<X,Y,Z> implements Function<X,Z> {
    private final Function<X, Y> f;
    private final Function<Y, Z> g;

    Composition(final Function<X,Y> f, final Function<Y, Z> g) {
      this.f = f;
      this.g = g;
    }

    @Override
    public int hashCode() {
      return f.hashCode() * 211 + g.hashCode() * 79;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof Composition)) return false;
      Composition other = (Composition) obj;
      return Objects.equals(f, other.f) && Objects.equals(g, other.g);
    }

    @Override public Z apply(X x) { return g.apply(f.apply(x)); }
  }
  
  public static <X,Y,Z> Function<X, Z> compose(final Function<Y, Z> g, final Function<X,Y> f) {
    return new Composition<>(f, g);
  }
  
  public static <Y, X extends Y> Function<X, Y> identity() {
    return new Function<X, Y>() {
      @Override public X apply(X x) { return x; }
    };
  }
  
  public static <X, Y> Function<X, Y> forReader(final Reader<Y> r) {
    return new Function<X, Y>() {
      @Override public Y apply(X x) { return r.read(); }
    };
  }

  public static <T> Function<Long, T> onList(final List<T> list) {
    return new Function<Long, T>() {
      public T apply(Long i) { return list.get(i.intValue()); }
    };
  }
  
  public static <X, Y> Function<X, Y> forMap(final Map<X, Y> map, final Function<X, Y> alt) {
    return new Function<X, Y>() {

      @Override public Y apply(X x) {
        Y y = map.get(x);
        return y != null ? y : alt.apply(x);
      }
    };
  }
  
  public static <X, Y> Iterable<Y> map(Iterable<X> xs, Function<X, Y> f) {
    List<Y> ys = new LinkedList<>();
    for (X x : xs) ys.add(f.apply(x));

    return ys;
  }

  public static <X> Reader<X> constantReader(final X x) {
    return new Reader<X>() {
      public X read() { return x; }
    };
  }

  public static <X,Y> Function<X,Y> constant(final Y y) {
    return new Function<X, Y>() {
      public Y apply(X x) { return y; }
    };
  }

  public static <X, Y> Function<Y, Pair<X, Y>> pair1(final X x) {
    return new Function<Y, Pair<X, Y>>() {

      @Override public Pair<X, Y> apply(Y y) {
        return new Pair<>(x, y);
      }
    };
  }

  public static <X, Y> Function<X, Pair<X, Y>> pair2(final Y y) {
    return new Function<X, Pair<X, Y>>() {
      @Override public Pair<X, Y> apply(X x) {
        return new Pair<>(x, y);
      }
    };
  }

  public static PartialFunction<String, Integer> parseInt =
      new PartialFunction<String, Integer>() {
        @Override
        public FP.Option<Integer> apply(String s) {
          try {
            return FP.Option(Integer.parseInt(s));
          } catch (Exception ignore) {
            return FP.none();
          }
        }
      };

  public static PartialFunction<String, Double> parseDouble =
      new PartialFunction<String, Double>() {
        @Override
        public FP.Option<Double> apply(String s) {
          try {
            return FP.Option(Double.parseDouble(s));
          } catch (Exception ignore) {
            return FP.none();
          }
        }
      };
  
  static class StringSplitter implements Unfoldable<String, String> {
    private final String separator;

    StringSplitter(String separator) {
      this.separator = separator;
    }
    @Override public List<String> apply(String s) {
      return Arrays.asList(s.split(separator));
    }

    @Override
    public int hashCode() {
      return 211 + separator.hashCode() * 7;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof StringSplitter)) return false;
      StringSplitter other = (StringSplitter) obj;
      return Objects.equals(separator, other.separator);
    }
  }
  
  public static Unfoldable<String, String> splitBy(final String separator) {
    return new StringSplitter(separator);
  }

  /**
   * Integrates "area under curve" (assuming it exists),
   * that is, for a parametric curve specified by functions x and y,
   * defined on integer domain [from, to], calculate the area
   * between x[from], x[to], horizontal axis, and the curve.
   * @param x x-component of the curve
   * @param y y-component of the curve
   * @param from min value of the curve range
   * @param to max value of the curve range
   * @return the area under curve, the result of integrating x*y' over [from,to].
   */
  public static double integrate(
      Function<Integer, Double> x,
      Function<Integer, Double> y,
      int from, int to) {
    double s = 0;
    double x0 = x.apply(from);
    double y0 = y.apply(from);
    for (int i = from + 1; i <= to; i++) {
      double x1 = x.apply(i);
      double y1 = y.apply(i);
      s += (y1+y0)*(x1-x0)*.5;

      x0 = x1; y0 = y1;
    }

    return s;
  }
}
