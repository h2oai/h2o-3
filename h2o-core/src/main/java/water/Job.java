package water;

import jsr166y.CountedCompleter;
import java.util.Arrays;
import water.H2O.H2OCountedCompleter;

/** Jobs are used to do minimal tracking of long-lifetime user actions,
 *  including progress-bar updates and the ability to review in progress or
 *  completed Jobs, and cancel currently running Jobs.
 *  <p>
 *  Jobs are {@link Keyed}, because they need to Key to control e.g. atomic updates.
 *  <p>
 *  Jobs are generic on Keyed, because their primary result is a Keyed result -
 *  which is Not a Job.  Obvious examples are Frames (from running Parse or
 *  CreateFrame jobs), or Models (from running ModelBuilder jobs).
 *  <p>
 *  Long running tasks will has-a Job, not is-a Job.
 */
public final class Job<T extends Keyed> extends Keyed<Job> {
  /** Result Key */
  public final Key<T> _result;

  /** User description */
  public final String _description;

  /** Create a Job
   *  @param key  Key of the final result
   *  @param desc String description
   *  @param work Amount of work to-do, for updating progress bar
   */
  public Job(Key<T> key, String desc, long work) { 
    super(defaultJobKey());     // Passing in a brand new Job key
    _result = key;              // Result (destination?) key
    _description = desc; 
    _work = work;
  }

  // Job Keys are pinned to this node (i.e., the node that invoked the
  // computation), because it should be almost always updated locally
  private static Key<Job> defaultJobKey() { return Key.make((byte) 0, Key.JOB, false, H2O.SELF); }


  /** Job start_time and end_time using Sys.CTM */
  private long _start_time;     // Job started, or 0 if not running
  private long   _end_time;     // Job end time, or 0 if not ended

  // Simple state accessors
  public long start_time()   { return _start_time; }
  public boolean isCreated() { return _start_time == 0; }
  public boolean isRunning() { return _start_time != 0 && _end_time == 0; }
  public boolean isStopped() { return   _end_time != 0; }
  // Slightly more involved state accessors
  public boolean isCanceling(){return _cancel_requested && isRunning(); }
  public boolean isCanceled(){ return _cancel_requested && isStopped(); }
  public boolean isCrashing(){ return hasEx() && isRunning(); }
  public boolean isCrashed (){ return hasEx() && isStopped(); }


  /** Current runtime; zero if not started. */
  public long msec() {
    if( isCreated() ) return 0; // Created, not running
    if( isRunning() ) return System.currentTimeMillis() - _start_time;
    return _end_time - _start_time; // Stopped
  }

  /** Jobs may be requested to Cancel.  Each individual job will respond to
   *  this on a best-effort basis, and make some time to cancel.  Cancellation
   *  really means "the Job stops", but is not an indication of any kind of
   *  error or fail.  Perhaps the user simply got bored.  Because it takes time
   *  to stop, a Job may be both in state RUNNING and cancel_requested, and may
   *  later switch to STOPPED and cancel_requested.  Also, an exception may be
   *  posted. */
  private volatile boolean _cancel_requested; // monotonic change from false to true
  public boolean cancel_requested() { return _cancel_requested; }
  public void cancel() { 
    if( !_cancel_requested )    // fast path cutout
      new JAtomic() {
        @Override boolean abort(Job job) { return job._cancel_requested; }
        @Override void update(Job job) { job._cancel_requested = true; }
      };
  }

  /** Any exception thrown by this Job, or null if none.  Note that while
   *  setting an exception generally triggers stopping a Job, stopping
   *  takes time, so the Job might still be running with an exception
   *  posted. */
  private Throwable _ex;
  public Throwable ex() { return _ex; }
  public boolean hasEx() { return _ex != null; }
  /** Set an exception into this Job, marking it as failing and setting the
   *  _cancel_requested flag.  Only the first exception (of possibly many) is
   *  kept. */
  public void setEx(Throwable ex, Class thrower_clz) {
    if( _ex == null ) {
      final DException dex = new DException(ex, thrower_clz);
      new JAtomic() {
        @Override boolean abort(Job job) { return job._ex != null; } // One-shot update; keep first exception
        @Override void update(Job job) { job._ex = dex.toEx(); job._cancel_requested = true; }
      };
    }
  }


