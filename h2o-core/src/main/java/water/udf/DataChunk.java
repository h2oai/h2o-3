package water.udf;

import water.fvec.Chunk;
import water.fvec.Vec;

/**
 * Wrapper of a chunk that knows its type, with mutability
 */
public abstract class DataChunk<T> implements TypedChunk<T> {
  protected Chunk c;

  /**
   * Deserializaiton only
   */
  public DataChunk() {}
  
  public DataChunk(Chunk c) { this.c = c; }

  protected static int indexOf(long position) { return Integer.MAX_VALUE & (int) position; }

  static long positionOf(int cidx, long i) {
    return ((long)cidx << Integer.SIZE) | indexOf(i);
  }

  static long positionOf(long index, int cidx, long start) {
    return positionOf(cidx, index - start);
  }

  @Override public boolean isNA(long i) { return c.isNA(indexOf(i)); }
  public void setNA(int i) { c.setNA(indexOf(i)); }

  @Override public long start() { return c.start(); }
  @Override public int length() { return c.len(); }

  public void set(long position, T value) {
    int i = indexOf(position);
    if (value == null) c.setNA(i); else setValue(i, value);
  }

  protected abstract void setValue(int at, T value);
  
  @Override public Vec vec() { return c.vec(); }
}
