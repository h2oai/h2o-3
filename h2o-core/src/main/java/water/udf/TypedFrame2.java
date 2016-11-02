package water.udf;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;

import java.io.IOException;

import static water.udf.MaterializedColumns.*;

/**
 * Single column frame that knows its data type
 */
public class TypedFrame2<X, Y> extends Frame {
  private final Factory<Y> columnFactory;
  private final long len;
  private final Function<X, Y> function;
  private Column<X> columnX;
  private Column<X> columnY;

  public TypedFrame2(Factory<Y> columnFactory, long len, Function<X, Y> function) {
    super();
    this.columnFactory = columnFactory;
    this.len = len;
    this.function = function;
  }

  final static class EnumFrame2<X> extends TypedFrame2<X, Integer> {
    private final String[] domain;
    
    public EnumFrame2(long len, Function<X, Integer> function, String[] domain) {
      super(Enums, len, function);
      this.domain = domain;
    }

    @Override protected TypedVector<Integer> newColumn(Vec vec) throws IOException {
      vec.setDomain(domain);
      return Enums.newColumn(vec);
    }
  }
  
  protected TypedVector<Y> newColumn(Vec vec) throws IOException {
    return columnFactory.newColumn(vec);
  }

  public FunColumn<X, Y> newColumn() throws IOException {
    return new FunColumn<>(function, columnX);
  }
  
}
