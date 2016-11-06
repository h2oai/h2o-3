package water.udf;

import water.fvec.Chunk;

/**
 * This factory creates a TypedChunk; there's a variety of data sources,
 * can be a materialized Chunk, or a function.
 */
public interface ChunkFactory<DataType> extends Function<Chunk, DataChunk<DataType>> {
  byte typeCode();
}
