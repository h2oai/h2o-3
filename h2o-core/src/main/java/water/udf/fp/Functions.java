package water.udf.fp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

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
      return equal(f, other.f) && equal(g, other.g);
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
      return equal(separator, other.separator);
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
      List<Integer> bits = new ArrayList<>(domain.length);

      for (int i = 0; i < domain.length; i++) bits.add(i == cat ? 1 : 0);
      
      return bits;
    }
  }

  public static Unfoldable<Integer, Integer> oneHotEncode(String[] domain) {
    return new OneHotEncoder(domain);
  }
  
  public static int hashCode(Object x) {
    return x == null ? 0 : x.hashCode();
  }

  public static boolean equal(Object x, Object y) {
    return x == null ? y == null : x.equals(y);
  }
}
