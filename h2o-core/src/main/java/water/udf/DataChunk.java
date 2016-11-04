package water.udf;

import water.fvec.Chunk;

/**
 * Wrapper of a chunk that knows its type, with mutability
 */
public abstract class DataChunk<T> implements TypedChunk<T> {
  protected Chunk c;

  boolean isNA(int i) {
    return c.isNA(i);
  }

  public DataChunk(Chunk c) { this.c = c; }

  abstract void set(int idx, T value);
}
