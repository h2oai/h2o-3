package water.udf.specialized;

import water.fvec.Chunk;
import water.fvec.Vec;
import water.udf.ColumnFactory;
import water.udf.DataChunk;
import water.udf.DataColumn;
import water.udf.DataColumns;

import java.util.Date;

/**
 * Specialized factory for dates
 */
public class Dates extends DataColumns.BaseFactory<Date> {
  public static final water.udf.specialized.Dates Dates = new Dates();
  
  public Dates() {
    super(Vec.T_TIME, "Time");
  }

  static class DateChunk extends DataChunk<Date> {
    /**
     * for deserialization
     */
    public DateChunk(){}
    
    public DateChunk(Chunk c) {
      super(c);
    }
    
    @Override
    public Date get(int idx) {
      return isNA(idx) ? null : new Date(c.at8(idx));
    }

    @Override
    public void set(int idx, Date value) {
      if (value == null) c.setNA(idx);
      else c.set(idx, value.getTime());
    }
  }
  
  @Override
  public DataChunk<Date> apply(final Chunk c) {
    return new DateChunk(c);
  }

  static class Column extends DataColumn<Date> {
    public Column() {}
    public Column(Vec v, ColumnFactory<Date> factory) {
      super(v, factory);
    }
    @Override
    public Date get(long idx) {
      return isNA(idx) ? null : new Date(vec().at8(idx));
    }

    @Override
    public void set(long idx, Date value) {
      if (value == null) vec().setNA(idx);
      else vec().set(idx, value.getTime());
    }
  }
  
  
  @Override
  public DataColumn<Date> newColumn(final Vec vec) {
    if (vec.get_type() != Vec.T_TIME && vec.get_type() != Vec.T_NUM)
      throw new IllegalArgumentException("Expected a type compatible with Dates, got " + vec.get_type_str());
    return new Column(vec, this);
  }
}
