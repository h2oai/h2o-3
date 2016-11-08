package water.udf;

import water.fvec.Chunk;
import water.fvec.Vec;

import java.io.Serializable;

/**
 * Generic typed data column
 */
public interface Column<T> extends Function<Long, T>, Vec.Holder {
  T apply(long idx);
  TypedChunk<T> chunkAt(int i);
  
  boolean isNA(long idx);
  
  String getString(long idx);
  
  Vec vec();
  int rowLayout();
  long size();

  boolean isCompatibleWith(Column<?> ys);
}
