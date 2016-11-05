package water.udf;

import water.fvec.Chunk;

/**
 * Represents a chunk that knows its type
 */
public abstract class DependentChunk<T> implements TypedChunk<T> {
  private final TypedChunk<?> master;
  DependentChunk(TypedChunk<?> master) {
    this.master = master;
  }

  @Override public int length() { return master.length(); }
  @Override public int cidx() { return master.cidx(); }
}
