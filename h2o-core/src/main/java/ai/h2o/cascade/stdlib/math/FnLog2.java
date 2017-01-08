package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Base-2 logarithm function
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnLog2 extends FnUniOp {

  public double apply(double x) {
    return FastMath.log(x) / LG2;
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, LOG2);
  }


  private static final double LG2 = FastMath.log(2);
  private static final FnLog.LogBSpec LOG2 = new FnLog.LogBSpec(2);
}
