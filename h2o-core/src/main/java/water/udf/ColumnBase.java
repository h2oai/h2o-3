package water.udf;

import water.Iced;
import water.fvec.Frame;
import water.fvec.Vec;

/**
 * Basic common behavior for Functional Columns
 */
public abstract class ColumnBase<T> extends Iced<ColumnBase<T>> implements Column<T> {

  public abstract T get(long idx);
  
  @Override public T apply(long idx) { return get(idx); }

  @Override public T apply(Long idx) { return get(idx); }

  @Override 
  public boolean isCompatibleWith(Column<?> ys) {
    boolean itis = new Frame(vec()).isCompatible(new Frame(ys.vec()));
    if (!itis) {
      new Frame(vec()).isCompatible(new Frame(ys.vec()));
    }
    return itis;
  }
}
