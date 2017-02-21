package water.udf;

import water.fvec.Frame;
import water.fvec.Vec;

import java.util.ArrayList;
import java.util.List;

/**
 * Single column frame that knows its data type and can materialize
 */
public class MatrixFrame<ColumnType extends DataColumn<?>> 
    extends Frame {
  private final long length;
  private List<ColumnType> columns;

  /**
   * deserialization :(
   */
  public MatrixFrame() {
    length = -1;
  }
  
  MatrixFrame(long length, List<ColumnType> columns) {
    super(vecs(columns));
    this.length = length;
    this.columns = columns;
  }

  public ColumnType column(int i) {
    return columns.get(i);
  }

  public int width() {
    return columns.size();
  }
  
  private static <ColumnType extends Column<?>> Vec[] vecs(List<ColumnType> columns) {
    List<Vec> vecs = new ArrayList<>(columns.size());
    for (Column c : columns) vecs.add(c.vec());
    
    return vecs.toArray(new Vec[0]);
  }

  public static 
  <DataType, ColumnType extends DataColumn<DataType>>
  MatrixFrame<ColumnType> forColumns(final List<ColumnType> columns) {
    return new MatrixFrame<ColumnType>(columns.get(0).size(), columns) {
    };
  }

  
  //.toArray
}
