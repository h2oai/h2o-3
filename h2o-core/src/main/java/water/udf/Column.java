package water.udf;

import water.fvec.Chunk;
import water.fvec.Vec;

/**
 * Generic typed data column
 */
public interface Column<T> {
  T get(long idx);
  TypedChunk<T> chunkAt(int i);
  
  boolean isNA(long idx);
  
  String getString(long idx);
  
  Vec vec();
  int rowLayout();
}
