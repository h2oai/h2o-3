package ai.h2o.cascade.core;

import water.fvec.Chunk;
import water.fvec.Vec;

import java.util.List;

/**
 * Specialization of a {@link GhostFrame} for a frame that depends on a
 * single parent frame.
 */
public abstract class GhostFrame1 extends GhostFrame {
  protected GhostFrame parent;
  protected long nrows;

  public GhostFrame1(GhostFrame p) {
    parent = p;
    nrows = p.numRows();
  }

  public long numRows() {
    return nrows;
  }

  protected void prepareInputs(List<Vec> inputs) {
    parent.prepareInputs(inputs);
  }

  protected void preparePerChunk(Chunk[] cs) {
    parent.preparePerChunk(cs);
  }

}
