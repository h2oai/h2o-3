package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Signum function (return the sign of the argument)
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnSignum extends FnUniOp {

  public double apply(double x) {
    return FastMath.signum(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, SIGNUM);
  }


  private static SignumSpec SIGNUM = new SignumSpec();
  private static class SignumSpec extends UniOpSpec {
    public SignumSpec() {}
    @Override public String name() { return "signum"; }
    @Override public double apply(double x) { return FastMath.signum(x); }
  }
}
