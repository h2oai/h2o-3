package ai.h2o.cascade.core;

import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.parser.BufferedString;

import java.util.Arrays;
import java.util.List;


/**
 * This is a {@link GhostFrame} wrapped around a plain old {@link Frame}.
 */
public class CorporealFrame extends GhostFrame {
  private Frame frame;
  // Helper variables used during an MRTask execution over the frame.
  private int j0;
  private transient Chunk[] chunks;
  private transient BufferedString bs;


  public CorporealFrame(Frame f) {
    frame = f;
  }

  /**
   * Retrieve the {@link Frame} wrapped by this {@code CorporealFrame}. This
   * method is unsafe, as it allows access to underlying data without any
   * supervision from the Cascade runtime.
   */
  public Frame getWrappedFrame() {
    return frame;
  }


  //--------------------------------------------------------------------------------------------------------------------
  // GhostFrame interface implementation
  //--------------------------------------------------------------------------------------------------------------------

  @Override
  public int numCols() {
    return frame.numCols();
  }

  @Override
  public long numRows() {
    return frame.numRows();
  }

  @Override
  public byte type(int i) {
    return frame.vec(i).get_type();
  }

  @Override
  public String name(int i) {
    return frame.name(i);
  }


  @Override
  protected void prepareInputs(List<Vec> inputs) {
    j0 = inputs.size();
    inputs.addAll(Arrays.asList(frame.vecs()));
  }

  @Override
  protected void preparePerChunk(Chunk[] cs) {
    chunks = cs;
    bs = new BufferedString();
  }

  @Override
  public double getNumValue(int i, int j) {
    return chunks[j + j0].atd(i);
  }

  @Override
  public BufferedString getStrValue(int i, int j) {
    return chunks[j + j0].atStr(bs, i);
  }

  /**
   * Materializing a {@code CorporealFrame} is a noop -- the frame is already
   * "material". Therefore, this method just returns the object itself (not
   * even a copy).
   */
  @Override
  public CorporealFrame materialize() {
    return this;
  }

}
