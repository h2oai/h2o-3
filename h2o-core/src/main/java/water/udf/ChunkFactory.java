package water.udf;

import water.fvec.Chunk;
import water.util.fp.Function;

/**
 * This factory creates a TypedChunk; there's a variety of data sources,
 * can be a materialized Chunk, or a function.
 * Have to keep a byte with type code, since, well, it's H2O.
 */
public interface ChunkFactory<DataType> extends Function<Chunk, DataChunk<DataType>> {
  byte typeCode();
}
