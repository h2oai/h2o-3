package water.udf.fp;

import java.util.*;

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

  public static <X> Function<X, X> identity() {
    return new Function<X, X>() {
      @Override public X apply(X x) { return x; }
    };
  }

  public static <X> Function<Long, X> onList(final List<X> list) {
    return new Function<Long, X>() {
      public X apply(Long i) { return list.get(i.intValue()); }
    };
  }

  public static <X, Y> Iterable<Y> map(Iterable<X> xs, Function<X, Y> f) {
    List<Y> ys = new LinkedList<>();
    for (X x : xs) ys.add(f.apply(x));

    return ys;
  }

  public static <X,Y> Function<X,Y> constant(final Y y) {
    return new Function<X, Y>() {
      public Y apply(X x) { return y; }
    };
  }

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

  static class OneHotEncoder implements Unfoldable<Integer, Integer> {
    private String[] domain;
    private int hashCode;

    OneHotEncoder(String[] domain) {
      this.domain = domain;
      hashCode = 911 + Arrays.hashCode(domain);
    }

    @Override
    public int hashCode() {
      return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
      return (obj instanceof OneHotEncoder) &&
          Arrays.equals(domain, ((OneHotEncoder)obj).domain);
    }

    @Override
    public List<Integer> apply(Integer cat) {
      List<Integer> bits = new ArrayList<>(domain.length+1);

      for (int i = 0; i < domain.length; i++) bits.add(cat != null && cat == i ? 1 : 0);
      bits.add(cat == null ? 1 : 0);
      return bits;
    }
  }

  public static <X> Function<Integer, X> fromArray(final X[] array) {
    return new Function<Integer, X>() {

      @Override
      public X apply(Integer i) {
        return array[i];
      }
    };
  }

  public static Function<Integer, Integer> fromArray(final int[] array) {
    return new Function<Integer, Integer>() {

      @Override
      public Integer apply(Integer i) {
        return array[i];
      }
    };
  }

  public static <X> Function<Integer, X> fromCollection(final List<X> xs) {
    return new Function<Integer, X>() {

      @Override
      public X apply(Integer i) {
        return xs.get(i);
      }
    };
  }

  public static Function<Integer, String> format(final String format) {
    return new Function<Integer, String>() {

      @Override
      public String apply(Integer i) {
        return String.format(format, i);
      }
    };
  }
  
  public static Unfoldable<Integer, Integer> oneHotEncode(String[] domain) {
    return new OneHotEncoder(domain);
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
