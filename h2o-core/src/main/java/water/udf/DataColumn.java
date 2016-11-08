package water.udf;

import water.fvec.Vec;

/**
 * A Column based on actual data in a Vec (hence implementing Vec.Holder)
 */
public abstract class DataColumn<T> extends ColumnBase<T> {
  protected Vec vec;
  public final byte type;
  private ChunkFactory<T> chunkFactory;

  public abstract T get(long idx);

  public abstract void set(long idx, T value);

  @Override
  public T apply(Long idx) {
    return get(idx);
  }

  @Override
  public T apply(long idx) {
    return get(idx);
  }

  @Override
  public int rowLayout() {
    return vec._rowLayout;
  }

  @Override
  public long size() {
    return vec.length();
  }

  @Override
  public TypedChunk<T> chunkAt(int i) {
    return chunkFactory.apply(vec.chunkForChunkIdx(i));
  }

  protected DataColumn(Vec vec, byte type, ChunkFactory<T> factory) {
    this.vec = vec;
    this.type = type;
    this.chunkFactory = factory;
  }

  public boolean isNA(long idx) {
    return vec.isNA(idx);
  }

  public String getString(long idx) {
    return isNA(idx) ? "(N/A)" : String.valueOf(apply(idx));
  }

  public Vec vec() {
    return vec;
  }
  
  @Override public String toString() {
    return "DataColumn(type=" + type + ", factory=" + chunkFactory + ", vec=" + vec + ")";
  }
}
