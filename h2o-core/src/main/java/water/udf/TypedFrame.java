package water.udf;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.udf.fp.Function;
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
      @Override protected X evalAt(long coord, long absIndex) {
        return column.apply(coord);
      }
    };
  }
  
  protected X evalAt(long coord, long absIndex) {
    return function.apply(absIndex);
  }
    
  public final static class EnumFrame extends TypedFrame<Integer> {
    
    public EnumFrame(long length, Function<Long, Integer> function, String[] domain) {
      super(Enums.enums(domain), length, function);
    }
  }
  
  protected Vec buildZeroVec() { return factory.buildZeroVec(length); }
  
  private Vec makeVec() throws IOException {
    final Vec vec0 = buildZeroVec();
    MRTask task = new MRTask() {
      @Override public void map(Chunk[] cs) {
        for (Chunk c : cs) {
          DataChunk<X> tc = factory.apply(c);
          for (int r = 0; r < c._len; r++) {

            long positionInVec = ((long)c.cidx()<<32) + r;
            long absoluteIndex = c.start() + r;
            try {
              final X value = evalAt(positionInVec, absoluteIndex);
              tc.set(positionInVec, value);
            } catch (ArrayIndexOutOfBoundsException iae) {
              throw new IllegalArgumentException(iae.getMessage() + " i=" + absoluteIndex + "(" + Long.toHexString(positionInVec) + ")/" + c.cidx() + "," + c._len + "," + r, iae);
            }
          }
        }
      }
    };
    MRTask mrTask = task.doAll(vec0);
    final Vec vec = mrTask._fr.vecs()[0];
    return vec;
  }

  protected DataColumn<X> newColumn(Vec vec) throws IOException {
    return factory.newColumn(vec);
  }

  public DataColumn<X> newColumn() throws IOException {
    return newColumn(makeVec());
  }
  
}
