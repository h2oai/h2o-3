package water.udf.fp;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Operations on functions
 */
public class Functions {

  /**
   * A function that is a composition of two other functions
   * @param <X> domain of the first function
   * @param <Y> domain of the second function
   * @param <Z> codomain of the second function
   */
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

  /**
   * Given two functions, produce another that is their composition
   * @param g second function to apply
   * @param f first function to apply
   * @param <X> domain of <code>f</code>
   * @param <Y> codomain of <code>f</code> and domain of <code>g</code>
   * @param <Z> codomain of <code>g</code>
   * @return composition of f and g
   */
  public static <X,Y,Z> Function<X, Z> compose(final Function<Y, Z> g, final Function<X,Y> f) {
    return new Composition<>(f, g);
  }

  /**
   * Identity function on type X
   * @param <X> domain (and codomain)
   * @return identity function
   */
  public static <X> Function<X, X> identity() {
    return new Function<X, X>() {
      @Override public X apply(X x) { return x; }
    };
  }

  /**
   * Representation of List as a function (on <code>Long</code>)
   * @param list the list we use for a function
   * @param <T> data type, also a codomain of the function
   * @return a function that takes a long and returns the list value on that long
   */
  public static <T> Function<Long, T> onList(final List<T> list) {
    return new Function<Long, T>() {
      public T apply(Long i) { return list.get(i.intValue()); }
    };
  }

  /**
   * Maps an iterable on a given function.
   * The operation is not lazy (TODO).
   * 
   * @param xs iterable to map
   * @param f function to apply
   * @param <X> iterable data type
   * @param <Y> function codomain type
   * @return an iterable consisting of the values f(x) for x in iterable
   */
  public static <X, Y> Iterable<Y> map(Iterable<X> xs, Function<X, Y> f) {
    List<Y> ys = new LinkedList<>();
    for (X x : xs) ys.add(f.apply(x));

    return ys;
  }

  /**
   * Constant function
   * @param y the value of the function
   * @param <X> domain type
   * @param <Y> codomain type
   * @return a function that returns the <code>y</code> for any <code>X x</code>
   */
  public static <X,Y> Function<X,Y> constant(final Y y) {
    return new Function<X, Y>() {
      public Y apply(X x) { return y; }
    };
  }

  /**
   * Splitter function, has a separator; given a string, returns a list
   * of substrings separated by the separator
   */
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

  /**
   * Builds a StringSplitter function, given a separator
   * @param separator the separator
   * @return splitter function
   */
  public static Unfoldable<String, String> splitBy(final String separator) {
    return new StringSplitter(separator);
  }

  public static int hashCode(Object x) {
    return x == null ? 0 : x.hashCode();
  }

  public static boolean equal(Object x, Object y) {
    return x == null ? y == null : x.equals(y);
  }
}
