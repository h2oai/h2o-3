package water.udf;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.fp.Function;
import water.udf.specialized.Enums;

import static water.udf.DataColumns.*;

import java.io.IOException;

/**
 * Single column frame that knows its data type
 */
public class TypedFrame<X> extends Frame {
  private final ColumnFactory<X> factory;
  private final long length;
  private final Function<Long, X> function;
  private Column<X> column;

  /**
   * deserialization :(
   */
  public TypedFrame() {
    factory = null;
    length = -1;
    function = null;
  }
  
  public TypedFrame(BaseFactory<X> factory, long length, Function<Long, X> function) {
    super();
    this.factory = factory;
    this.length = length;
    this.function = function;
  }

  public static <X> TypedFrame<X> forColumn(final BaseFactory<X> factory, final Column<X> column) {
    return new TypedFrame<X>(factory, column.size(), column) {
      @Override protected Vec buildZeroVec() { return factory.buildZeroVec(column); }
    };
  }
  
  public final static class EnumFrame extends TypedFrame<Integer> {
    private final String[] domain;
    
    public EnumFrame(long length, Function<Long, Integer> function, String[] domain) {
      super(Enums.enums(domain), length, function);
      this.domain = domain;
    }
  }
  
  protected Vec buildZeroVec() { return factory.buildZeroVec(length); }
  
  protected Vec makeVec() throws IOException {
    final Vec vec0 = buildZeroVec();
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
