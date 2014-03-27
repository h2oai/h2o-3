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
  static int DEBUG_WEAVER;
  protected DTask(){}
  public DTask(H2OCountedCompleter completer){super(completer);}
  // NOTE: DTask CAN NOT have any ICED members (FetchId is DTask, causes DEADLOCK in multinode environment)
  // exception info, it must be unrolled here
  protected String _exception;
  protected String _msg;
  protected String _eFromNode; // Node where the exception originated
  // stackTrace info
  protected int [] _lineNum;
  protected String [] _cls, _mth, _fname;

  public void setException(Throwable ex){
    _exception = ex.getClass().getName();
    _msg = ex.getMessage();
    _eFromNode = H2O.SELF.toString();
    StackTraceElement[]  stk = ex.getStackTrace();
    _lineNum = new int[stk.length];
    _cls = new String[stk.length];
    _mth = new String[stk.length];
    _fname = new String[stk.length];
    for(int i = 0; i < stk.length; ++i){
      _lineNum[i] = stk[i].getLineNumber();
      _cls[i] = stk[i].getClassName();
      _mth[i] = stk[i].getMethodName();
      _fname[i] = stk[i].getFileName();
    }
  }

  public boolean hasException(){
    return _exception != null;
  }

  public DistributedException getDException() {
    if( !hasException() ) return null;
    String msg = _msg;
    if( !_exception.equals(DistributedException.class.getName()) ) {
      msg = " from " + _eFromNode + "; " + _exception;
      if( _msg != null ) msg = msg+": "+_msg;
    }
    DistributedException dex = new DistributedException(msg,null);
    StackTraceElement [] stk = new StackTraceElement[_cls.length];
    for(int i = 0; i < _cls.length; ++i)
      stk[i] = new StackTraceElement(_cls[i],_mth[i], _fname[i], _lineNum[i]);
    dex.setStackTrace(stk);
    return dex;
  }

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

  // The serialization flavor / delegate.  Lazily set on first use.
  private transient short _ice_id;

  // Return the icer for this instance+class.  Will set on 1st use.
  protected Icer<T> icer() {
    int id = _ice_id;
    return TypeMap.getIcer(id!=0 ? id : (_ice_id=(short)TypeMap.onIce(this)),this); 
  }
  @Override public AutoBuffer write(AutoBuffer ab) { return icer().write(ab,(T)this); }
  @Override public DTask      read (AutoBuffer ab) { return icer().read (ab,(T)this); }
  @Override public int        frozenType()         { return icer().frozenType();   }
  //@Override public AutoBuffer writeJSONFields(AutoBuffer bb) { return bb; }
  //@Override public water.api.DocGen.FieldDoc[] toDocField() { return null; }
  //public void copyOver(T that) {
  //  this._exception = that._exception;
  //  this._eFromNode = that._eFromNode;
  //  this._lineNum   = that._lineNum;
  //  this._fname     = that._fname;
  //  this._msg       = that._msg;
  //  this._cls       = that._cls;
  //  this._mth       = that._mth;
  //}
  private RuntimeException barf(String method) {
    return new RuntimeException(H2O.SELF + ":" + getClass().toString()+ " " + method +  " should be automatically overridden in the subclass by the auto-serialization code");
  }
  @Override public T clone() { return (T)super.clone(); }
}
