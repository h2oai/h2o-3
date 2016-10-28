package water.udf;

import water.fvec.Chunk;

/**
 * Wrapper of a chunk that knows its type.
 */
public abstract class TypedChunk<T> {
  private Chunk c;

  public TypedChunk(Chunk c) {
    this.c = c;
  }

  boolean isNA(int i) {
    return c.isNA(i);
  }

  abstract T get(int idx);

  abstract void set(int idx, T value);
}
