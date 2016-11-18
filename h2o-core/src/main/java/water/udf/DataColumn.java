package water.udf;

import water.DKV;
import water.Key;
import water.fvec.Vec;

/**
 * A Column based on actual data in a Vec (hence implementing Vec.Holder)
 */
public abstract class DataColumn<T> extends ColumnBase<T> {
  protected transient Vec vec = null;
  private Key<Vec> vecKey;
  public final byte type;
  private ChunkFactory<T> chunkFactory;

  /**
   * Deserialization only; pls don't use
   */
  public DataColumn() {
    type = Vec.T_BAD;
  }
  
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
    return vec()._rowLayout;
  }

  @Override
  public long size() {
    return vec().length();
  }

  @Override
  public TypedChunk<T> chunkAt(int i) {
    return chunkFactory.apply(vec().chunkForChunkIdx(i));
  }

  protected DataColumn(Vec vec, ChunkFactory<T> factory) {
    this.vec = vec;
    this.vecKey = vec._key;
    this.type = factory.typeCode();
    this.chunkFactory = factory;
  }

  public boolean isNA(long idx) {
    return vec().isNA(idx);
  }

  public Vec vec() {
    if (vec == null) vec = DKV.get(vecKey).get();
    return vec;
  }
  
  @Override public String toString() {
    return "DataColumn(type=" + type + ", factory=" + chunkFactory + ", vec=" + vec() + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o instanceof DataColumn<?>) {
      DataColumn<?> that = (DataColumn<?>) o;
      return (type == that.type) && vecKey.equals(that.vecKey);
    } else return false;
  }

  @Override
  public int hashCode() {
    return 31 * vecKey.hashCode() + type;
  }
}
