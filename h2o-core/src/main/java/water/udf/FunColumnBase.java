package water.udf;

import water.fvec.Vec;

/**
 * Basic common behavior for Functional Columns
 */
public abstract class FunColumnBase<T> extends ColumnBase<T> implements Column<T> {
  Column<?> master;
  
  FunColumnBase(Column<?> master) {
    this.master = master;
  }

  private Vec myVec = null;

  @Override public Vec vec() {
    if (myVec == null) myVec = new VirtualVec<>(this);
    return myVec;
  }

  @Override public long size() { return master.size(); }

  public abstract T get(long idx);
  
  @Override public T apply(long idx) { return get(idx); }

  @Override public T apply(Long idx) { return get(idx); }

  @Override
  public String getString(long idx) { return isNA(idx) ? "(N/A)" : String.valueOf(apply(idx)); }
}
