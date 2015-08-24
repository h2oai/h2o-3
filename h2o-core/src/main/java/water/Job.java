package water;

import jsr166y.CountedCompleter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;

import hex.ModelBuilder;
import water.H2O.H2OCountedCompleter;
import water.exceptions.H2OKeyNotFoundArgumentException;
import water.util.Log;

/** Jobs are used to do minimal tracking of long-lifetime user actions,
 *  including progress-bar updates and the ability to review in progress or
 *  completed Jobs, and cancel currently running Jobs.
 *  <p>
 *  Jobs are {@link Keyed}, because they need to Key to control e.g. atomic updates.
 *  Jobs produce a {@link Keyed} result, such as a Frame (from Parsing), or a Model.
 *  NOTE: the Job class is parametrized on the type of its _dest field.
 */
public class Job<T extends Keyed> extends Keyed {
  /** A system key for global list of Job keys. */
  public static final Key<Job> LIST = Key.make(" JobList", (byte) 0, Key.BUILT_IN_KEY, false);
  /** A list of field validation issues. */
  public ValidationMessage[] _messages = new ValidationMessage[0];
  private int _error_count = -1; // -1 ==> init not run yet, for those Jobs that have an init, like ModelBuilder. Note, this counts ONLY errors, not WARNs and etc.

  /**
   * init(expensive) is called inside a DTask, not from the http request thread.  If we add validation messages to the
   * Job we want to update it in the DKV so the client can see them when polling and later on
   * after the Job completes.
   * <p>
   * NOTE: this should only be called when no other threads are updating the job, for example from init() or after the
   * DTask is stopped and is getting cleaned up.
   */
  public void updateValidationMessages() {
    // Atomically update the validation messages in the Job in the DKV.

    // In some cases we haven't stored to the DKV yet:
    new TAtomic<Job>() {
      @Override public Job atomic(Job old) {
        if( old == null ) throw new H2OKeyNotFoundArgumentException((Key)null);

        old._messages = _messages;
        return old;
      }
    }.invoke(_key);
    }

  public int error_count() { return (_error_count > 0 ? _error_count : 0); }
  public int error_count_or_uninitialized() { return _error_count; }

  public void hide (String field_name, String message) { message(ValidationMessage.MessageType.HIDE , field_name, message); }

  public void info (String field_name, String message) { message(ValidationMessage.MessageType.INFO , field_name, message); }

  public void warn (String field_name, String message) { message(ValidationMessage.MessageType.WARN , field_name, message); }

  public void error(String field_name, String message) { message(ValidationMessage.MessageType.ERROR, field_name, message); _error_count++; }

  public void clearValidationErrors() {
    _messages = new ValidationMessage[0];
    _error_count = 0;
  }

  public void message(ValidationMessage.MessageType message_type, String field_name, String message) {
    _messages = Arrays.copyOf(_messages, _messages.length + 1);
    _messages[_messages.length - 1] = new ValidationMessage(message_type, field_name, message);
  }

  /** Get a string representation of only the ERROR ValidationMessages (e.g., to use in an exception throw). */
  public String validationErrors() {
    StringBuilder sb = new StringBuilder();
    for( ValidationMessage vm : _messages )
      if( vm.message_type == ValidationMessage.MessageType.ERROR )
        sb.append(vm.toString()).append("\n");
    return sb.toString();
  }

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

  transient H2OCountedCompleter _fjtask; // Top-level task to do
  transient H2OCountedCompleter _barrier;// Top-level task you can block on

  /** Jobs produce a single DKV result into Key _dest */
  public Key<T> _dest;   // Key for result
  /** Since _dest is public final, not sure why we have a getter but some
   *  people like 'em. */
  public final Key<T> dest() { return _dest; }

  public final Key<Job> jobKey() { return _key; }

  /** User description */
  public String _description;
  /** Job start_time using Sys.CTM */
  public long _start_time;     // Job started
  /** Job end_time using Sys.CTM, or 0 if not ended */
  public long   _end_time;     // Job end time, or 0 if not ended
  /** Any exception thrown by this Job, or null if none */
  public String _exception;    // Unpacked exception & stack trace

