package water;

import water.util.Log;

import java.util.Arrays;

/** Lockable Keys - Keys locked during long running {@link Job}s, to prevent
 *  overwriting in-use keys.  E.g. model-building: expected to read-lock input
 *  {@link water.fvec.Frame}s, and write-lock the output {@link hex.Model}.
 *  Parser should write-lock the output Frame, to guard against double-parsing.
 *  This is a simple cooperative distributed locking scheme.  Because we need
 *  <em>distributed</em> locking, the normal Java locks do not work.  Because
 *  we cannot enforce good behavior, this is a <em>cooperative</em> scheme
 *  only.
 *  
 *  Supports: <ul>
 *    <li>lock-and-delete-old-and-update (for new Keys)</li>
 *    <li>lock-and-delete                (for removing old Keys)</li>
 *    <li>unlock</li>
 *  </ul>
 *  
 *  @author <a href="mailto:cliffc@h2o.ai"></a>
 *  @version 1.0
 */
public abstract class Lockable<T extends Lockable<T>> extends Keyed<T> {
  /** List of Job Keys locking this Key.
   *  <ul>
   *  <li>Write-locker job  is  in {@code _lockers[0 ]}.  Can be null locker.</li>
   *  <li>Read -locker jobs are in {@code _lockers[1+]}.</li>
   *  <li>Unlocked has _lockers equal to null.</li>
   *  <li>Only 1 situation will be true at a time; atomically updated.</li>
   *  <li>Transient, because this data is only valid on the master node.</li>
   *  </ul>
   */
  public transient Key<Job> _lockers[];

  /** Create a Lockable object, if it has a {@link Key}. */
  public Lockable( Key<T> key ) { super(key); }

  // -----------
  // Atomic create+overwrite of prior key.
  // If prior key exists, block until acquire a write-lock.
  // Then call remove, removing all of a prior key.
  // The replace this object as the new Lockable, still write-locked.
  // "locker" can be null, meaning the special no-Job locker; for use by expected-fast operations
  //
  // Example: write-lock & remove an old Frame, and replace with a new locked Frame
  //     Local-Node                              Master-Node
  // (1)  new,old    -->write_lock(job)-->          old
  // (2)  new,old.waiting...                     new,old+job-locked atomic xtn loop
  // (3)                                            old.remove onSuccess
  // (4)  new        <--update success <--       new+job-locked

  /** Write-lock {@code this._key} by {@code job_key}.  
   *  Throws IAE if the Key is already locked.
   *  @return the old POJO mapped to this Key, generally for deletion. */
  public Lockable write_lock() { return write_lock((Key<Job>)null); }
  public Lockable write_lock( Job job ) { return write_lock(job._key); }
  public Lockable write_lock( Key<Job> job_key ) {
    Log.debug("write-lock "+_key+" by job "+job_key);
    return ((PriorWriteLock)new PriorWriteLock(job_key).invoke(_key))._old;
  }

  /** Write-lock {@code this._key} by {@code job_key}, and delete any prior mapping.  
   *  Throws IAE if the Key is already locked.
   *  @return self, locked by job_key */
  public T delete_and_lock( ) { return delete_and_lock((Key<Job>)null); }
  public T delete_and_lock( Job job ) { return (T)delete_and_lock(job._key); }
  public T delete_and_lock( Key<Job> job_key ) {
    Lockable old =  write_lock(job_key);
    if( old != null ) {
      Log.debug("lock-then-clear "+_key+" by job "+job_key);
      old.remove_impl(new Futures()).blockForPending();
    }
    return (T)this;
  }

