package water.udf;

import water.DKV;
import water.Key;
import water.fvec.Chunk;
import water.fvec.Vec;

import java.util.Iterator;

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

  /**
   * Gets the vec value by its coordinates
   * @param position coordinates, (chunkNumber, relativePosition) represented as long
   * @return the value of type T
   */
  public abstract T get(long position);
  
  public T getByRowNumber(long i) {
    return get(positionOfRow(i));
  }

  public long positionOfRow(long i) {
    Chunk ch = vec.chunkForRow(i);
    return DataChunk.positionOf(i, ch.cidx(), ch.start(), ch.len());
  }

  @Override
  public Iterable<Long> positions() {
    return new Iterable<Long>() {
     
      @Override
      public Iterator<Long> iterator() {
        return new Iterator<Long>() {
          int ciMax = vec().nChunks();
          Chunk c = vec().chunkForChunkIdx(0);
          int ci = 0;
          int i = 0;
          @Override
          public boolean hasNext() {
            return ci < ciMax && i < c.len();
          }

          @Override
          public Long next() {
            if (!hasNext()) throw new IndexOutOfBoundsException("Chunk#" + ci + ", last was #" + i);
            Long x = DataChunk.positionOf(ci, i, c.len());
            if (++i >= c.len()) {
              ++ci;
              if (ci < ciMax) c = vec().chunkForChunkIdx(ci);
            }
            return x;
          }
          
          public void remove() {
            throw new UnsupportedOperationException("can't remove immutable data");
          }
        };
      }
    };
  }

  /**
   * Sets the vec value by its coordinates
   * @param i coordinates, (chunkNumber, relativePosition) represented as long
   * @param value the value to set
   */
  public abstract void set(long i, T value);

  public void setByIndex(long index, T value) {
    set(positionOfRow(index), value);
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

  protected Chunk chunkAt(long i) { return chunk((int)(i>>32)); } 
  
  public boolean isNA(long i) {
    final Chunk chunk = chunkAt(i);
    return chunk.isNA(DataChunk.indexOf(i, chunk.len()));
  }

  public Vec vec() {
    if (vec == null) vec = DKV.get(vecKey).get();
    return vec;
  }
  
  public Chunk chunk(int i) {
    return vec().chunkForChunkIdx(i);
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
    return 61 * vecKey.hashCode() + type;
  }
}
