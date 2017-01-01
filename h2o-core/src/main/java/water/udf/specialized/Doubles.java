package water.udf.specialized;

import water.fvec.Chunk;
import water.fvec.Vec;
import water.udf.ColumnFactory;
import water.udf.DataChunk;
import water.udf.DataColumn;
import water.udf.DataColumns;

/**
 * Specialized factory for double numbers
 */
public class Doubles extends DataColumns.BaseFactory<Double> {
  public static final water.udf.specialized.Doubles Doubles = new Doubles();
   
  public Doubles() {
    super(Vec.T_NUM, "Doubles");
  }

  @Override
  public DataChunk<Double> apply(final Chunk c) {
    return new DoubleChunk(c);
  }

  @Override
  public DataColumn<Double> newColumn(final Vec vec) {
    if (vec.get_type() != Vec.T_NUM)
      throw new IllegalArgumentException("Expected type T_NUM, got " + vec.get_type_str());
    return new Column(vec, this);
  }

  static class DoubleChunk extends DataChunk<Double> {
    /**
     * deserialization wants it
     */
    public DoubleChunk() {}    
    DoubleChunk(Chunk c) {
      super(c);
    }
    @Override public Double get(long idx) { 
      int i = index4(idx);
      return c.isNA(i) ? Double.NaN : c.atd(i); 
    }

    @Override public void set(long idx, Double value) {
      int i = index4(idx);
      if (value == null) c.setNA(i); else c.set(i, value);
    }
    public void set(int idx, double value) { c.set(idx, value); }
  }

  static class Column extends DataColumn<Double> {
    /**
     * deserialization wants it
     */
    public Column() {}
    Column(Vec vec, ColumnFactory<Double> factory) {
      super(vec, factory);      
    }
    public Double get(long coord) {
      DoubleChunk c = new DoubleChunk(chunkAt(coord));
      return c.get(coord);
    }

    @Override public void set(long coord, Double value) {
      DoubleChunk c = new DoubleChunk(chunkAt(coord));
      c.set(coord, value);
    }

    public void set(long coord, double value) {
      DoubleChunk c = new DoubleChunk(chunkAt(coord));
      c.set(coord, value);
    }
  }
}
