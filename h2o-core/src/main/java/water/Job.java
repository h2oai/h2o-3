package water;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import water.H2O.H2OCountedCompleter;
import water.util.Log;

/** 
 *  Jobs are Keyed, because they need to Key to control e.g. atomic updates.
 *  Jobs produce a Keyed result, such as a Frame (from Parsing), or a Model.
 */
public class Job<T extends Keyed> extends Keyed {
  /** A system key for global list of Job keys. */
  public static final Key LIST = Key.make(" JobList", (byte) 0, Key.BUILT_IN_KEY);
  private static class JobList extends Keyed { 
    Key[] _jobs;
    JobList() { super(LIST); _jobs = new Key[0]; }
  }

  transient H2OCountedCompleter _fjtask; // Top-level task you can block on

  /** Jobs produce a single DKV result into Key _dest */
  public final Key _dest;       // Key for result
  public final Key dest() { return _dest; }

  /** Basic metadata about the Job */
  public final String _description;
  public long _start_time;     // Job started
  public long   _end_time;     // Job end time, or 0 if not ended
  public String _exception;    // Unpacked exception & stack trace

  /** Possible job states. */
  public static enum JobState {
    CREATED,   // Job was created
    RUNNING,   // Job is running
    CANCELLED, // Job was cancelled by user
    FAILED,    // Job crashed, error message/exception is available
    DONE       // Job was successfully finished
  }

  public JobState _state;

  /** Returns true if the job was cancelled by the user or crashed.
   *  @return true if the job is in state {@link JobState#CANCELLED} or {@link JobState#FAILED} */
  private boolean isCancelledOrCrashed() {
    return _state == JobState.CANCELLED || _state == JobState.FAILED;
  }

  /** Returns true if this job is running
   *  @return returns true only if this job is in running state. */
  public boolean isRunning() { return _state == JobState.RUNNING; }

  /** Returns true if this job was started and is now stopped */
  public boolean isStopped() { return _state == JobState.DONE || isCancelledOrCrashed(); }

  /** Check if given job is running.
   *  @param job_key job key
   *  @return true if job is still running else returns false.  */
  public static boolean isRunning(Key job_key) {
    return ((Job)DKV.get(job_key).get()).isRunning();
  }

  /** Create a Job
   *  @param dest Final result Key to be produced by this Job
   *  @param desc String description
   *  @param work Units of work to be completed
   */
  protected Job( Key dest, String desc, long work ) { 
    super(defaultJobKey()); 
    _description = desc; 
    _dest = dest; 
    _state = JobState.CREATED;  // Created, but not yet running
    _work = work;               // Units of work
  }
  // Job Keys are pinned to this node (i.e., the node that invoked the
  // computation), because it should be almost always updated locally
  private static Key defaultJobKey() { return Key.make((byte) 0, Key.JOB, H2O.SELF); }


  /** Start this task based on given top-level fork-join task representing job computation.
   *  @param fjtask top-level job computation task.
   *  @return this job in {@link JobState#RUNNING} state
   *  
   *  @see JobState
   *  @see H2OCountedCompleter
   */
  public Job start(final H2OCountedCompleter fjtask) {
    assert _state == JobState.CREATED : "Trying to run job which was already run?";
    assert fjtask != null : "Starting a job with null working task is not permitted!";
    assert _key.home();         // Always starting on same node job was created
    _fjtask = fjtask;
    _start_time = System.currentTimeMillis();
    _state      = JobState.RUNNING;
    // Save the full state of the job
    DKV.put(_key, this);
    // Update job list
    new TAtomic<JobList>() {
      @Override public JobList atomic(JobList old) {
        if( old == null ) old = new JobList();
        Key[] jobs = old._jobs;
        old._jobs = Arrays.copyOf(jobs, jobs.length + 1);
        old._jobs[jobs.length] = _key;
        return old;
      }
    }.invoke(LIST);
    H2O.submitTask(fjtask);
    return this;
  }

  /** Blocks and get result of this job.
   * <p>
   * The call blocks on working task which was passed via {@link #start(H2OCountedCompleter)} method
   * and returns the result which is fetched from UKV based on job destination key.
   * </p>
   * @return result of this job fetched from UKV by destination key.
   * @see #start(H2OCountedCompleter)
   * @see DKV
   */
  public T get() {
    assert _fjtask != null : "Cannot block on missing F/J task";
    assert _key.home();         // Always blocking on same node job was created
    _fjtask.join();
    assert !isRunning();
    return DKV.get(_dest).get();
  }

  /** Marks job as finished and records job end time. */
  public void done() { cancel(null,JobState.DONE); }

  /** Signal cancellation of this job.
   * <p>The job will be switched to state {@link JobState#CANCELLED} which signals that
   * the job was cancelled by a user. */
  public void cancel() {
    cancel(null, JobState.CANCELLED);
  }

  /** Signal exceptional cancellation of this job.
   *  @param ex exception causing the termination of job. */
  public void cancel2(Throwable ex) {
    if(_fjtask != null && !_fjtask.isDone()) _fjtask.completeExceptionally(ex);
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    ex.printStackTrace(pw);
    String stackTrace = sw.toString();
    cancel("Got exception '" + ex.getClass() + "', with msg '" + ex.getMessage() + "'\n" + stackTrace, JobState.FAILED);
  }

  /** Signal exceptional cancellation of this job.
   *  @param msg cancellation message explaining reason for cancelation */
  public void cancel(final String msg) {
    cancel(msg, msg == null ? JobState.CANCELLED : JobState.FAILED);
  }

  private void cancel(final String msg, final JobState resultingState ) {
    assert resultingState != JobState.RUNNING;
    if( _state == JobState.CANCELLED ) Log.info("Canceled job " + _key + "("  + _description + ") was cancelled again.");
    if( _state == resultingState ) return; // No change if already done

    final long done = System.currentTimeMillis();
    _exception = msg;
    _state = resultingState;
    _end_time = done;
    // Atomically flag the job as canceled
    new TAtomic<Job>() {
      @Override public Job atomic(Job old) {
        if( old == null ) return null; // Job already removed
        if( old._state == resultingState ) return null; // Job already canceled/crashed
        if( !isCancelledOrCrashed() && old.isCancelledOrCrashed() ) return null;
        // Atomically capture cancel/crash state, plus end time
        old._exception = msg;
        old._state = resultingState;
        old._end_time = done;
        return old;
      }
      @Override void onSuccess( Job old ) {
        // Run the onCancelled code synchronously, right now
        if( isCancelledOrCrashed() )
          onCancelled();
      }
    }.invoke(_key);
  }

  /**
   * Callback which is called after job cancellation (by user, by exception).
   */
  protected void onCancelled() {
  }


  /** Returns a float from 0 to 1 representing progress.  Polled periodically.  
   *  Can default to returning e.g. 0 always.  */
  public final long _work;
  private long _worked;
  public final float progress() { return (float)_worked/(float)_work; }
  public final void update(final long newworked) { update(newworked,_key); }
  public static void update(final long newworked, Key jobkey) { 
    new TAtomic<Job>() {
      @Override public Job atomic(Job old) {
        assert newworked+old._worked <= old._work;
        old._worked+=newworked;
        return old;
      }
    }.fork(jobkey);
  }

}
