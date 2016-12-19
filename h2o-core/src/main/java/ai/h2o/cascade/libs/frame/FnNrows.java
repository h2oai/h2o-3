package ai.h2o.cascade.libs.frame;

import ai.h2o.cascade.core.Function;
import ai.h2o.cascade.vals.Val;
import ai.h2o.cascade.vals.ValNum;
import water.fvec.Frame;

/**
 * Number of rows in a frame.
 */
public class FnNrows extends Function {

  public long apply(Frame frame) {
    return frame.numRows();
  }

  // TODO: autogenerate
  @Override public Val apply(Val[] args) {
    if (args.length != 1)
      throw new IllegalArgumentException("Expected 1 argument but obtained " + args.length);
    long res = apply(args[0].getFrame());
    return new ValNum(res);
  }

}