  /** Possible job states.  These are ORDERED; state levels can increased but never decrease */
  public enum JobState {
    CREATED,   // Job was created
    RUNNING,   // Job is running
    DONE,      // Job was successfully finished
    CANCELLED, // Job was cancelled by user
    FAILED,    // Job crashed, error message/exception is available
  }

  public JobState _state;

  /** Returns true if the job was cancelled by the user or crashed.
   *  @return true if the job is in state {@link JobState#CANCELLED} or {@link JobState#FAILED} */
  public boolean isCancelledOrCrashed() {
    return _state == JobState.CANCELLED || _state == JobState.FAILED;
  }

  /** Returns true if this job is running
   *  @return returns true only if this job is in running state. */
  public boolean isRunning() { return _state == JobState.RUNNING; }
  /** Returns true if this job is done
   *  @return  true if the job is in state {@link JobState#DONE} */
  public boolean isDone   () { return _state == JobState.DONE   ; }

  /** Returns true if this job was started and is now stopped */
  public boolean isStopped() { return _state == JobState.DONE || isCancelledOrCrashed(); }

  /** Check if given job is running.
   *  @param job_key job key
   *  @return true if job is still running else returns false.  */
  public static boolean isRunning(Key<Job> job_key) { return job_key.get().isRunning(); }

  /** Current runtime; zero if not started */
  public final long msec() {
    switch( _state ) {
    case CREATED: return 0;
    case RUNNING: return System.currentTimeMillis() - _start_time;
    default:      return _end_time                  - _start_time;
    }
  }

  private Job(Key<T> jobKey, Key<T> dest, String desc) {
    super(jobKey);
    _description = desc;
    _dest = dest;
    _state = JobState.CREATED;  // Created, but not yet running
  }
  /** Create a Job
   *  @param dest Final result Key to be produced by this Job
   *  @param desc String description
   */
  public Job(Key<T> dest, String desc) {
    this(defaultJobKey(),dest,desc);
  }
  // Job Keys are pinned to this node (i.e., the node that invoked the
  // computation), because it should be almost always updated locally
  private static Key defaultJobKey() { return Key.make((byte) 0, Key.JOB, false, H2O.SELF); }


