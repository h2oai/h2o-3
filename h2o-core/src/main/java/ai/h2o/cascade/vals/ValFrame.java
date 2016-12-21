package ai.h2o.cascade.vals;

import ai.h2o.cascade.core.CFrame;
import water.fvec.Frame;

/**
 * Val wrapper around a {@link CFrame}.
 */
public class ValFrame extends Val {
  private CFrame frame;


  public ValFrame(Frame f) {
    frame = new CFrame(f);
  }

  public ValFrame(CFrame cf) {
    frame = cf;
  }

  @Override
  public Type type() {
    return Type.FRAME;
  }

  @Override
  public boolean maybeFrame() {
    return true;
  }

  @Override
  public CFrame getFrame() {
    return frame;
  }
}
