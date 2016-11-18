package water.udf;

import water.fvec.Vec;

/**
 * Basic common behavior for Functional Columns
 */
public abstract class FunColumnBase<T> extends ColumnBase<T> implements Column<T> {
  Column<?> master;

  /**
   * deserialization :(
   */
  public FunColumnBase() {}
  
  FunColumnBase(Column<?> master) {
    this.master = master;
  }

  transient private Vec myVec = null;

  @Override public Vec vec() {
    if (myVec == null) myVec = new VirtualVec<>(this);
    return myVec;
  }

  @Override public long size() { return master.size(); }

  public abstract T get(long idx);
  
  @Override public T apply(long idx) { return get(idx); }

  @Override public T apply(Long idx) { return get(idx); }
  
  public int hashCode(JustCode x) {
    return x == null ? 0 : x.getClass().hashCode();
  }

  public boolean equal(JustCode x, JustCode y) {
    return x == null ? y == null : x.getClass() == y.getClass();
  }
}
