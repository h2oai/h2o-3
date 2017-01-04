package ai.h2o.cascade.core;

import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.parser.BufferedString;

import java.util.Arrays;
import java.util.List;

/**
 * This is a {@link GhostFrame} wrapped around a plain {@link Frame}.
 */
public class CorporealFrame extends GhostFrame {
  private Frame frame;
  private int inputsOffset;
  private transient Chunk[] chunks;
  private transient BufferedString bs;

  public CorporealFrame(Frame f) {
    frame = f;
  }

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
    inputsOffset = inputs.size();
    inputs.addAll(Arrays.asList(frame.vecs()));
  }

  @Override
  protected void preparePerChunk(Chunk[] cs) {
    chunks = cs;
    bs = new BufferedString();
  }

  @Override
  public double getNumValue(int i, int j) {
    return chunks[j + inputsOffset].atd(i);
  }

  @Override
  public BufferedString getStrValue(int i, int j) {
    return chunks[j + inputsOffset].atStr(bs, i);
  }


  public Frame getWrappedFrame() {
    return frame;
  }
}
