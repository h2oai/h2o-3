package water;

import jsr166y.CountedCompleter;
import sun.misc.Unsafe;
import water.DException.DistributedException;
import water.H2O.H2OCountedCompleter;
import water.nbhm.UtilUnsafe;

/** Objects which are passed & remotely executed.<p>
 * <p>
 * Efficient serialization methods for subclasses will be automatically
 * generated, but explicit ones can be provided.  Transient fields will
 * <em>not</em> be mirrored between the VMs.
 * <ol>
 * <li>On the local vm, this task will be serialized and sent to a remote.</li>
 * <li>On the remote, the task will be deserialized.</li>
 * <li>On the remote, the {@link #invoke(H2ONode)} method will be executed.</li>
 * <li>On the remote, the task will be serialized and sent to the local vm</li>
 * <li>On the local vm, the task will be deserialized
 * <em>into the original instance</em></li>
 * <li>On the local vm, the {@link #onAck()} method will be executed.</li>
 * <li>On the remote, the {@link #onAckAck()} method will be executed.</li>
 * </ol>
 *
 */
public abstract class DTask<T extends DTask> extends H2OCountedCompleter implements Freezable {
  protected DTask(){}
  public DTask(H2OCountedCompleter completer){super(completer);}

  // Return a distributed-exception
  private DException _ex;
  public final boolean hasException() { return _ex != null; }
  public void setException(Throwable ex) { _ex = new DException(ex); }
  public DistributedException getDException() { return _ex==null ? null : _ex.toEx(); }

  // Track if the reply came via TCP - which means a timeout on ACKing the TCP
  // result does NOT need to get the entire result again, just that the client
  // needs more time to process the TCP result.
  transient boolean _repliedTcp; // Any return/reply/result was sent via TCP

  /** Top-level remote execution hook.  Called on the <em>remote</em>. */
  public void dinvoke( H2ONode sender ) { compute2(); }

  /** 2nd top-level execution hook.  After the primary task has received a
   * result (ACK) and before we have sent an ACKACK, this method is executed
   * on the <em>local vm</em>.  Transients from the local vm are available here.
   */
  public void onAck() {}

  /** 3rd top-level execution hook.  After the original vm sent an ACKACK,
   * this method is executed on the <em>remote</em>.  Transients from the remote
   * vm are available here.
   */
  public void onAckAck() {}

  /** Override to remove 2 lines of logging per RPC.  0.5M RPC's will lead to
   *  1M lines of logging at about 50 bytes/line --> 50M of log file, which
   *  will swamp all other logging output. */
  public boolean logVerbose() { return true; }

  // the exception should be forwarded and handled later, do not do anything
  // here (mask stack trace printing of H2OCountedCompleter)
  @Override public boolean onExceptionalCompletion( Throwable ex, CountedCompleter caller ) {
    return true;
  }
}
