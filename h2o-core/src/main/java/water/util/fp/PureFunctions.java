package water.util.fp;

/**
 * Stores stock pure functions, that is those that don't keep any context.
 * Pure functions have this feature that their equals() only compares classes.
 */
public class PureFunctions extends Functions {
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

  public static <X,Y,Z> Function<X, Z> compose(final Function<X,Y> f, final Function<Y, Z> g) {
    return new Function<X, Z>() {
      @Override public Z apply(X x) { return g.apply(f.apply(x)); }
    };
  }

  abstract static class Function<X,Y> extends JustCode implements water.util.fp.Function<X, Y> {}

  abstract static class Function2<X,Y,Z> extends JustCode implements water.util.fp.Function2<X, Y, Z> {}

  abstract static class Function3<X,Y,Z,T> extends JustCode implements water.util.fp.Function3<X, Y, Z, T> {}

  abstract static class Foldable<X, Y> extends JustCode implements water.util.fp.Foldable<X, Y> {
  }

  abstract static class Unfoldable<X, Y> extends JustCode implements water.util.fp.Unfoldable<X, Y> {
  }
}
