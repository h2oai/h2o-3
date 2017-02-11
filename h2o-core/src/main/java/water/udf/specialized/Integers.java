package water.udf.specialized;

import water.fvec.Chunk;
import water.fvec.Vec;
import water.udf.DataChunk;
import water.udf.DataColumn;
import water.udf.DataColumns;

/**
 * Specialized factory for Integer numbers
 */
public class Integers extends DataColumns.SimpleColumnFactory<Integer> {
  public static final water.udf.specialized.Integers Integers = new Integers();
   
  public Integers() {
    super(Vec.T_NUM, "Integers");
  }

  @Override
  public DataChunk<Integer> apply(final Chunk c) {
    return new IntegerChunk(c);
  }

  @Override
  public DataColumn<Integer> newColumn(final Vec vec) {
    if (vec.get_type() != Vec.T_NUM)
      throw new IllegalArgumentException("Expected type T_NUM, got " + vec.get_type_str());
    return new Column(vec, this);
  }

  static class IntegerChunk extends DataChunk<Integer> {
    /**
     * deserialization wants it
     */
    public IntegerChunk() {}    
    IntegerChunk(Chunk c) {
      super(c);
    }
    @Override public Integer get(int idx) { return c.isNA(idx) ? null : (int)c.at8(idx); }

    @Override public void set(int idx, Integer value) {
      if (value == null) c.setNA(idx); else c.set(idx, value);
    }
  }

  static class Column extends DataColumn<Integer> {
    /**
     * deserialization wants it
     */
    public Column() {}
    Column(Vec vec, DataColumns.BaseFactory<Integer, ?> factory) {
      super(vec, factory);      
    }
    public Integer get(long idx) { return (int)vec().at8(idx); }

    @Override public Integer apply(Long idx) { return get(idx); }

    @Override public Integer apply(long idx) { return get(idx); }

    @Override public void set(long idx, Integer value) {
      if (value == null) vec().setNA(idx); else vec().set(idx, value);
    }

    public void set(long idx, int value) { vec().set(idx, value); }
  }
}
