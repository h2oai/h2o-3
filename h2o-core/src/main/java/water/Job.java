package water;

import jsr166y.CountedCompleter;
import water.H2O.H2OCountedCompleter;
import water.api.schemas3.KeyV3;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.Arrays;

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

  public enum JobStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    STOPPED,
    FAILED;

    public static String[] domain() {
      return Arrays.stream(values()).map(Object::toString).toArray(String[]::new);
    }
  }

  /** Result Key */
  public final Key<T> _result;
  public final int _typeid;

  /** User description */
  public final String _description;

  // whether the _result key is ready for view
  private boolean _ready_for_view = true;

  private String [] _warns;

  public void warn(String warn) {
    Log.warn(warn);
    setWarnings(ArrayUtils.append(warns(),warn));
  }
  public void setWarnings(final String [] warns){
    new JAtomic() {
      @Override boolean abort(Job job) { return job._stop_requested; }
      @Override void update(Job job) { job._warns = warns; }
    }.apply(this);
  }

  /** Create a Job
   *  @param key  Key of the final result
   *  @param clz_of_T String class of the Keyed result
   *  @param desc String description   */
  public Job(Key<T> key, String clz_of_T, String desc) {
    super(defaultJobKey());     // Passing in a brand new Job key
    assert key==null || clz_of_T!=null;
    _result = key;              // Result (destination?) key
    _typeid = clz_of_T==null ? 0 : TypeMap.getIcedId(clz_of_T);
    _description = desc; 
  }

  /** Create a Job when a warning already exists due to bad model_id
   *  @param key  Key of the final result
   *  @param clz_of_T String class of the Keyed result
   *  @param desc String description
   *  @param warningStr String contains a warning on model_id*/
  public Job(Key<T> key, String clz_of_T, String desc, String warningStr) {
    this(key, clz_of_T, desc);
    if (warningStr != null) {
      _warns = new String[] {warningStr};
    }

  }

  // Job Keys are pinned to this node (i.e., the node that invoked the
  // computation), because it should be almost always updated locally
  private static Key<Job> defaultJobKey() { return Key.make((byte) 0, Key.JOB, false, H2O.SELF); }


  /** Job start_time and end_time using Sys.CTM */
  private long _start_time;     // Job started, or 0 if not running
  private long   _end_time;     // Job end time, or 0 if not ended

  // Simple internal state accessors
  private boolean created() { return _start_time == 0; }
  private boolean running() { return _start_time != 0 && _end_time == 0; }
  private boolean stopped() { return   _end_time != 0; }

  // Simple state accessors; public ones do a DKV update check
  public long start_time()   { update_from_remote(); assert !created(); return _start_time; }
  public long   end_time()   { update_from_remote(); assert  stopped(); return   _end_time; }
  public boolean isRunning() {
    update_from_remote();
    return  running();
  }
  public boolean isStopped() { update_from_remote(); return  stopped(); }
  // Slightly more involved state accessors
  public boolean isStopping(){ return isRunning() && _stop_requested; }
  public boolean isDone()    { return isStopped() && _ex == null; }
  public boolean isCrashing(){ return isRunning() && _ex != null; }
  public boolean isCrashed (){ return isStopped() && _ex != null; }

  public JobStatus getStatus() {
    if (isCrashed())
      return JobStatus.FAILED;
    else if (isStopped())
      if (stop_requested())
        return JobStatus.STOPPED;
      else
        return JobStatus.SUCCEEDED;
    else if (isRunning())
      return JobStatus.RUNNING;
    else
      return JobStatus.PENDING;
  }

  /** Current runtime; zero if not started. */
  public long msec() {
    update_from_remote();
    if( created() ) return 0;   // Created, not running
    if( running() ) return System.currentTimeMillis() - _start_time;
    return _end_time - _start_time; // Stopped
  }

  public boolean readyForView() { return _ready_for_view; }
  public void setReadyForView(boolean ready) { _ready_for_view = ready; }

  /** Jobs may be requested to Stop.  Each individual job will respond to this
   *  on a best-effort basis, and make some time to stop.  Stop really means
   *  "the Job stops", but is not an indication of any kind of error or fail.
   *  Perhaps the user simply got bored.  Because it takes time to stop, a Job
   *  may be both in state isRunning and stop_requested, and may later switch
   *  to isStopped and stop_requested.  Also, an exception may be posted. */
  private volatile boolean _stop_requested; // monotonic change from false to true
  public boolean stop_requested() { update_from_remote(); return _stop_requested; }
  public void stop() { 
    if( !_stop_requested )      // fast path cutout
      new JAtomic() {
        @Override boolean abort(Job job) { return job._stop_requested; }
        @Override void update(Job job) {
          job._stop_requested = true;
          Log.debug("Job "+job._description+" requested to stop");
        }
      }.apply(this);
  }

  /** Any exception thrown by this Job, or null if none.  Note that while
   *  setting an exception generally triggers stopping a Job, stopping
   *  takes time, so the Job might still be running with an exception
   *  posted. */
  private byte [] _ex;
  public Throwable ex() {
    if(_ex == null) return null;
    return (Throwable)AutoBuffer.javaSerializeReadPojo(_ex);
  }

  /** Total expected work. */
  public long _work;            // Total work to-do
  public long _max_runtime_msecs;
  private long _worked;         // Work accomplished; between 0 and _work
  private String _msg;          // Progress string

  /** Returns a float from 0 to 1 representing progress.  Polled periodically.
   *  Can default to returning e.g. 0 always.  */
  public float progress() { update_from_remote();
    float regularProgress = _work==0 ? 0f : Math.min(1,(float)_worked/_work);
    if (_max_runtime_msecs>0) return Math.min(1,Math.max(regularProgress, (float)msec()/_max_runtime_msecs));
    return regularProgress;
  }
  /** Returns last progress message. */
  public String progress_msg() { update_from_remote(); return _msg; }

  /** Report new work done for this job */
  public final void update( final long newworked, final String msg) {
    if( newworked > 0 || (msg != null && !msg.equals(_msg)) ) {
      new JAtomic() {
        @Override boolean abort(Job job) { return newworked==0 && ((msg==null && _msg==null) || (msg != null && msg.equals(job._msg))); }
        @Override void update(Job old) { old._worked += newworked; old._msg = msg; }
      }.apply(this);
    }
  }
  public final  void update(final long newworked) { update(newworked,(String)null); }
  public static void update(final long newworked, Key<Job> jobkey) { update(newworked, null, jobkey); }
  public static void update(final long newworked, String msg, Key<Job> jobkey) { jobkey.get().update(newworked, msg); }

  // --------------
  /** A system key for global list of Job keys. */
  public static final Key<Job> LIST = Key.make(" JobList", (byte) 0, Key.BUILT_IN_KEY, false);

  public String[] warns() {
    update_from_remote();
    return _warns;
  }

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

  public static final long WORK_UNKNOWN = 0L;

  public final long getWork() {
    update_from_remote();
    return _work;
  }

  /** Set the amount of work for this job - can only be called if job was started without work specification */
  public final void setWork(final long work) {
    if (getWork() != WORK_UNKNOWN) {
      throw new IllegalStateException("Cannot set work amount if it was already previously specified");
    }
    new JAtomic() {
      @Override boolean abort(Job job) { return false; }
      @Override void update(Job old) { old._work = work; }
    }.apply(this);
  }

  public Job<T> start(final H2OCountedCompleter fjtask, long work, double max_runtime_secs) {
    _max_runtime_msecs = (long)(max_runtime_secs*1e3);
    return start(fjtask, work);
  }

  /** Start this task based on given top-level fork-join task representing job computation.
   *  @param fjtask top-level job computation task.
   *  @param work Amount of work to-do, for updating progress bar
   *  @return this job in {@code isRunning()} state
   *
   *  @see H2OCountedCompleter
   */
  public Job<T> start(final H2OCountedCompleter fjtask, long work) {
    // Job does not exist in any DKV, and so does not have any global
    // visibility (yet).
    assert !new AssertNoKey(_key).doAllNodes()._found;
    assert created() && !running() && !stopped();
    assert fjtask != null : "Starting a job with null working task is not permitted!";
    assert fjtask.getCompleter() == null : "Cannot have a completer; this must be a top-level task";

    // F/J rules: upon receiving an exception (the task's compute/compute2
    // throws an exception caugt by F/J), the task is marked as "completing
    // exceptionally" - it is marked "completed" before the onExComplete logic
    // runs.  It is then notified, and wait'ers wake up - before the
    // onExComplete runs; onExComplete runs on in another thread, so wait'ers
    // are racing with the onExComplete.  

    // We want wait'ers to *wait* until the task's onExComplete runs, AND Job's
    // onExComplete runs (marking the Job as stopped, with an error).  So we
    // add a few wrappers:

    // Make a wrapper class that only *starts* when the task completes -
    // especially it only starts even when task completes exceptionally... thus
    // the task onExceptionalCompletion code runs completely before Barrer1
    // starts - providing a simple barrier.  The Barrier1 onExComplete runs in
    // parallel with wait'ers on Barrier1.  When Barrier1 onExComplete itself
    // completes, Barrier2 is notified.

    // Barrier2 is an empty class, and vacuously runs in parallel with wait'ers
    // of Barrier2 - all callers of Job.get().
    _barrier = new Barrier2(); 
    fjtask.setCompleter(new Barrier1(_barrier));

    // These next steps must happen in-order:
    // 4 - cannot submitTask without being on job-list, lest all cores get
    // slammed but no user-visible record of why, so 4 after 3
    // 3 - cannot be on job-list without job in DKV, lest user (briefly) see it
    // on list but cannot click the link & find job, so 3 after 2
    // 2 - cannot be findable in DKV without job also being in running state
    // lest the finder be confused about the job state, so 2 after 1
    // 1 - set state to running

    // 1 - Change state from created to running
    _start_time = System.currentTimeMillis();
    assert !created() && running() && !stopped();
    _work = work;

    // 2 - Save the full state of the job, first time ever making it public
    DKV.put(this);              // Announce in DKV

    // 3 - Update job list
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
    // 4 - Fire off the FJTASK
    H2O.submitTask(fjtask);
    return this;
  }
  transient private Barrier2 _barrier; // Top-level task to block on

  // Handy for assertion
  private static class AssertNoKey extends MRTask<AssertNoKey> {
    private final Key<Job> _key;
    boolean _found;
    AssertNoKey( Key<Job> key ) { _key = key; }
    @Override public void setupLocal() { _found = H2O.containsKey(_key); }
    @Override public void reduce( AssertNoKey ank ) { _found |= ank._found; }
  }

  public static class JobCancelledException extends RuntimeException {}

  // A simple barrier.  Threads blocking on the job will block on this
  // "barrier" task, which will block until the fjtask runs the onCompletion or
  // onExceptionCompletion code.
  private class Barrier1 extends CountedCompleter {
    Barrier1(CountedCompleter cc) { super(cc,0); }
    @Override public void compute() { }
    @Override public void onCompletion(CountedCompleter caller) {
      new Barrier1OnCom().apply(Job.this);
      _barrier = null;          // Free for GC
    }
    @Override public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller) {
      if(Job.isCancelledException(ex)) {
        new Barrier1OnCom().apply(Job.this);
      } else {
        try {
          Log.err(ex);
        } catch (Throwable t) {/* do nothing */}
        new Barrier1OnExCom(ex).apply(Job.this);
      }
      _barrier = null;          // Free for GC
      return true;
    }
  }

  static public boolean isCancelledException(Throwable ex) {
    return (ex != null) && 
            (ex instanceof JobCancelledException || ex.getCause() != null && ex.getCause() instanceof JobCancelledException);
  }

  private static class Barrier1OnCom extends JAtomic {
    @Override boolean abort(Job job) { return false; }
    @Override public void update(Job old) {
      assert old._end_time==0 : "onComp should be called once at most, and never if onExComp is called";
      old._end_time = System.currentTimeMillis();
      if( old._worked < old._work ) old._worked = old._work;
      old._msg = old._stop_requested ? "Cancelled." : "Done.";
    }
  }
  private static class Barrier1OnExCom extends JAtomic {
    final byte[] _dex;
    Barrier1OnExCom(Throwable ex) {
      _dex = AutoBuffer.javaSerializeWritePojo(ex);
    }
    @Override boolean abort(Job job) { return job._ex != null && job._end_time!=0; } // Already stopped & exception'd
    @Override void update(Job job) {
      if( job._ex == null ) job._ex = _dex; // Keep first exception ever
      job._stop_requested = true; // Since exception set, also set stop
      if( job._end_time == 0 )    // Keep first end-time
        job._end_time = System.currentTimeMillis();
      job._msg = "Failed.";
    }
  }
  private class Barrier2 extends CountedCompleter {
    @Override public void compute() { }
  }

  /** Blocks until the Job completes  */
  public T get() {
    Barrier2 bar = _barrier;
    if( bar != null )           // Barrier may be null if task already completed
      bar.join(); // Block on the *barrier* task, which blocks until the fjtask on*Completion code runs completely
    assert isStopped();
    if (_ex!=null)
      throw new RuntimeException((Throwable)AutoBuffer.javaSerializeReadPojo(_ex));
    // Maybe null return, if the started fjtask does not actually produce a result at this Key
    return _result==null ? null : _result.get(); 
  }

  // --------------
  // Atomic State Updaters.  Atomically change state on the home node.  They
  // also update the *this* object from the freshest remote state, meaning the
  // *this* object changes after these calls.  
  // NO OTHER CHANGES HAPPEN TO JOB FIELDS.

  private abstract static class JAtomic extends TAtomic<Job> {
    void apply(Job job) { invoke(job._key); job.update_from_remote(); }
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
    Job remote = DKV.getGet(_key); // Watch for changes in the DKV
    if( this==remote ) return; // Trivial!
    if( null==remote ) return; // Stay with local version
    boolean differ = false;
    if( _stop_requested != remote._stop_requested ) differ = true;
    if(_start_time!= remote._start_time) differ = true;
    if(_end_time  != remote._end_time  ) differ = true;
    if(_ex        != remote._ex        ) differ = true;
    if(_work      != remote._work      ) differ = true;
    if(_worked    != remote._worked    ) differ = true;
    if(_msg       != remote._msg       ) differ = true;
    if(_max_runtime_msecs != remote._max_runtime_msecs) differ = true;
    if(! Arrays.equals(_warns, remote._warns)) differ = true;
    if( differ )
      synchronized(this) { 
        _stop_requested = remote._stop_requested;
        _start_time= remote._start_time;
        _end_time  = remote._end_time  ;
        _ex        = remote._ex        ;
        _work      = remote._work      ;
        _worked    = remote._worked    ;
        _msg       = remote._msg       ;
        _max_runtime_msecs = remote._max_runtime_msecs;
        _warns     = remote._warns;
      }
  }
  @Override public Class<KeyV3.JobKeyV3> makeSchema() { return KeyV3.JobKeyV3.class; }
}
