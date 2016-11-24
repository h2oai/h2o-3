package water.udf;

import water.fvec.Chunk;

/**
 * Represents a chunk that depends on another
 */
public abstract class DependentChunk<T> implements TypedChunk<T> {
  private final TypedChunk<?> master;
  DependentChunk(TypedChunk<?> master) {
    this.master = master;
  }

  public long start() { return master.rawChunk().start(); }
  @Override public int len() { return master.len(); }
  @Override public int cidx() { return master.cidx(); }
}
