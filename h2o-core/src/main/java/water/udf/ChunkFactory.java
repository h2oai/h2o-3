package water.udf;

import water.fvec.Chunk;

import java.io.Serializable;

/**
 * This factory creates a TypedChunk; there's a variety of data sources,
 * can be a materialized Chunk, or a function.
 */
public interface ChunkFactory<ChunkType> extends Function<Chunk, ChunkType> {
  byte typeCode();
}
