package water;

import java.util.Arrays;
import water.util.Log;

/** Lockable Keys - locked during long running jobs, to prevent overwriting
 *  in-use keys.  e.g. model-building: expected to read-lock input ValueArray
 *  and Frames, and write-lock the output Model.  Parser should write-lock the
 *  output VA/Frame, to guard against double-parsing.
 *  
 *  Supports:
 *    lock-and-delete-old-and-update (for new Keys)
 *    lock-and-delete                (for removing old Keys)
 *    unlock
 *  
 *  @author <a href="mailto:cliffc@0xdata.com"></a>
 *  @version 1.0
 */
public abstract class Lockable<T extends Lockable<T>> extends Keyed {
  /** Write-locker job  is  in _jobs[0 ].  Can be null locker.
   *  Read -locker jobs are in _jobs[1+].
   *  Unlocked has _jobs equal to null.
   *  Only 1 situation will be true at a time; atomically updated.
   *  Transient, because this data is only valid on the master node.
   */
  //@API(help="Jobs locking this key")
  public transient Key _lockers[];

  public Lockable( Key key ) { super(key); }

  // Will fail if locked by anybody.
  public void delete( ) { delete(null,0.0f); }
  // Will fail if locked by anybody other than 'job_key'
  public void delete( Key job_key, float dummy ) { 
    if( _key != null ) {
      Log.debug("lock-then-delete "+_key+" by job "+job_key);
      new PriorWriteLock(job_key).invoke(_key);
    }
    remove(new Futures()).blockForPending();
  }

  // Obtain the write-lock on _key, which may already exist, using the current 'this'.
  private class PriorWriteLock extends TAtomic<Lockable> {
    private final Key _job_key;         // Job doing the locking
    private Lockable _old;              // Return the old thing, for deleting later
    private PriorWriteLock( Key job_key ) { _job_key = job_key; }
    @Override public Lockable atomic(Lockable old) {
      _old = old;
      if( old != null ) {       // Prior Lockable exists?
        assert !old.is_wlocked(_job_key) : "Key "+_key+" already locked; lks="+Arrays.toString(old._lockers); // No double locking by same job
        if( old.is_locked(_job_key) ) // read-locked by self? (double-write-lock checked above)
          old.set_unlocked(old._lockers,_job_key); // Remove read-lock; will atomically upgrade to write-lock
        if( !old.is_unlocked() ) // Blocking for some other Job to finish???
          throw new IllegalArgumentException(old.errStr()+" "+_key+" is already in use.  Unable to use it now.  Consider using a different destination name.");
        assert old.is_unlocked() : "Not unlocked when locking "+Arrays.toString(old._lockers)+" for "+_job_key;
      }
      // Update & set the new value
      set_write_lock(_job_key);
      return Lockable.this;
    }
  }

  // -----------
  // Accessers for locking state.  Minimal self-checking; primitive results.
  private boolean is_locked(Key job_key) { 
    if( _lockers==null ) return false;
    for( int i=(_lockers.length==1?0:1); i<_lockers.length; i++ ) {
      Key k = _lockers[i];
      if( job_key==k || (job_key != null && k != null && job_key.equals(k)) ) return true;
    }
    return false;
  }
  private boolean is_wlocked() { return _lockers!=null && _lockers.length==1; }
  private boolean is_wlocked(Key job_key) { return is_wlocked() && (_lockers[0] == job_key || _lockers[0] != null && _lockers[0].equals(job_key)); }
  private boolean is_unlocked() { return _lockers== null; }
  private void set_write_lock( Key job_key ) { 
    _lockers=new Key[]{job_key}; 
    assert is_locked(job_key);
  }
  private void set_read_lock(Key job_key) {
    assert !is_locked(job_key); // no double locking
    assert !is_wlocked();       // not write locked
    _lockers = _lockers == null ? new Key[2] : Arrays.copyOf(_lockers,_lockers.length+1);
    _lockers[_lockers.length-1] = job_key;
    assert is_locked(job_key);
  }
  private void set_unlocked(Key lks[], Key job_key) {
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
      int j=1;                  // Skip the initial null slot
      for( int i=1; i<lks.length; i++ )
        if(job_key != null && !job_key.equals(lks[i]) || (job_key == null && lks[i] != null)){
            _lockers[j++] = lks[i];
        }
      assert j==lks.length-1;   // Was locked exactly once
    }
    assert !is_locked(job_key);
  }

  // Pretty string when locking fails
  protected abstract String errStr();
}
