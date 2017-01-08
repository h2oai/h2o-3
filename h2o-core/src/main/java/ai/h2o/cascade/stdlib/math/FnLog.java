package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Logarithm function
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnLog extends FnUniOp {

  /** Natural logarithm of x */
  public double apply(double x) {
    return FastMath.log(x);
  }

  /** Logarithm of x in the given base */
  public double apply(double x, double base) {
    return FastMath.log(base, x);
  }

  /** Natural logarithm applied to a frame */
  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, LN);
  }

  /** Logarithm in the given base, applied to a frame */
  public GhostFrame apply(GhostFrame frame, double base) {
    if (base <= 0)
      return new NanFrame(frame, "log");
    else
      return new NumericUniOpFrame(frame, new LogBSpec(base));
  }


  private static LnSpec LN = new LnSpec();
  private static class LnSpec extends UniOpSpec {
    public LnSpec() {}
    @Override public String name() { return "ln"; }
    @Override public double apply(double x) { return FastMath.log(x); }
  }


  public static class LogBSpec extends UniOpSpec {
    private double numerator;
    private transient double base;

    public LogBSpec() {}
    public LogBSpec(double base) {
      this.base = base;
      numerator = FastMath.log(base);
    }
    @Override public String name() { return ((int) base == base)? "log" + base : "log"; }
    @Override public double apply(double x) { return FastMath.log(x) / numerator; }
  }
}