  /** Write-lock key and delete; blocking.
   *  Throws IAE if the key is already locked.  
   */
  public static void delete( Key key ) { 
    Value val = DKV.get(key);
    if( val==null ) return;
    ((Lockable)val.get()).delete();
  }
  /** Write-lock 'this' and delete; blocking.
   *  Throws IAE if the _key is already locked.
   *
   *  Subclasses that need custom deletion logic should override {@link #remove_impl(Futures)}
   *  as by contract, the only difference between {@link #delete()} and {@link #remove()}
   *  is that `delete` first write-locks `this`.
   */
  public final void delete( ) { delete(null,new Futures()).blockForPending(); }
  /** Write-lock 'this' and delete. 
   *  Throws IAE if the _key is already locked.
   *
   *  Subclasses that need custom deletion logic should override {@link #remove_impl(Futures)}.
   */
  public final Futures delete( Key<Job> job_key, Futures fs ) {
    if( _key != null ) {
      Log.debug("lock-then-delete "+_key+" by job "+job_key);
      new PriorWriteLock(job_key).invoke(_key);
    }
    return remove(fs);
  }

  // Obtain the write-lock on _key, which may already exist, using the current 'this'.
  private final class PriorWriteLock extends TAtomic<Lockable> {
    private final Key<Job> _job_key; // Job doing the locking
    private Lockable _old;              // Return the old thing, for deleting later
    private PriorWriteLock( Key<Job> job_key ) { _job_key = job_key; }
    @Override public Lockable atomic(Lockable old) {
      _old = old;
      if( old != null ) {       // Prior Lockable exists?
        assert !old.is_wlocked(_job_key) : "Key "+_key+" already locked (or deleted); lks="+Arrays.toString(old._lockers); // No double locking by same job
        if( old.is_locked(_job_key) ) // read-locked by self? (double-write-lock checked above)
          old.set_unlocked(old._lockers,_job_key); // Remove read-lock; will atomically upgrade to write-lock
        if( !old.is_unlocked() ) // Blocking for some other Job to finish???
          throw new IllegalArgumentException(old.getClass()+" "+_key+" is already in use.  Unable to use it now.  Consider using a different destination name.");
      }
      // Update & set the new value
      set_write_lock(_job_key);
      return Lockable.this;
    }
  }

  // -----------
  /** Atomically get a read-lock on Key k, preventing future deletes or updates */
  public static void read_lock( Key k, Job job ) { read_lock(k,job._key); }
  public static void read_lock( Key k, Key<Job> job_key ) {
    Value val = DKV.get(k);
    if( val.isLockable() )
      ((Lockable)val.get()).read_lock(job_key); // Lockable being locked
  }
  /** Atomically get a read-lock on this, preventing future deletes or updates */
  public void read_lock( Key<Job> job_key ) { 
    if( _key != null ) {
      Log.debug("shared-read-lock "+_key+" by job "+job_key);
      new ReadLock(job_key).invoke(_key); 
    }
  }

  // Obtain read-lock
  static private class ReadLock extends TAtomic<Lockable> {
    final Key<Job> _job_key;    // Job doing the unlocking
    ReadLock( Key<Job> job_key ) { _job_key = job_key; }
    @Override public Lockable atomic(Lockable old) {
      if( old == null ) throw new IllegalArgumentException("Nothing to lock!");
      if( old.is_wlocked() )
        throw new IllegalArgumentException( old.getClass()+" "+_key+" is being created;  Unable to read it now.");
      old.set_read_lock(_job_key);
      return old;
    }
  }

  // -----------
  /** Atomically set a new version of self, without changing the locking.  Typically used
   *  to upgrade a write-locked Model to a newer version with more training iterations. */
  public T update( ) { return update((Key<Job>)null); }
  public T update( Job job ) { return (T)update(job._key); }
  public T update( Key<Job> job_key ) { 
    Log.debug("update write-locked "+_key+" by job "+job_key);
    new Update(job_key).invoke(_key); 
    return (T)this;             // Flow-coding
  }

  // Freshen 'this' and leave locked
  private class Update extends TAtomic<Lockable> {
    final Key<Job> _job_key;    // Job doing the unlocking
    Update( Key<Job> job_key ) { _job_key = job_key; }
    @Override public Lockable atomic(Lockable old) {
      assert old != null : "Cannot update - Lockable is null!";
      assert old.is_wlocked() : "Cannot update - Lockable is not write-locked!";
      _lockers = old._lockers;  // Keep lock state
      return Lockable.this;     // Freshen this
    }
  }

