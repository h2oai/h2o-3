package water;

import jsr166y.CountedCompleter;
import water.DException.DistributedException;
import water.H2O.H2OCountedCompleter;

/** Objects which are passed and {@link #dinvoke} is remotely executed.<p>
 * <p>
 * Efficient serialization methods for subclasses will be automatically
 * generated, but explicit ones can be provided.  Transient fields will
 * <em>not</em> be mirrored between the VMs.
 * <ol>
 * <li>On the local vm, this task will be serialized and sent to a remote.</li>
 * <li>On the remote, the task will be deserialized.</li>
 * <li>On the remote, the {@link #dinvoke(H2ONode)} method will be executed.</li>
 * <li>On the remote, the task will be serialized and sent to the local vm</li>
 * <li>On the local vm, the task will be deserialized
 * <em>into the original instance</em></li>
 * <li>On the local vm, the {@link #onAck()} method will be executed.</li>
 * <li>On the remote, the {@link #onAckAck()} method will be executed.</li>
 * </ol>
 *
 */
public abstract class DTask<T extends DTask> extends H2OCountedCompleter<T> {
  protected DTask(){}


  public DTask(H2OCountedCompleter completer){super(completer);}

  protected boolean _modifiesInputs = false;

  /** A distributable exception object, thrown by {@link #dinvoke}.  */
  protected DException _ex;
  /** True if {@link #dinvoke} threw an exception.
   *  @return True if _ex is non-null */
  public final boolean hasException() { return _ex != null; }
  /** Capture the first exception in _ex.  Later setException attempts are ignored. */
  public synchronized void setException(Throwable ex) { if( _ex==null ) _ex = new DException(ex,getClass()); }
  /** The _ex field as a RuntimeException or null.
   *  @return The _ex field as a RuntimeException or null. */
  public DistributedException getDException() { return _ex==null ? null : _ex.toEx(); }

  // Track if the reply came via TCP - which means a timeout on ACKing the TCP
  // result does NOT need to get the entire result again, just that the client
  // needs more time to process the TCP result.
  transient boolean _repliedTcp; // Any return/reply/result was sent via TCP

  /** Top-level remote execution hook.  Called on the <em>remote</em>. */
  public void dinvoke( H2ONode sender ) {
    // note: intentionally using H2O.submit here instead of direct compute2 call here to preserve FJ behavior
    // such as exceptions being caught and handled via onExceptionalCompletion
    // can't use fork() to keep correct priority level
    H2O.submitTask(this);
  }
  
  /** 2nd top-level execution hook.  After the primary task has received a
   * result (ACK) and before we have sent an ACKACK, this method is executed on
   * the <em>local vm</em>.  Transients from the local vm are available here. */
  public void onAck() {}

  /** 3rd top-level execution hook.  After the original vm sent an ACKACK, this
   * method is executed on the <em>remote</em>.  Transients from the remote vm
   * are available here.  */
  public void onAckAck() {}

  /** Override to remove 2 lines of logging per RPC.  0.5M RPC's will lead to
   *  1M lines of logging at about 50 bytes/line produces 50M of log file,
   *  which will swamp all other logging output. */
  public boolean logVerbose() { return true; }

  // For MRTasks, we need to copyOver
  protected void copyOver( T src ) { icer().copyOver((T)this,src); }

  /** Task to be executed at the home node of the given key.
   *  Basically a wrapper around DTask which enables us to bypass
   *  remote/local distinction (RPC versus submitTask).  */
  public static abstract class DKeyTask<T extends DKeyTask,V extends Keyed> extends DTask<DKeyTask>{
    private final Key _key;
    public DKeyTask(final Key k) {this(null,k);}
    public DKeyTask(H2OCountedCompleter cmp,final Key k) {
      super(cmp);
      _key = k;
    }

    /** Override map(); will be run on Key's home node */
    protected abstract void map(V v);

    @Override public final void compute2(){
      if(_key.home()){
        Value val = H2O.get(_key);
        if( val != null )
          map(val.<V>get());    // Call map locally
        tryComplete();
      } else {                  // Else call remotely
        new RPC(_key.home_node(),this).addCompleter(this).call();
      }
    }
    // onCompletion must be empty here, may be invoked twice (on remote and local)
    @Override public final void onCompletion(CountedCompleter cc){}
    /** Convenience non-blocking submit to work queues */
    public void submitTask() {H2O.submitTask(this);}
    /** Convenience blocking submit to work queues */
    public T invokeTask() {
      H2O.submitTask(this);
      join();
      return (T)this;
    }
  }

  /** Task to cleanly remove value from the K/V (call it's remove()
   *  destructor) without the need to fetch it locally first.  */
  public static class RemoveCall extends DKeyTask {
    public RemoveCall(H2OCountedCompleter cmp, Key k) { super(cmp, k);}
    @Override protected void map(Keyed val) { val.remove();}
  }

}
