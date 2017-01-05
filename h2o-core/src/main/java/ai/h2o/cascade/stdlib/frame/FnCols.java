package ai.h2o.cascade.stdlib.frame;

import ai.h2o.cascade.core.GhostFrame;
import ai.h2o.cascade.core.GhostFrame1;
import ai.h2o.cascade.core.SliceList;
import ai.h2o.cascade.stdlib.StdlibFunction;

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
  }

}
