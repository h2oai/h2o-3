package water.udf.specialized;

import water.fvec.CStrChunk;
import water.fvec.Chunk;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.udf.ColumnFactory;
import water.udf.DataChunk;
import water.udf.DataColumn;
import water.udf.DataColumns;

/**
 * Factory for strings column
 */
public class Strings extends DataColumns.BaseFactory<String> {
  public static final Strings Strings = new Strings();
  
  public Strings() {
    super(Vec.T_STR, "Strings");
  }

  static class StringChunk extends DataChunk<String> {
    /**
     * deserialization :(
     */
    public StringChunk() {}
    
    public StringChunk(Chunk c) { super(c); }
    @Override
    public String get(long position) {
      int i = indexOf(position);
      try {
        return asString(c.atStr(new BufferedString(), i));
      } catch (IllegalArgumentException iae) {
        if (iae.getMessage().equals("Not a String")) return null;
        throw new IllegalArgumentException("position was " + Long.toHexString(position) + "; " + iae.getMessage(), iae);
      } catch (ArrayIndexOutOfBoundsException aie) {
        aie.printStackTrace();
        CStrChunk cs = (CStrChunk) c;
        int idx = cs.idx(i);
        int offset = cs.intAt(idx);
        throw new IllegalArgumentException("Position was " + Long.toHexString(position) + ", i=" + i + ", got " + aie.getMessage() + "; details: " + this + "; idx=" + idx + ", offset=" + offset, aie);
      }
    }

    @Override
    public void setValue(int at, String value) {
      c.set(at, value);
    }
    
    @Override
    public String toString() {
      return "StringChunk(" + c + ")";
    }
  }
  
  @Override
  public DataChunk<String> apply(final Chunk c) {
    return new StringChunk(c);
  }

  static class StringColumn extends DataColumn<String> {
    /**
     * deserialization :(
     */
    public StringColumn() {}
    
    StringColumn(Vec vec, ColumnFactory<String> factory) { super(vec, factory); }

    @Override
    public String get(long position) {
      StringChunk c = new StringChunk(chunkAt(position));
      return c.get(position);
    }

    @Override
    public void set(long position, String value) {
      StringChunk c = new StringChunk(chunkAt(position));
      c.set(position, value);
    }
  }
  
  @Override
  public DataColumn<String> newColumn(final Vec vec) {
    if (vec.get_type() != Vec.T_STR)
      throw new IllegalArgumentException("Expected type T_STR, got " + vec.get_type_str());
    return new StringColumn(vec, this);
  }
  
  private static String asString(Object x) { 
    return x == null ? null : x.toString(); 
  }

}
