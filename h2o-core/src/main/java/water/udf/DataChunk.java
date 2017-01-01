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

  @Override public Chunk rawChunk() { return c; }

  protected static int index4(long i) { return Integer.MAX_VALUE & (int) i; }

  static long positionOf( int cidx, long i) {
    return ((long)cidx << Integer.SIZE) | index4(i);
  }

  static long positionOf(long index, int cidx, long start) {
    return positionOf(cidx, index - start);
  }

  @Override public boolean isNA(long i) { return c.isNA(index4(i)); }
  public void setNA(int i) { c.setNA(index4(i)); }

  @Override public long start() { return c.start(); }
  @Override public int length() { return c.len(); }

  public abstract void set(long i, T value);
  
  @Override public int cidx() { return c.cidx(); }
  
  @Override public Vec vec() { return c.vec(); }
}
