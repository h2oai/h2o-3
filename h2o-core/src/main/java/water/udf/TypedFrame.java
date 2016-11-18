package water.udf;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.udf.specialized.Enums;

import static water.udf.DataColumns.*;

import java.io.IOException;

/**
 * Single column frame that knows its data type
 */
public class TypedFrame<X> extends Frame {
  private final ColumnFactory<X> factory;
  private final long len;
  private final Function<Long, X> function;
  private Column<X> column;

  /**
   * deserialization :(
   */
  public TypedFrame() {
    factory = null;
    len = -1;
    function = null;
  }
  
  public TypedFrame(BaseFactory<X> factory, long len, Function<Long, X> function) {
    super();
    this.factory = factory;
    this.len = len;
    this.function = function;
  }

  public static <X> TypedFrame<X> forColumn(final BaseFactory<X> factory, final Column<X> column) {
    return new TypedFrame<X>(factory, column.size(), column) {
      @Override protected Vec initVec() { return factory.initVec(column); }
    };
  }
  
  public final static class EnumFrame extends TypedFrame<Integer> {
    private final String[] domain;
    
    public EnumFrame(long len, Function<Long, Integer> function, String[] domain) {
      super(Enums.enums(domain), len, function);
      this.domain = domain;
    }
  }
  
  protected Vec initVec() { return factory.initVec(len); }
  
  protected Vec makeVec() throws IOException {
    final Vec vec0 = initVec();
    MRTask task = new MRTask() {
      @Override public void map(Chunk[] cs) {
        for (Chunk c : cs) {
          DataChunk<X> tc = factory.apply(c);
          for (int r = 0; r < c._len; r++) {
            long i = r + c.start();
            tc.set(r, function.apply(i));
          }
        }
      }
    };
    MRTask mrTask = task.doAll(vec0);
    return mrTask._fr.vecs()[0];
  }

  protected DataColumn<X> newColumn(Vec vec) throws IOException {
    return factory.newColumn(vec);
  }

  public DataColumn<X> newColumn() throws IOException {
    return newColumn(makeVec());
  }
  
}
