package ai.h2o.cascade.vals;

import water.fvec.Frame;

/**
 * Val wrapper around a {@link Frame}.
 */
public class ValFrame extends Val {
  private Frame frame;


  public ValFrame(Frame f) {
    frame = f;
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
  public Frame getFrame() {
    return frame;
  }
}
