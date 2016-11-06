package water.udf;

import water.fvec.Frame;

import java.io.IOException;

/**
 * Three-column frame that knows its data types
 */
public class TypedFrame3<X, Y, Z> extends Frame {
  private final Function2<X, Y, Z> function;
  private Column<X> columnX;
  private Column<Y> columnY;

  public TypedFrame3(Column<X> columnX, Column<Y> columnY, Function2<X, Y, Z> function) {
    super();
    this.columnX = columnX;
    this.function = function;
  }

  final static class EnumFrame3<X, Y> extends TypedFrame3<X, Y, Integer> {
    private final String[] domain;
    
    public EnumFrame3(Column<X> columnX, Column<Y> columnY, Function2<X, Y, Integer> function, String[] domain) {
      super(columnX, columnY, function);
      this.domain = domain;
    }
  }

  public Fun2Column<X, Y, Z> newColumn() throws IOException {
    return new Fun2Column<>(function, columnX, columnY);
  }
}
