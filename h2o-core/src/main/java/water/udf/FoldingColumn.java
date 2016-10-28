package water.udf;

import java.util.Arrays;

/**
 * This column depends a plurality of columns
 */
public class FoldingColumn<X, Y> implements Column<Y> {
  private final Foldable<X, Y> f;
  private final Iterable<Column<X>> columns;
  
  public FoldingColumn(Foldable<X, Y> f, Column<X>... columns) {
    this.f = f;
    this.columns = Arrays.asList(columns);
  }

  public FoldingColumn(Foldable<X, Y> f, Iterable<Column<X>> columns) {
    this.f = f;
    this.columns = columns;
  }
  
  @Override public Y get(long idx) {
    Y y = f.initial();
    for (Column<X> col : columns) y = f.apply(y, col.get(idx));
    return y; 
  }

  @Override public boolean isNA(long idx) {
    for (Column<X> col : columns) if (col.isNA(idx)) return true;
    return false;
  }

  @Override
  public String getString(long idx) { 
    Y y = get(idx);
    return y == null ? "(N/A)" : String.valueOf(y); 
  }
}
