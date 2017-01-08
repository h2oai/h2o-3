package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;


/**
 * Box-Cox transform.
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnBoxCox extends FnUniOp {

  public double apply(double x, double a) {
    return a == 0? FastMath.log(x) : (FastMath.pow(x, a) - 1) / a;
  }

  public GhostFrame apply(GhostFrame frame, double a) {
    if (a == 0)
      return new NumericUniOpFrame(frame, FnLog.LN);
    else
      return new NumericUniOpFrame(frame, new BoxCoxSpec(a));
  }


  private static class BoxCoxSpec extends UniOpSpec {
    private double a;
    public BoxCoxSpec() {}
    public BoxCoxSpec(double a) { this.a = a; }
    @Override public String name() { return "BoxCox"; }
    @Override public double apply(double x) { return (FastMath.pow(x, a) - 1) / a; }
  }
}
