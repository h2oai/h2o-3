package water;

import jsr166y.CountedCompleter;
import java.util.Arrays;
import water.H2O.H2OCountedCompleter;
import water.util.Log;
import water.util.StringUtils;

/** Jobs are used to do minimal tracking of long-lifetime user actions,
 *  including progress-bar updates and the ability to review in progress or
 *  completed Jobs, and cancel currently running Jobs.
 *  <p>
 *  Jobs are {@link Keyed}, because they need to Key to control e.g. atomic updates.
 *  <p>
 *  Long running tasks will has-a Job, not is-a Job.
 */
public final class Job extends Keyed<Job> {
  /** User description */
  public final String _description;

  /** Possible job states.  These are ORDERED; state levels can increased but never decrease */
  private volatile byte _state;
  private final byte CREATED = 0; // Job was created
  private final byte RUNNING = 1; // Job is running
  private final byte STOPPED = 2; // Job was running, is now stopped.
  // Simple accessors
  public boolean isCreated() { return _state == CREATED; }
  public boolean isRunning() { return _state == RUNNING; }
  public boolean isStopped() { return _state == STOPPED; }
  private void state(byte ns) { if( _state != ns ) atomic_state(ns); }
  // Update Job _state monotonically
  private void atomic_state( byte ns ) {
    new TAtomic<Job>() {
      @Override public Job atomic(Job old) {
        assert old != null : "Race on job creation";
        if( ns <= old._state ) return null; // No change
        old._state = ns;
        return old;
      }
    }.invoke(_key);
    update_from_remote();
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
  public void cancel() { if( !_cancel_requested ) atomic_cancel(); }
  // Update Job _cancel monotonically
  private void atomic_cancel( ) {
    new TAtomic<Job>() {
      @Override public Job atomic(Job old) {
        assert old != null : "Race on job creation";
        if( old._cancel ) return null; // No change
        old._cancel = true;
        return old;
      }
    }.invoke(_key);
    update_from_remote();
  }

  /** Any exception thrown by this Job, or null if none.  Note that while
   *  setting an exception generally triggers stopping a Job, stopping
   *  takes time, so the Job might still be running with an exception
   *  posted. */
  private Throwable _ex;
  public Throwable ex() { return _ex; }
  public boolean hasCrashed() { return _exception != null; }
  public setEx(Throwable ex) { if( _ex == null ) atomic_ex(ex); } 
  // Update Job _ex monotonically: set only once
  private void atomic_ex( Throwable ex ) {
    new TAtomic<Job>() {
      @Override public Job atomic(Job old) {
        assert old != null : "Race on job creation";
        if( old._ex != null ) return null; // One-shot update
        old._ex = ex;
        return old;
      }
    }.invoke(_key);
    update_from_remote();
  }


  /** Job start_time using Sys.CTM */
  private long _start_time;     // Job started
  public long start_time() { return _start_time; }
  private long   _end_time;     // Job end time, or 0 if not ended
  /** Current runtime; zero if not started.  End time is computed as start_time()+msec() */
  public final long msec() {
    switch( _state ) {
    case CREATED: return 0;
    case RUNNING: return System.currentTimeMillis() - _start_time;
    default:      return _end_time                  - _start_time;
    }
  }

  /** Total expected work. */
  public final long _work;
  private long _worked;         // Work accomplished; between 0 and _work
  private String _progress;     // Progress string

  /** Returns a float from 0 to 1 representing progress.  Polled periodically.
   *  Can default to returning e.g. 0 always.  */
  public float progress() { update_from_remote(); return _work==0 ? 0f : (float)_worked/_work; }
  /** Returns last progress message. */
  public String progress_msg() { update_from_remote(); return _progress; }

  /** Report new work done for this job */
  public final void update(final long newworked, String msg) { 
    if( newworked > 0 || (msg!=null && _msg==null) || !msg.equals(_msg) )
      atomic_progress(newworked, msg);
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
    @Override protected long checksum_impl() { throw H2O.fail("Joblist checksum does not exist by definition"); }
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

  /** Create a Job
   *  @param desc String description
   */
  public Job(String desc, long work) { this(defaultJobKey(),desc,work); }
  private Job(Key<Job> key, String desc) { super(key); _description = desc; _work = work;}

  // Job Keys are pinned to this node (i.e., the node that invoked the
  // computation), because it should be almost always updated locally
  private static Key<Job> defaultJobKey() { return Key.make((byte) 0, Key.JOB, false, H2O.SELF); }



  /** Start this task based on given top-level fork-join task representing job computation.
   *  @param fjtask top-level job computation task.
   *  @return this job in {@link JobState#RUNNING} state
   *
   *  @see JobState
   *  @see H2OCountedCompleter
   */
  protected Job start(final H2OCountedCompleter fjtask) {
    throw H2O.unimpl();
    // TODO: ASSERT Job in any DKV (remote or otherwise), and so only has global visibility when RUNNING?
    // TODO: Race with changing state to RUNNING and being on the JOBS list.
    //if (work >= 0)
    //  DKV.put(_progressKey = createProgressKey(), new Progress(work));
    // protected Key createProgressKey() { return Key.make(); }
    //assert _state == JobState.CREATED : "Trying to run job which was already run?";
    //assert fjtask != null : "Starting a job with null working task is not permitted!";
    //assert fjtask.getCompleter() == null : "Cannot have a completer; this must be a top-level task";
    //_fjtask = fjtask;
    //
    //// Make a wrapper class that only *starts* when the fjtask completes -
    //// especially it only starts even when fjt completes exceptionally... thus
    //// the fjtask onExceptionalCompletion code runs completely before this
    //// empty task starts - providing a simple barrier.  Threads blocking on the
    //// job will block on the "barrier" task, which will block until the fjtask
    //// runs the onCompletion or onExceptionCompletion code.
    //_barrier = new H2OCountedCompleter() {
    //    @Override public void compute2() { }
    //    @Override public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller) {
    //      if( getCompleter() == null ) { // nobody else to handle this exception, so print it out
    //        System.err.println("barrier onExCompletion for "+fjtask);
    //        ex.printStackTrace();
    //        Job.this.failed(ex);
    //      }
    //      return true;
    //    }
    //  };
    //fjtask.setCompleter(_barrier);
    //if (restartTimer) _start_time = System.currentTimeMillis();
    //_state      = JobState.RUNNING;
    //// Save the full state of the job
    //DKV.put(_key, this);
    //// Update job list
    //final Key jobkey = _key;
    //new TAtomic<JobList>() {
    //  @Override public JobList atomic(JobList old) {
    //    if( old == null ) old = new JobList();
    //    Key[] jobs = old._jobs;
    //    old._jobs = Arrays.copyOf(jobs, jobs.length + 1);
    //    old._jobs[jobs.length] = jobkey;
    //    return old;
    //  }
    //}.invoke(LIST);
    //H2O.submitTask(fjtask);
    //return this;
  }

  transient H2OCountedCompleter _fjtask; // Top-level task to do
  transient H2OCountedCompleter _barrier;// Top-level task you can block on

  /** Blocks and get result of this job.
   * <p>
   * This call blocks on working task which was passed via {@link #start}
   * method and returns the result which is fetched from DKV based on job
   * destination key.
   * </p>
   * @return result of this job fetched from DKV by destination key.
   * @see #start
   * @see DKV
   */
  public T get() {
    assert _fjtask != null : "Cannot block on missing F/J task";
    _barrier.join(); // Block on the *barrier* task, which blocks until the fjtask on*Completion code runs completely
    assert !isRunning() : "Job state should not be running, but it is " + _state;
    return _dest.get();
  }

  // --------------
  // Atomic State Updaters.  Atomically change state on the home node.  They
  // also update the *this* object from the freshest remote state, meaning the
  // *this* object changes after these calls.  
  // NO OTHER CHANGES HAPPEN TO JOB FIELDS.
  
  // Update the *this* object from a remote object.
  private void update_from_remote( Job remote_job ) {
    if( this==remote_job ) return; // Trivial!
    synchronized(this) {
      icer().copyOver(this,remote_job);
    }
  }
  // Support for progress updates: watch for changes in the DKV.
  private void update_from_remote( ) { update_from_remote(DKV.getGet(_key)); }
}
