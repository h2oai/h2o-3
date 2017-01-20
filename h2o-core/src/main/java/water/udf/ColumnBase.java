package water.udf;

import water.Iced;
import water.fvec.Frame;
import water.fvec.Vec;

/**
 * Basic common behavior for Functional Columns
 */
public abstract class ColumnBase<T> extends Iced<ColumnBase<T>> implements Column<T> {

  public abstract T get(long position);
  
  @Override public T apply(long position) { return get(position); }

  @Override public T apply(Long position) { return get(position); }

  @Override 
  public boolean isCompatibleWith(Column<?> ys) {
    return vec().isCompatibleWith(ys.vec());
  }
}
