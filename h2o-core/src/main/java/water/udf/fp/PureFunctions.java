package water.udf.fp;

import org.joda.time.DateTime;
import water.parser.ParseTime;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

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



  public static final Function<String, Date> AS_DATE = new Function<String, Date>() {
    @Override public Date apply(String s) {
      return ParseTime.parseDate(s);
    }
  };

  public static final Function2<Date, Date, Double> YEARS_BETWEEN = new Function2<Date, Date, Double>() {
    @Override
    public Double apply(Date from, Date to) {
      return (to.getTime() - from.getTime()) / 1000.0 / 3600 / 24 / 365.25;
    }
  };

  public static final Function2<Date, Date, Double> MONTHS_BETWEEN = new Function2<Date, Date, Double>() {
    @Override
    public Double apply(Date from, Date to) {
      return (to.getTime() - from.getTime()) / 1000.0 / 3600 / 24 / 365.25 * 12;
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

  abstract static class Function<X,Y> extends JustCode implements water.udf.fp.Function<X, Y> {}

  abstract static class Function2<X,Y,Z> extends JustCode implements water.udf.fp.Function2<X, Y, Z> {}

  abstract static class Function3<X,Y,Z,T> extends JustCode implements water.udf.fp.Function3<X, Y, Z, T> {}

  abstract static class Foldable<X, Y> extends JustCode implements water.udf.fp.Foldable<X, Y> {
  }

  abstract static class Unfoldable<X, Y> extends JustCode implements water.udf.fp.Unfoldable<X, Y> {
  }
}
