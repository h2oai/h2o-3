package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import ai.h2o.cascade.core.GhostFrame1;
import ai.h2o.cascade.stdlib.StdlibFunction;
import water.Iced;
import water.fvec.Chunk;
import water.fvec.Vec;

import java.util.List;

/**
 *
 */
public abstract class FnUniOp extends StdlibFunction {


  public abstract static class UniOpSpec extends Iced<UniOpSpec> {
    public abstract String name();
    public abstract double apply(double value);
  }


  /**
   * Frame, which is a result of a simple transformation of another (parent)
   * frame containing only numeric columns.
   */
  protected static class NumericUniOpFrame extends GhostFrame1 {
    private UniOpSpec func;
    private transient int ncols;

    public NumericUniOpFrame() {}  // For serialization

    public NumericUniOpFrame(GhostFrame frame, UniOpSpec function) {
      super(frame);
      ncols = frame.numCols();
      func = function;
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
      return func.name() + "(" + parent.name(i) + ")";
    }

    @Override
    public double getNumValue(int i, int j) {
      return func.apply(parent.getNumValue(i, j));
    }
  }


  protected static class NanFrame extends GhostFrame1 {
    private transient String funcName;
    private transient int ncols;

    public NanFrame() {}
    public NanFrame(GhostFrame parent, String functionName) {
      super(parent);
      funcName = functionName;
      ncols = parent.numCols();
    }

    @Override public int numCols() { return ncols; }
    @Override public byte type(int i) { return Vec.T_NUM; }
    @Override public String name(int i) {
      return funcName + "(" + parent.name(i) + ")";
    }

    @Override protected void prepareInputs(List<Vec> inputs) {}
    @Override protected void preparePerChunk(Chunk[] cs) {}
    @Override public double getNumValue(int i, int j) {
      return Double.NaN;
    }
  }

}
