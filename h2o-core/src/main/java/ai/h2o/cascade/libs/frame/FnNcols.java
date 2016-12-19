package ai.h2o.cascade.libs.frame;

import ai.h2o.cascade.core.Function;
import ai.h2o.cascade.vals.Val;
import ai.h2o.cascade.vals.ValNum;
import water.fvec.Frame;

/**
 * Number of vecs (columns) in a frame.
 */
public class FnNcols extends Function {

  public int apply(Frame frame) {
    return frame.numCols();
  }

  // TODO: autogenerate
  @Override public Val apply(Val[] args) {
    if (args.length != 1)
      throw new IllegalArgumentException("Expected 1 argument but obtained " + args.length);
    int res = apply(args[0].getFrame());
    return new ValNum(res);
  }

}
