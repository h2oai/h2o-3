package water.udf;

import water.fvec.Chunk;
import water.fvec.Vec;

/**
 * Represents a chunk that knows its type.
 * Currently it's not used anywhere in samples, so its existence
 * is doubtful. May be removed later.
 */
public interface TypedChunk<T> extends Vec.Holder {
  T get(long i);
  boolean isNA(long i);
  int length();
  long start();
}