  /** Start this task based on given top-level fork-join task representing job computation.
   *  @param fjtask top-level job computation task.
   *  @param work Units of work to be completed
   *  @param restartTimer
   * @return this job in {@link JobState#RUNNING} state
   *
   *  @see JobState
   *  @see H2OCountedCompleter
   */
  protected Job<T> start(final H2OCountedCompleter fjtask, long work, boolean restartTimer) {
    if (work >= 0)
      DKV.put(_progressKey = createProgressKey(), new Progress(work));
    assert _state == JobState.CREATED : "Trying to run job which was already run?";
    assert fjtask != null : "Starting a job with null working task is not permitted!";
    assert fjtask.getCompleter() == null : "Cannot have a completer; this must be a top-level task";
    _fjtask = fjtask;

    // Make a wrapper class that only *starts* when the fjtask completes -
    // especially it only starts even when fjt completes exceptionally... thus
    // the fjtask onExceptionalCompletion code runs completely before this
    // empty task starts - providing a simple barrier.  Threads blocking on the
    // job will block on the "barrier" task, which will block until the fjtask
    // runs the onCompletion or onExceptionCompletion code.
    _barrier = new H2OCountedCompleter() {
        @Override public void compute2() { }
        @Override public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller) {
          if( getCompleter() == null ) { // nobody else to handle this exception, so print it out
            System.err.println("barrier onExCompletion for "+fjtask);
            ex.printStackTrace();
          }
          return true;
        }
      };
    fjtask.setCompleter(_barrier);
    if (restartTimer) _start_time = System.currentTimeMillis();
    _state      = JobState.RUNNING;
    // Save the full state of the job
    DKV.put(_key, this);
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
    H2O.submitTask(fjtask);
    return this;
  }

  protected Key createProgressKey() { return Key.make(); }

  protected boolean deleteProgressKey() { return true; }

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
    block();
    assert !isRunning() : "Job state should not be running, but it is " + _state;
    return _dest.get();
  }

  /**
   * Blocks for job completion, but do not return anything as the destination object might not yet be finished
   */
  public void block() {
    assert _fjtask != null : "Cannot block on missing F/J task";
    _barrier.join(); // Block on the *barrier* task, which blocks until the fjtask on*Completion code runs completely
  }

  /** Marks job as finished and records job end time. */
  public void done() {
    done(false);
  }

  /**
   * Conditionally mark the job as finished and record job end time
   * @param force If set to false, then ask canBeDone() whether to mark the job as finished
   */
  protected void done(boolean force) {
    if (force || canBeDone()) changeJobState(null, JobState.DONE);
  }

  /**
   * Allow ModelBuilders to override this to conditionally mark the job as finished
   * @return whether or not the job should be marked as finished in done() or done(false)
   */
  protected boolean canBeDone() { return true; }

  /** Signal cancellation of this job.
   * <p>The job will be switched to state {@link JobState#CANCELLED} which signals that
   * the job was cancelled by a user. */
  public void cancel() { changeJobState(null, JobState.CANCELLED); }

  /** Signal exceptional cancellation of this job.
   *  @param ex exception causing the termination of job. */
  public void failed(Throwable ex) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    ex.printStackTrace(pw);
    String stackTrace = sw.toString();
    changeJobState("Got exception '" + ex.getClass() + "', with msg '" + ex.getMessage() + "'\n" + stackTrace, JobState.FAILED);
    //if(_fjtask != null && !_fjtask.isDone()) _fjtask.completeExceptionally(ex);
  }

  /** Signal exceptional cancellation of this job.
   *  @param msg cancellation message explaining reason for cancellation */
  public void cancel(final String msg) { changeJobState(msg, msg == null ? JobState.CANCELLED : JobState.FAILED); }

  private void changeJobState(final String msg, final JobState resultingState) {
    assert resultingState != JobState.RUNNING;
    if( _state == JobState.CANCELLED ) Log.info("Canceled job " + _key + "("  + _description + ") was cancelled again.");
    if( _state == resultingState ) return; // No change if already done
    final float finalProgress = resultingState==JobState.DONE ? 1.0f : progress_impl(); // One-shot set from NaN to progress, no longer need Progress Key
    final long done = System.currentTimeMillis();
    // Atomically flag the job as canceled
    new TAtomic<Job>() {
      @Override public Job atomic(Job old) {
        if( old == null ) return null; // Job already removed
        // States monotonically increase; states can increase but not decrease
        if( resultingState.ordinal() <= old._state.ordinal() ) return null;
        // Atomically capture changeJobState/crash state, plus end time
        old._exception = msg;
        old._state = resultingState;
        old._end_time = done;
        old._finalProgress = finalProgress;
        return old;
      }
    }.invoke(_key);
    // Also immediately update immediately a possibly cached local POJO (might
    // be shared with the DKV cached job, might not).
    if( this != DKV.getGet(_key) ) {
      _exception = msg;
      _state = resultingState;
      _end_time = done;
      _finalProgress = finalProgress;
    }
    // Remove on cancel/fail/done, only used whilst Job is Running
    if (deleteProgressKey())
      DKV.remove(_progressKey);
  }

  /** Returns a float from 0 to 1 representing progress.  Polled periodically.
   *  Can default to returning e.g. 0 always.  */
  public float progress() {
    return isStopped() ? _finalProgress : progress_impl();
  }
  // Read racy progress in a non-racy way: read the DKV exactly once,
  // null-checking as we go.  Handles the case where the Job is being removed
  // exactly when we are reading progress e.g. for the GUI.
  private Progress getProgress() {
    Key k = _progressKey;
    Value val;
    return k!=null && (val=DKV.get(k))!=null ? (Progress)val.get() : null;
  }
  // Checks the DKV for the progress Key & object
  private float progress_impl() {
    Progress p = getProgress();
    return p==null ? 0f : p.progress();
  }

  /** Returns last progress message. */
  public String progress_msg() { return isStopped() ? _state.toString() : progress_msg_impl(); }
  private String progress_msg_impl() {
    Progress p = getProgress();
    return p==null ? "" : p.progress_msg();
  }

  protected Key<Progress> _progressKey; //Key to store the Progress object under
  private float _finalProgress = Float.NaN; // Final progress after Job stops running

  /** Report new work done for this job */
  public final  void update(final long newworked) { update(newworked,(String)null); }
  public final  void update(final long newworked, String msg) { new ProgressUpdate(newworked, msg).fork(_progressKey); }
  public static void update(final long newworked, Key<Job> jobkey) { update(newworked, null, jobkey); }
  public static void update(final long newworked, String msg, Key<Job> jobkey) { jobkey.get().update(newworked, msg); }

  /**
   * Helper class to store the job progress in the DKV
   */
  public static class Progress extends Keyed<Progress> {
    @Override
    protected long checksum_impl() {
      return 2134340823432L*_work + 9023742947234L*_worked+(long)(12343242340234L*_fraction_done)+_progress_msg.hashCode();
    }

    // Progress methodology 1:  Specify total work up front and periodically tell when new units of work complete.
    private final long _work;
    private long _worked;
    public Progress() { _work = -1; _fraction_done = 0; _progress_msg = "Running..."; }

    // Progress methodology 2:  Client tells what fraction is total work is done every time.
    // In addition, a short one-line message can optionally be provided for a smart client like Flow.
    private float _fraction_done;
    private String _progress_msg;
    public Progress(long total) { _work = total; _fraction_done = -1.0f; _progress_msg = "Running...";}

    /** Report Job progress from 0 to 1.  Completed jobs are always 1.0 */
    public float progress() {
      if (_work >= 0) return _work == 0 /*not yet initialized*/ ? 0f : Math.max(0.0f, Math.min(1.0f, (float) _worked / (float) _work));
      return _fraction_done;
    }

    /** Report most recent progress message. */
    public String progress_msg() {
      return _progress_msg;
    }
  }

  /** Helper class to atomically update the job progress in the DKV */
  public static class ProgressUpdate extends TAtomic<Progress> {
    final long _newwork;
    final String _progress_msg;
    public ProgressUpdate(long newwork, String progress_msg) {  _newwork = newwork; _progress_msg = progress_msg; }
    public ProgressUpdate(String progress_msg) { this(0L,progress_msg); }

    /** Update progress with new work & message */
    @Override public Progress atomic(Progress old) {
      if( old == null ) return old;
      old._worked += _newwork;
      if (_progress_msg != null) old._progress_msg = _progress_msg;
      return old;
    }
  }

  /** Simple named exception class */
  public static class JobCancelledException extends RuntimeException{}

  @Override protected Futures remove_impl(Futures fs) {
    if (null != _progressKey && deleteProgressKey()) DKV.remove(_progressKey, fs);
    return fs;
  }

  /** Default checksum; not really used by Jobs.  */
  @Override protected long checksum_impl() { throw H2O.fail("Job checksum does not exist by definition"); }

  /** The result of an abnormal Model.Parameter check.  Contains a
   *  level, a field name, and a message.
   *
   *  Can be an ERROR, meaning the parameters can't be used as-is,
   *  a HIDE, which means the specified field should be hidden given
   *  the values of other fields, or a WARN or INFO for informative
   *  messages to the user.
   */
  public static final class ValidationMessage extends Iced {
    public enum MessageType { HIDE, INFO, WARN, ERROR }
    final ModelBuilder.ValidationMessage.MessageType message_type;
    final String field_name;
    final String message;

    public ValidationMessage(ModelBuilder.ValidationMessage.MessageType message_type, String field_name, String message) {
      this.message_type = message_type;
      this.field_name = field_name;
      this.message = message;
      switch (message_type) {
        case INFO: Log.info(field_name + ": " + message); break;
        case WARN: Log.warn(field_name + ": " + message); break;
        case ERROR: Log.err(field_name + ": " + message); break;
      }
    }

    public MessageType getMessageType() {
      return message_type;
    }

    @Override public String toString() { return message_type + " on field: " + field_name + ": " + message; }
  }
}
