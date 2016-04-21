package water;

/**
 *  A typed atomic update.
 */
public abstract class TAtomic<T extends Freezable> extends Atomic<TAtomic<T>> {
  public TAtomic(){}
  public TAtomic(H2O.H2OCountedCompleter completer){super(completer);}
  /** Atomically update an old value to a new one.
   *  @param old  The old value, it may be null.  It is a defensive copy.
   *  @return The new value; if null if this atomic update no longer needs to be run
   */
  protected abstract T atomic(T old);

  @Override protected Value atomic(Value val) {
    T old = val == null || val.isNull() ? null : (T)(val.getFreezable().clone());
    T nnn = atomic(old);
    // Atomic operation changes the data, so it can not be performed over
    // values persisted on read-only data source as we would not be able to
    // write those changes back.
    assert val == null || val.onICE() || !val.isPersisted();
    return nnn == null ? null : new Value(_key,nnn,val==null?Value.ICE:val.backend());
  }
  @Override protected void onSuccess( Value old ) { onSuccess(old==null?null:(T)old.getFreezable()); }
  // Upcast the old value to T
  public void onSuccess( T old ) { }
}
