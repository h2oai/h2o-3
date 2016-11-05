package water.udf;

import water.fvec.Chunk;

/**
 * Mutable version of Column
 */
public interface MutableColumn<T> extends Column<T> {
  void set(long idx, T value);
  @Override T apply(long idx);
}
