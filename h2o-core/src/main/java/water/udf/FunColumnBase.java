package water.udf;

import water.fvec.Vec;

/**
 * Basic common behavior for Functional Columns
 */
public abstract class FunColumnBase<T> implements Column<T> {
  Column<?> master;
  
  FunColumnBase(Column<?> master) {
    this.master = master;
  }

  @Override public long size() { return master.size(); }
  
  @Override public Vec vec() { return new VirtualVec<>(this); }

  public abstract T get(long idx);
  
  @Override public T apply(long idx) { return get(idx); }

  @Override public T apply(Long idx) { return get(idx); }

  @Override
  public String getString(long idx) { return isNA(idx) ? "(N/A)" : String.valueOf(apply(idx)); }
}
