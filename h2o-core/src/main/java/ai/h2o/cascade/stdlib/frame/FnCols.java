package ai.h2o.cascade.stdlib.frame;

import ai.h2o.cascade.core.*;
import ai.h2o.cascade.stdlib.StdlibFunction;
import water.fvec.Frame;
import water.fvec.Vec;

/**
 * Extract multiple columns out of a frame.
 */
public class FnCols extends StdlibFunction {

  public GhostFrame apply(GhostFrame frame, SliceList columns) {
    try {
      return new SliceFrame(frame, columns);
    } catch (IllegalArgumentException e) {
      throw new ValueError(1, e.getMessage());
    }
  }

  public GhostFrame apply(GhostFrame frame, String[] columns) {
    SliceList sl = new SliceList(columns, frame);
    return new SliceFrame(frame, sl);
  }


  /**
   * Frame representing a slice (i.e. a subset of columns) from the original
   * frame. If the slice is applied directly to a {@link CorporealFrame},
   * then materialization is optimized to reuse the vecs from the original
   * frame rather than copying them.
   */
  public static class SliceFrame extends GhostFrame1 {
    private int[] index;

    public SliceFrame(GhostFrame source, SliceList indices) {
      super(source);
      int sourceNcols = source.numCols();
      index = indices.normalizeR(sourceNcols).expand4();
    }

    //------------------------------------------------------------------------------------------------------------------
    // GhostFrame interface implementation
    //------------------------------------------------------------------------------------------------------------------

    @Override
    public int numCols() {
      return index.length;
    }

    @Override
    public byte type(int i) {
      return parent.type(index[i]);
    }

    @Override
    public String name(int i) {
      return parent.name(index[i]);
    }

    @Override
    public CorporealFrame materialize(Scope scope) {
      if (parent instanceof CorporealFrame) {
        Frame f = ((CorporealFrame) parent).getWrappedFrame();
        Vec[] vecsSlice = new Vec[index.length];
        String[] namesSlice = new String[index.length];
        for (int i = 0; i < index.length; i++) {
          vecsSlice[i] = f.vec(index[i]);
          namesSlice[i] = f.name(index[i]);
        }
        Frame r = new Frame(scope.<Frame>mintKey(), namesSlice, vecsSlice);
        return new CorporealFrame(r);
      }
      return super.materialize(scope);
    }
  }

}
