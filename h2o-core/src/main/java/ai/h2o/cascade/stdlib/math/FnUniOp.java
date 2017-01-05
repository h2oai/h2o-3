package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import ai.h2o.cascade.core.GhostFrame1;
import ai.h2o.cascade.stdlib.StdlibFunction;
import water.fvec.Vec;

/**
 *
 */
public abstract class FnUniOp extends StdlibFunction {

  /** Override in subclasses which define functions double->double */
  public double apply(double value) {
    return Double.NaN;
  }


  protected class NumericUniOpFrame extends GhostFrame1 {
    private transient int ncols;
    private transient String opname;

    public NumericUniOpFrame() {}  // For serialization

    public NumericUniOpFrame(GhostFrame frame, String funcName) {
      super(frame);
      ncols = frame.numCols();
      opname = funcName;
      for (int i = 0; i < ncols; i++) {
        if (parent.type(i) != Vec.T_NUM)
          throw new ValueError(0, "Column " + i + " of the frame is not numeric");
      }
    }

    @Override
    public int numCols() {
      return ncols;
    }

    @Override
    public byte type(int i) {
      return Vec.T_NUM;
    }

    @Override
    public String name(int i) {
      return opname + "(" + parent.name(i) + ")";
    }

    @Override
    public double getNumValue(int i, int j) {
      return apply(parent.getNumValue(i, j));
    }
  }


}
