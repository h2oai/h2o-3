package water.udf;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;

import java.io.IOException;

import static water.udf.DataColumns.*;

/**
 * Single column frame that knows its data type
 */
public class TypedFrame2<X, Y> extends Frame {
  private final Function<X, Y> function;
  private Column<X> columnX;

  public TypedFrame2(Column<X> columnX, Function<X, Y> function) {
    super();
    this.columnX = columnX;
    this.function = function;
  }

  final static class EnumFrame2<X> extends TypedFrame2<X, Integer> {
    private final String[] domain;
    
    public EnumFrame2(Column<X> columnX, Function<X, Integer> function, String[] domain) {
      super(columnX, function);
      this.domain = domain;
    }
  }

  public FunColumn<X, Y> newColumn() throws IOException {
    return new FunColumn<>(function, columnX);
  }

  protected TypedVector<Y> newColumn(Factory<Y> factory) throws IOException {
    Vec vec;
    return factory.newColumn(materializeVec(factory, columnX.vec().length()));
  }

  public Column<Y> materialize() throws IOException {
    return newColumn();
  }
  protected Vec materializeVec(final Factory<Y> factory, long len) throws IOException {
    Column<Y> column = factory.newColumn(Vec.makeZero(len, factory.typeCode));
    MRTask task = new MRTask() {
      @Override
      public void map(Chunk[] cs) {
        for (Chunk c : cs) {
          TypedChunk<X> xc = columnX.chunkAt(c.cidx());
          DataChunk<Y> yc = factory.apply(c);
          for (int i = 0; i < c._len; i++) {
            X x = xc.get(i);
            yc.set(i, function.apply(x));
          }
        }
      }
    };
    Vec vec = column.vec();
    MRTask mrTask = task.doAll(vec);
    return mrTask._fr.vecs()[0];
  }
  
}
