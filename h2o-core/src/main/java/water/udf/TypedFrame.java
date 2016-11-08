package water.udf;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import static water.udf.DataColumns.*;

import java.io.IOException;

/**
 * Single column frame that knows its data type
 */
public class TypedFrame<X> extends Frame {
  private final Factory<X> factory;
  private final long len;
  private final Function<Long, X> function;
  private Column<X> column;  

  public TypedFrame(Factory<X> factory, long len, Function<Long, X> function) {
    super();
    this.factory = factory;
    this.len = len;
    this.function = function;
  }

  final static class EnumFrame1 extends TypedFrame<Integer> {
    private final String[] domain;
    
    public EnumFrame1(long len, Function<Long, Integer> function, String[] domain) {
      super(Enums(domain), len, function);
      this.domain = domain;
    }
  }
  
  protected Vec makeVec() throws IOException {
    // TODO(vlad): we need to inherit the vec layout
    final Vec vec0 = Vec.makeZero(len, factory.typeCode());
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
