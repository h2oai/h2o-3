package water.udf;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;

import java.io.IOException;

import static water.udf.DataColumns.*;

/**
 * Two-column frame that knows its data types
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
}
