package water.udf;

import water.fvec.Chunk;
import water.fvec.Vec;

/**
 * Represents a chunk that knows its type
 */
public interface TypedChunk<T> {
  T get(int i);
  boolean isNA(int i);
  int len();
  long start();
  Chunk rawChunk();

  int cidx();

  Vec vec();
}
