package water.udf;

import water.fvec.Frame;
import water.fvec.Vec;

/**
 * Basic common behavior for Functional Columns
 */
public abstract class ColumnBase<T> implements Column<T> {

  public abstract T get(long idx);
  
  @Override public T apply(long idx) { return get(idx); }

  @Override public T apply(Long idx) { return get(idx); }

  @Override
  public String getString(long idx) { return isNA(idx) ? "(N/A)" : String.valueOf(apply(idx)); }

  @Override 
  public boolean isCompatibleWith(Column<?> ys) {
    return new Frame(vec()).isCompatible(new Frame(ys.vec()));
  }
}