  // -----------
  /** Atomically set a new version of self and unlock. */
  public T unlock( ) { return unlock(null,true); }
  public T unlock( Job job ) { return (T)unlock(job._key,true); }
  public T unlock( Key<Job> job_key ) { return unlock(job_key,true); }
  public T unlock( Key<Job> job_key, boolean exact ) {
    if( _key != null ) {
      Log.debug("unlock "+_key+" by job "+job_key);
      new Unlock(job_key,exact).invoke(_key);
    }
    return (T)this;
  }

  // Freshen 'this' and unlock
  private class Unlock extends TAtomic<Lockable> {
    final Key<Job> _job_key;    // Job doing the unlocking
    // Most uses want exact semantics: assert if not locked when unlocking.
    // Crash-cleanup code sometimes has a hard time knowing if the crash was
    // before locking or after, so allow a looser version which quietly unlocks
    // in all situations.
    final boolean _exact;       // Complain if not locked when unlocking
    Unlock( Key<Job> job_key, boolean exact ) { _job_key = job_key; _exact = exact;}
    @Override public Lockable atomic(Lockable old) {
      assert !_exact || old != null : "Trying to unlock null!";
      assert !_exact || old.is_locked(_job_key) : "Can't unlock: Not locked!";
      if( _exact || old.is_locked(_job_key) )
        set_unlocked(old._lockers,_job_key);
      return Lockable.this;
    }
  }

  // -----------
  // Accessors for locking state.  Minimal self-checking; primitive results
  private boolean is_locked(Key<Job> job_key) { 
    if( _lockers==null ) return false;
    for( int i=(_lockers.length==1?0:1); i<_lockers.length; i++ ) {
      Key k = _lockers[i];
      if( job_key==k || (job_key != null && k != null && job_key.equals(k)) ) return true;
    }
    return false;
  }
  private boolean is_wlocked() { return _lockers!=null && _lockers.length==1; }
  private boolean is_wlocked(Key<Job> job_key) { return is_wlocked() && (_lockers[0] == job_key || (_lockers[0] != null && _lockers[0].equals(job_key))); }
  private boolean is_unlocked() { return _lockers== null; }
  private void set_write_lock( Key<Job> job_key ) { 
    _lockers=new Key[]{job_key}; 
    assert is_locked(job_key);
  }
  private void set_read_lock(Key<Job> job_key) {
    assert !is_wlocked();       // not write locked
    _lockers = _lockers == null ? new Key[2] : Arrays.copyOf(_lockers,_lockers.length+1);
    _lockers[_lockers.length-1] = job_key;
    assert is_locked(job_key);
  }
  private void set_unlocked(Key lks[], Key<Job> job_key) {
    if( lks.length==1 ) {       // Is write-locked?
      assert job_key==lks[0] || job_key.equals(lks[0]);
      _lockers = null;           // Then unlocked
    } else if( lks.length==2 ) { // One reader
      assert lks[0]==null;       // Not write-locked
      assert lks[1]==job_key || (job_key != null && job_key.equals(lks[1]));
      _lockers = null;          // So unlocked
    } else {                    // Else one of many readers
      assert lks.length>2;
      _lockers = Arrays.copyOf(lks,lks.length-1);
      for( int i=1; i<lks.length; i++ )
        if( (job_key != null && job_key.equals(lks[i])) || (job_key == null && lks[i] == null) ) {
          if( i < _lockers.length ) _lockers[i] = lks[lks.length-1];
          break;
        }
    }
  }

  /** Force-unlock (break a lock) all lockers; useful in some debug situations. */
  public void unlock_all() {
    if( _key != null )
      for (Key k : _lockers) new UnlockSafe(k).invoke(_key);
  }

  private class UnlockSafe extends TAtomic<Lockable> {
    final Key<Job> _job_key;    // potential job doing the unlocking
    UnlockSafe( Key job_key ) { _job_key = job_key; }
    @Override public Lockable atomic(Lockable old) {
      if (old.is_locked(_job_key))
        set_unlocked(old._lockers,_job_key);
      return Lockable.this;
    }
  }
}