  /** Total expected work. */
  public final long _work;
  private long _worked;         // Work accomplished; between 0 and _work
  private String _msg;          // Progress string

  /** Returns a float from 0 to 1 representing progress.  Polled periodically.
   *  Can default to returning e.g. 0 always.  */
  public float progress() { update_from_remote(); return _work==0 ? 0f : (float)_worked/_work; }
  /** Returns last progress message. */
  public String progress_msg() { update_from_remote(); return _msg; }

  /** Report new work done for this job */
  public final void update( final long newworked, final String msg) {
    if( newworked > 0 || (msg != null && !msg.equals(_msg)) ) {
      new JAtomic() {
        @Override boolean abort(Job job) { return newworked==0 && ((msg==null && _msg==null) || (msg != null && msg.equals(job._msg))); }
        @Override void update(Job old) { old._worked += newworked; old._msg = msg; }
      };
    }
  }
  public final  void update(final long newworked) { update(newworked,(String)null); }
  public static void update(final long newworked, Key<Job> jobkey) { update(newworked, null, jobkey); }
  public static void update(final long newworked, String msg, Key<Job> jobkey) { jobkey.get().update(newworked, msg); }

  // --------------
  /** A system key for global list of Job keys. */
  public static final Key<Job> LIST = Key.make(" JobList", (byte) 0, Key.BUILT_IN_KEY, false);
  private static class JobList extends Keyed {
    Key<Job>[] _jobs;
    JobList() { super(LIST); _jobs = new Key[0]; }
    private JobList(Key<Job>[]jobs) { super(LIST); _jobs = jobs; }
  }

  /** The list of all Jobs, past and present.
   *  @return The list of all Jobs, past and present */
  public static Job[] jobs() {
    final Value val = DKV.get(LIST);
    if( val==null ) return new Job[0];
    JobList jl = val.get();
    Job[] jobs = new Job[jl._jobs.length];
    int j=0;
    for( int i=0; i<jl._jobs.length; i++ ) {
      final Value job = DKV.get(jl._jobs[i]);
      if( job != null ) jobs[j++] = job.get();
    }
    if( j==jobs.length ) return jobs; // All jobs still exist
    jobs = Arrays.copyOf(jobs,j);     // Shrink out removed
    Key keys[] = new Key[j];
    for( int i=0; i<j; i++ ) keys[i] = jobs[i]._key;
    // One-shot throw-away attempt at remove dead jobs from the jobs list
    DKV.DputIfMatch(LIST,new Value(LIST,new JobList(keys)),val,new Futures());
    return jobs;
  }

  /** Start this task based on given top-level fork-join task representing job computation.
   *  @param fjtask top-level job computation task.
   *  @return this job in {@code isRunning()} state
   *
   *  @see H2OCountedCompleter
   */
  public Job<T> start(final H2OCountedCompleter fjtask) {
    // Job does not exist in any DKV, and so does not have any global
    // visibility (yet).
    assert !new AssertNoKey(_key).doAllNodes()._found;
    assert isCreated() && !isRunning() && !isStopped();
    assert fjtask != null : "Starting a job with null working task is not permitted!";
    assert fjtask.getCompleter() == null : "Cannot have a completer; this must be a top-level task";
    // Make a wrapper class that only *starts* when the fjtask completes -
    // especially it only starts even when fjt completes exceptionally... thus
    // the fjtask onExceptionalCompletion code runs completely before this
    // empty task starts - providing a simple barrier.  Threads blocking on the
    // job will block on the "barrier" task, which will block until the fjtask
    // runs the onCompletion or onExceptionCompletion code.
    _barrier = new Barrier();
    fjtask.setCompleter(_barrier);
    _fjtask = fjtask;

    // Change state from created to running
    _start_time = System.currentTimeMillis();
    assert !isCreated() && isRunning() && !isStopped();

    // Save the full state of the job, first time ever making it public
    DKV.put(this);              // Announce in DKV

    // Update job list
    final Key jobkey = _key;
    new TAtomic<JobList>() {
      @Override public JobList atomic(JobList old) {
        if( old == null ) old = new JobList();
        Key[] jobs = old._jobs;
        old._jobs = Arrays.copyOf(jobs, jobs.length + 1);
        old._jobs[jobs.length] = jobkey;
        return old;
      }
    }.invoke(LIST);
    // Fire off the FJTASK
    H2O.submitTask(fjtask);
    return this;
  }
  transient private H2OCountedCompleter _fjtask; // Top-level task to do
  transient private Barrier _barrier;            // Top-level task to block on

