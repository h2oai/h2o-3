package water.udf;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import static water.udf.MaterializedColumns.*;

import java.io.IOException;

/**
 * Single column frame that knows its data type
 */
public class TypedFrame1<X> extends Frame {
  private final Factory<X> columnFactory;
  private final long len;
  private final Function<Long, X> function;
  private Column<X> column;  

  public TypedFrame1(Factory<X> columnFactory, long len, Function<Long, X> function) {
    super();
    this.columnFactory = columnFactory;
    this.len = len;
    this.function = function;
  }

  final static class EnumFrame1 extends TypedFrame1<Integer> {
    private final String[] domain;
    
    public EnumFrame1(long len, Function<Long, Integer> function, String[] domain) {
      super(Enums, len, function);
      this.domain = domain;
    }

    @Override protected TypedVector<Integer> newColumn(Vec vec) throws IOException {
      vec.setDomain(domain);
      return Enums.newColumn(vec);
    }
  }
  
  protected Vec makeVec() throws IOException {
    column = newColumn(Vec.makeZero(len, columnFactory.typeCode));
    MRTask task = new MRTask() {
      @Override
      public void map(Chunk[] cs) {
        for (Chunk c : cs) {
          TypedChunk<X> tc = columnFactory.newChunk(c);
          for (int r = 0; r < c._len; r++) {
            long i = r + c.start();
            tc.set(r, function.apply(i));
          }
        }
      }
    };
    final Vec vec = column.vec();
    final MRTask mrTask = task.doAll(vec);
    return mrTask._fr.vecs()[0];
  }

  protected TypedVector<X> newColumn(Vec vec) throws IOException {
    return columnFactory.newColumn(vec);
  }

  public TypedVector<X> newColumn() throws IOException {
    return newColumn(makeVec());
  }
  
}
