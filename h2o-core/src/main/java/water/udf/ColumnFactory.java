package water.udf;

import water.fvec.Vec;
import water.util.fp.Function;

import java.io.IOException;
import java.util.List;

/**
 * General-case factory for columns
 */
public interface ColumnFactory<T> extends ChunkFactory<T> {
  
  DataColumn<T> newColumn(Vec vec);

  DataColumn<T> newColumn(long length, final Function<Long, T> f) throws IOException;

  DataColumn<T> newColumn(final List<T> xs) throws IOException;

  DataColumn<T> materialize(Column<T> xs) throws IOException;

  Vec buildZeroVec(long length);
}