  // Handy for assertion
  private static class AssertNoKey extends MRTask<AssertNoKey> {
    private final Key<Job> _key;
    boolean _found;
    AssertNoKey( Key<Job> key ) { _key = key; }
    @Override public void setupLocal() { _found = H2O.containsKey(_key); }
    @Override public void reduce( AssertNoKey ank ) { _found |= ank._found; }
  }

  // A simple barrier.  Threads blocking on the job will block on this
  // "barrier" task, which will block until the fjtask runs the onCompletion or
  // onExceptionCompletion code.
  private class Barrier extends H2OCountedCompleter<Barrier> {
    @Override public void compute2() { }
    @Override public void onCompletion(CountedCompleter caller) {
      new JAtomic(){
        @Override boolean abort(Job job) { return false; }
        @Override public void update(Job old) {
          assert old._end_time==0 : "onComp should be called once at most, and never if onExComp is called";
          old._end_time = System.currentTimeMillis();
        }
      };
      update_from_remote();
      _fjtask = null;           // Free for GC
      _barrier = null;          // Free for GC
    }
    @Override public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller) {
      final DException dex = new DException(ex,caller.getClass());
      new JAtomic() {
        @Override boolean abort(Job job) { return job._ex != null && job._end_time!=0; } // Already stopped & exception'd
        @Override void update(Job old) {
          if( old._ex == null ) old._ex = dex.toEx(); // Keep first exception ever
          old._cancel_requested = true; // Since exception set, also set cancel
          if( old._end_time == 0 )      // Keep first end-time
            old._end_time = System.currentTimeMillis();
        }
      };
      if( getCompleter() == null ) { // nobody else to handle this exception, so print it out
        System.err.println("barrier onExCompletion for "+_fjtask);
        ex.printStackTrace();
      }
      _fjtask = null;           // Free for GC
      _barrier = null;          // Free for GC
      return true;
    }
  }

  /** Blocks until the Job completes  */
  public T get() {
    Barrier bar = _barrier;
    if( bar != null )           // Barrier may be null if task already completed
      bar.join(); // Block on the *barrier* task, which blocks until the fjtask on*Completion code runs completely
    assert isStopped();
    return _result.get();
  }

  // --------------
  // Atomic State Updaters.  Atomically change state on the home node.  They
  // also update the *this* object from the freshest remote state, meaning the
  // *this* object changes after these calls.  
  // NO OTHER CHANGES HAPPEN TO JOB FIELDS.

  private abstract class JAtomic extends TAtomic<Job> {
    JAtomic() { invoke(Job.this._key); update_from_remote(); }
    abstract boolean abort(Job job);
    abstract void update(Job job);
    @Override public Job atomic(Job job) {
      assert job != null : "Race on creation";
      if( abort(job) ) return null;
      update(job);
      return job;
    }
  }

  // Update the *this* object from a remote object.
  private void update_from_remote( ) {
    Job remote_job = DKV.getGet(_key); // Watch for changes in the DKV
    if( this==remote_job ) return; // Trivial!
    synchronized(this) { copyOver(remote_job); }
  }
}
