package ai.h2o.cascade.vals;

import ai.h2o.cascade.core.WorkFrame;
import water.fvec.Frame;

/**
 * Val wrapper around a {@link WorkFrame}.
 */
public class ValFrame extends Val {
  private WorkFrame frame;


  public ValFrame(Frame f) {
    frame = new WorkFrame(f);
  }

  public ValFrame(WorkFrame cf) {
    frame = cf;
  }

  @Override
  public Type type() {
    return Type.FRAME;
  }

  @Override
  public boolean isFrame() {
    return true;
  }

  @Override
  public WorkFrame getFrame() {
    return frame;
  }
}
