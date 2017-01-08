package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * {@code log(1 + x)}
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnLog1p extends FnUniOp {

  public double apply(double x) {
    return FastMath.log1p(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, LOG1P);
  }


  private static Log1pSpec LOG1P = new Log1pSpec();
  private static class Log1pSpec extends UniOpSpec {
    public Log1pSpec() {}
    @Override public String name() { return "log1p"; }
    @Override public double apply(double x) { return FastMath.log1p(x); }
  }
}
