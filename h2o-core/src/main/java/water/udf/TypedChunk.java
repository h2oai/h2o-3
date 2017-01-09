package water.udf;

import water.fvec.Chunk;
import water.fvec.Vec;

/**
 * Represents a chunk that knows its type
 */
public interface TypedChunk<T> extends Vec.Holder {
  T get(int i);
  boolean isNA(int i);
  int length();
  long start();
  Chunk rawChunk();

  int cidx();
}
