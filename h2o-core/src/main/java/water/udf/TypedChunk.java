package water.udf;

import water.fvec.Chunk;

/**
 * Represents a chunk that knows its type
 */
public interface TypedChunk<T> {
  T get(int idx);
}
