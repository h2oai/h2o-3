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

  public final Key _dest;       // Key for result
  public T _result;

  transient H2OCountedCompleter _fjtask; // Top-level task you can block on

  public final String _description;
  public long _start_time;     // Job started
  public long   _end_time;     // Job ended
  private String _exception;    // Unpacked exception & stack trace

  /** Possible job states. */
  public static enum JobState {
    CREATED,   // Job was created
    RUNNING,   // Job is running
    CANCELLED, // Job was cancelled by user
    FAILED,   // Job crashed, error message/exception is available
    DONE       // Job was successfully finished
  }

  public JobState _state;

  protected Job( Key dest, String desc ) { 
    super(defaultJobKey()); 
    _description = desc; 
    _dest = dest; 
    _state = JobState.CREATED;  // Created, but not yet running
  }

  final protected Key dest() { return _dest; }

  // Job Keys are pinned to this node (i.e., the node invoked computation),
  // because it should be almost always updated locally
  private static Key defaultJobKey() { return Key.make((byte) 0, Key.JOB, H2O.SELF); }

  /** Start this task based on given top-level fork-join task representing job computation.
   * @param fjtask top-level job computation task.
   * @return this job in {@link JobState#RUNNING} state
   *
   * @see JobState
   * @see H2OCountedCompleter
   */
  protected Job start(final H2OCountedCompleter fjtask) {
    assert _state == JobState.CREATED : "Trying to run job which was already run?";
//    assert fjtask != null : "Starting a job with null working task is not permitted!";
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
  protected T get() {
    assert _fjtask != null : "Cannot block on missing F/J task";
    _fjtask.join();             // Block until top-level job is done
    T ans = DKV.get(_dest).get();
    remove();                   // Remove self-job
    return ans;
  }

  /** Signal cancellation of this job.
   * <p>The job will be switched to state {@link JobState#CANCELLED} which signals that
   * the job was cancelled by a user. */
  public void cancel() {
    cancel(null, JobState.CANCELLED);
  }
  /** Signal exceptional cancellation of this job.
   * @param ex exception causing the termination of job.
   */
  public void cancel(Throwable ex){
    if(_fjtask != null && !_fjtask.isDone()) _fjtask.completeExceptionally(ex);
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    ex.printStackTrace(pw);
    String stackTrace = sw.toString();
    cancel("Got exception '" + ex.getClass() + "', with msg '" + ex.getMessage() + "'\n" + stackTrace, JobState.FAILED);
  }
  /** Signal exceptional cancellation of this job.
   * @param msg cancellation message explaining reason for cancelation
   */
  public void cancel(final String msg) {
    JobState js = msg == null ? JobState.CANCELLED : JobState.FAILED;
    cancel(msg, js);
  }
  private void cancel(final String msg, final JobState resultingState ) {
    if( _state == JobState.CANCELLED ) Log.info("Canceled job " + _key + "("  + _description + ") was cancelled again.");
    assert resultingState != JobState.RUNNING;
    final long done = System.currentTimeMillis();
    _exception = msg;
    _state = resultingState;
    _end_time = done;
    // Atomically flag the job as canceled
    new TAtomic<Job>() {
      @Override public Job atomic(Job old) {
        if( old == null ) return null; // Job already removed
        if( old.isCancelledOrCrashed() ) return null; // Job already canceled/crashed
        // Atomically capture cancel/crash state, plus end time
        old._exception = msg;
        old._state = resultingState;
        old._end_time = done;
        old._result = null; // No result, especially no giant but broken result
        return old;
      }
    }.invoke(_key);

    // Run the onCancelled code synchronously, right now
    onCancelled();
  }

  /**
   * Callback which is called after job cancellation (by user, by exception).
   */
  protected void onCancelled() {
  }

  /** Returns true if the job was cancelled by the user or crashed.
   * @return true if the job is in state {@link JobState#CANCELLED} or {@link JobState#FAILED}
   */
  private boolean isCancelledOrCrashed() {
    return _state == JobState.CANCELLED || _state == JobState.FAILED;
  }

  /** Returns true if this job is running
   * @return returns true only if this job is in running state.
   */
  public boolean isRunning() { return _state == JobState.RUNNING; }

   /** Check if given job is running.
   *
   * @param job_key job key
   * @return true if job is still running else returns false.
   */
  public static boolean isRunning(Key job_key) {
    Value j = DKV.get(job_key);
    assert j!=null : "Job should always be in DKV!";
    return ((Job)j.get()).isRunning();
  }

  public static class JobCancelledException extends RuntimeException {
    public JobCancelledException(){super("job was cancelled!");}
    public JobCancelledException(String msg){super("job was cancelled! with msg '" + msg + "'");}
  }

  public interface ProgressMonitor { public void update( long len ); }

  private static class JobList extends Keyed { 
    Key[] _jobs;
    JobList() { super(LIST); _jobs = new Key[0]; }
  }
}
