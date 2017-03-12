package water.udf;

import water.fvec.Vec;
import water.udf.fp.Function;

import java.io.IOException;
import java.util.List;

/**
 * General-case factory for columns
 */
public interface ColumnFactory<DataType, ColumnType> extends ChunkFactory<DataType> {
  
  ColumnType newColumn(Vec vec);

  ColumnType newColumn(long length, final Function<Long, DataType> f) throws IOException;

  ColumnType newColumn(final List<DataType> xs) throws IOException;

  ColumnType materialize(Column<DataType> xs) throws IOException;

  Vec buildZeroVec(long length);
}
