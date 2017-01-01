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

  @Override public Vec vec() {
    return master.vec();
  }

  @Override public long size() { return master.size(); }

  public abstract T get(long i);
  
  public T getByIndex(long i) { return get(index2position(i)); }
  
  @Override
  public Iterable<Long> positions() {
    return master.positions();
  }

  public long index2position(long i) { return master.index2position(i); }

}
