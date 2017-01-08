package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.special.Erf;

/**
 * The inverse error function.
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnInvErf extends FnUniOp {

  public double apply(double x) {
    return Erf.erfInv(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, INVERF);
  }


  private static InvErfSpec INVERF = new InvErfSpec();
  private static class InvErfSpec extends UniOpSpec {
    public InvErfSpec() {}
    @Override public String name() { return "invErf"; }
    @Override public double apply(double x) { return Erf.erfInv(x); }
  }
}
