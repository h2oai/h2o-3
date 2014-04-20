package water;

import water.H2O.H2OCountedCompleter;

/** 
 *  Jobs are Keyed, because they need to Key to control e.g. atomic updates.
 *  Jobs produce a Keyed result, such as a Frame (from Parsing), or a Model.
 */
public class Job<T extends Keyed> extends Keyed {

  public T _result;

  transient H2OCountedCompleter _fjtask; // Top-level task you can block on

  protected Job( ) { super(defaultJobKey()); }

  // Job Keys are pinned to this node (i.e., the node invoked computation),
  // because it should be almost always updated locally
  private static Key defaultJobKey() { return Key.make((byte) 0, Key.JOB, H2O.SELF); }

  /** Blocks and get result of this job.
   * <p>
   * The call blocks on working task which was passed via {@link #start(H2OCountedCompleter)} method
   * and returns the result which is fetched from UKV based on job destination key.
   * </p>
   * @return result of this job fetched from UKV by destination key.
   * @see #start(H2OCountedCompleter)
   * @see UKV
   */
  public T get() {
    _fjtask.join();             // Block until top-level job is done
    //T ans = (T) DKV.get(destination_key).get();
    //remove();                   // Remove self-job
    //return ans;
    throw H2O.unimpl();
  }


  static class JobCancelledException extends RuntimeException {
  }

  public interface ProgressMonitor { public void update( int len ); }
}
