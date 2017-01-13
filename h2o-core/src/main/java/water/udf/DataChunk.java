package water.udf;

import water.fvec.Chunk;
import water.fvec.Vec;

/**
 * Wrapper of a chunk that knows its type, with mutability
 */
public abstract class DataChunk<T> implements TypedChunk<T> {
  protected Chunk c;

  /**
   * Deserializaiton only
   */
  public DataChunk() {}
  
  public DataChunk(Chunk c) { this.c = c; }

  /**
   * Transforms absolute value position to value position within the chunk
   * @param position value position, a long that consists of chunk number and relative position of the value in the chunk
   * @return value position within the chunk
   */
  protected int indexOf(long position) { 
    return indexOf(position, c.len());
  }

  /**
   * Transforms absolute value position to value position within the chunk
   * @param position value position, a long that consists of chunk number and relative position of the value in the chunk
   * @param chunkLength length of current chunk
   * @return value position within the chunk
   */
  protected static int indexOf(long position, int chunkLength) {
    int p = Integer.MAX_VALUE & (int) position;
    if (p >= chunkLength || p < 0) {
      throw new ArrayIndexOutOfBoundsException("The size of chunk is " + chunkLength + ", but you want #" + p);
    }
    return p;
  }

  /**
   * Calculates an absolute position of a value in vec:
   * a long that consists of chunk number (cidx) and the value's relative position within the chunk
   * @param cidx chunk number
   * @param i position within the chunk
   * @param chunkLength chunk length
   * @return absolute position
   */
  static long positionOf(int cidx, long i, int chunkLength) {
    return ((long)cidx << Integer.SIZE) | indexOf(i, chunkLength);
  }

  /**
   * Calculates an absolute position of a value in vec:
   * a long that consists of chunk number (cidx) and the value's relative position within the chunk
   * @param index absolute index of a value within a Vec
   * @param cidx chunk number
   * @param start chunk start position
   * @param chunkLength chunk length
   * @return absolute position
   */
  static long positionOf(long index, int cidx, long start, int chunkLength) {
    return positionOf(cidx, index - start, chunkLength);
  }

  @Override public boolean isNA(long i) { return c.isNA(indexOf(i)); }
  public void setNA(int i) { c.setNA(indexOf(i)); }

  @Override public long start() { return c.start(); }
  @Override public int length() { return c.len(); }

  public void set(long position, T value) {
    int i = indexOf(position);
    if (value == null) c.setNA(i); else setValue(i, value);
  }

  protected abstract void setValue(int at, T value);
  
  @Override public Vec vec() { return c.vec(); }
}
