package water;

import water.H2O.H2OCountedCompleter;

/**
 * A typed atomic update.
 */
public abstract class TAtomic<T extends Iced> extends Atomic<TAtomic<T>> {
  /** Atomically update an old value to a new one.
   * @param old  The old value, it may be null.  It is a defensive copy.
   * @return The new value; if null if this atomic update no longer needs to be run
   */
  protected abstract T atomic(T old);

  @Override Value atomic(Value val) {
    T old = val == null ? null : (T)(val.get().clone());
    T nnn = atomic(old);
    // Atomic operation changes the data, so it can not be performed over
    // values persisted on read-only data source as we would not be able to
    // write those changes back.
    assert val == null || val.onICE() || !val.isPersisted();
    return  nnn == null ? null : new Value(_key,nnn,val==null?Value.ICE:val.backend());
  }
  @Override protected void onSuccess( Value old ) { onSuccess(old==null?null:(T)old.get()); }
  // Upcast the old value to T
  void onSuccess( T old ) { }
}
