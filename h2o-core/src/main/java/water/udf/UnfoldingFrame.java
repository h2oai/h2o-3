package water.udf;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.udf.fp.Function;
import water.udf.specialized.EnumColumn;
import water.udf.specialized.Enums;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static water.udf.MatrixFrame.forColumns;

/**
 * Single-column frame that knows its data type and can unfold
 */
public class UnfoldingFrame<DataType, ColumnType extends DataColumn<DataType>> extends Frame {
  protected final ColumnFactory<DataType, ColumnType> factory;
  protected final long len;
  protected final Function<Long, List<DataType>> function;
  protected final int width;

  /** for deserialization (sigh) */
  public UnfoldingFrame() {
    factory = null;
    len = -1;
    function = null;
    width = -1;
  }
  
  public UnfoldingFrame(ColumnFactory<DataType, ColumnType> factory, long len, Function<Long, List<DataType>> function, int width) {
    super();
    this.factory = factory;
    this.len = len;
    this.function = function;
    this.width = width;
    assert len   >= 0: "Frame must have a nonnegative length, but found"+len;
    assert width >= 0: "Multicolumn frame must have a nonnegative width, but found"+width;
  }

  public static 
  <DataType, ColumnType extends DataColumn<DataType>> 
  UnfoldingFrame<DataType, ColumnType> unfoldingFrame(
      final ColumnFactory<DataType, ColumnType> factory, 
      final Column<List<DataType>> master, int width) {
    return new UnfoldingFrame<DataType, ColumnType>(factory, master.size(), master, width) {
      @Override protected Vec buildZeroVec() {
        Vec v0 = DataColumns.buildZeroVec(this.len, factory.typeCode());
        v0.align(master.vec());
        return v0;
      }
    };
  }

  static class UnfoldingEnumFrame extends UnfoldingFrame<Integer, EnumColumn> {
    private final String[] domain;
    
    /** for deserialization */
    public UnfoldingEnumFrame() {domain = null; }
    
    public UnfoldingEnumFrame(long length, Function<Long, List<Integer>> function, int width, String[] domain) {
      super(Enums.enumsAlt(domain), length, function, width);
      this.domain = domain;
      assert domain != null : "An enum must have a domain";
      assert domain.length > 0 : "Domain cannot be empty";
    }
  }

  public static <DataType> UnfoldingEnumFrame UnfoldingEnumFrame(final Column<List<Integer>> master, int width, String[] domain) {
    return new UnfoldingEnumFrame(master.size(), master, width, domain) {
      @Override protected Vec buildZeroVec() {
        Vec v0 = DataColumns.buildZeroVec(this.len, Vec.T_CAT);
        v0.align(master.vec());
        return v0;
      }
    };
  }
  
  protected Vec buildZeroVec() {
    return DataColumns.buildZeroVec(len, factory.typeCode());
  }
  
  protected List<Vec> makeVecs() throws IOException {
    Vec[] vecs = new Vec[width];

    for (int j = 0; j < width; j++) {
      vecs[j] = buildZeroVec();
    }

    MRTask task = new MRTask() {
      @Override
      public void map(Chunk[] cs) {
        int size = cs[0].len(); // TODO(vlad): find a solution for empty  
        long start = cs[0].start();
        for (int r = 0; r < size; r++) {
          long i = r + start;
          List<DataType> values = function.apply(i);
          for (int j = 0; j < cs.length; j++) {
            DataChunk<DataType> tc = factory.apply(cs[j]);
            tc.set(r, j < values.size() ? values.get(j) : null);
          }          
        }
      }
    };
    
    MRTask mrTask = task.doAll(vecs);
    return Arrays.asList(mrTask._fr.vecs());
  }

  public MatrixFrame<ColumnType> materialize() throws IOException {
    List<Vec> vecs = makeVecs();
    List<ColumnType> columns = new ArrayList<>(width);

    for (Vec vec : vecs) columns.add(factory.newColumn(vec));
    MatrixFrame<ColumnType> matrix = forColumns(columns);
    matrix._names = _names;
    
    return matrix;
  }
  
}
