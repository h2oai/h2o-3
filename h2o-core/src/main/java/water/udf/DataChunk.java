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
  
  @Override public boolean isNA(int i) { return c.isNA(i); }

  @Override public long start() { return c.start(); }
  @Override public int len() { return c.len(); }

  public abstract void set(int idx, T value);
  
  @Override public int cidx() { return c.cidx(); }
  
  @Override public Vec vec() { return c.vec(); }
}
