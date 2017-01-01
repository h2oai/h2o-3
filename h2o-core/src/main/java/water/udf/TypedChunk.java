package water.udf;

import water.fvec.Chunk;
import water.fvec.Vec;

/**
 * Represents a chunk that knows its type
 */
public interface TypedChunk<T> extends Vec.Holder {
  T get(long i);
  boolean isNA(long i);
  int length();
  long start();
}
