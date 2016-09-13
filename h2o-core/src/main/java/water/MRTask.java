package water;

import jsr166y.CountedCompleter;
import jsr166y.ForkJoinPool;
import water.fvec.*;
import water.util.DistributedException;
import water.util.PrettyPrint;
import water.fvec.Vec.VectorGroup;

import java.util.Arrays;

/**
 * Map/Reduce style distributed computation.
 * <p>
 * MRTask provides several <code>map</code> and <code>reduce</code> methods
 * that can be overridden to specify a computation. Several instances of this
 * class will be created to distribute the computation over F/J threads and
 * machines.  Non-transient fields are copied and serialized to instances
 * created for map invocations.  Reduce methods can store their results in
 * fields.  Results are serialized and reduced all the way back to the invoking
 * node.  When the last reduce method has been called, fields of the initial
 * MRTask instance contains the computation results.</p>
 * <p>
 * Apart from small reduced POJO returned to the calling node, MRTask can
 * produce output vector(s) as a result.  These will have chunks co-located
 * with the input dataset, however, their number of lines will generally differ
 * so they won't be strictly compatible with the original.  To produce output
 * vectors, call doAll.dfork version with required number of outputs and
 * override appropriate <code>map</code> call taking required number of
 * NewChunks.  MRTask will automatically close the new Appendable vecs and a
 * call to <code>outputFrame</code> will make a frame with newly created Vecs.
 * </p>
 *
 * <p><b>Overview</b></p>
 * <p>
 * Distributed computation starts by calling <code>doAll</code>,
 * <code>dfork</code>, or <code>dfork</code>.  <code>doAll</code> simply
 * calls <code>dfork</code> and <code>dfork</code> before blocking;
 * <code>dfork</code> and <code>dfork</code> are non-blocking.  The main
 * pardigm is divide-conquer-combine using ForkJoin. </p>
 * <p>
 * If <code>doAll</code> is called with Keys, then one <code>map</code> call is
 * made per Key, on the Key's home node.  If MRTask is invoked on a Frame (or
 * equivalently a Vec[]), then one <code>map</code> call is made per Chunk for
 * all Vecs at once, on the Chunk's home node.  In both modes,
 * <code>reduce</code> is called between each pair of calls to
 * <code>map</code>.  </p>
 * <p>
 * MRTask can also be called with <code>doAllNodes</code>, in which case only
 * the setupLocal call is made once per node; neither map nor reduce are
 * called.</p>
 * <p>

 * Computation is tailored primarily by overriding.  The main method is the
 * <code>map</code> call, coupled sometimes with a <code>reduce</code> call.
 * <code>setupLocal</code> is called once per node before any map calls are
 * made on that node (but perhaps other nodes have already started); in reverse
 * <code>closeLocal</code> is called after the last map call completes on a
 * node (but perhaps other nodes are still computing maps).
 * <code>postGlobal</code> is called once only after all maps, reduces and
 * closeLocals, and only on the home node.</p>
 */
public abstract class MRTask<T extends MRTask<T>> extends DTask<T> implements ForkJoinPool.ManagedBlocker {

  /*
  * Technical note to developers:
  *
  *   There are several internal flags and counters used throughout. They are gathered in
  *   this note to help you reason about the execution of an MRTask.
  *
  *    internal "top-level" fields
  *    ---------------------------
  *     - RPC<T> _nleft, _nrite: "child" node/JVMs that are doing work
  *     - boolean _topLocal    : "root" MRTask on a local machine
  *     - boolean _topGlobal   : "root" MRTask on the "root" node
  *     - T _left, _rite       : "child" MRTasks on a local machine
  *     - T _res               : "result" MRTask (everything reduced into here)
  *     - int _nlo,_nhi        : range of nodes to do remote work on (divide-conquer; see Diagram 2)
  *     - Futures _fs          : _topLocal task blocks on _fs for _left and _rite to complete
  *
  *       Diagram 1: N is for Node; T is for Task
  *       -------------------------------------
  *              3 node cloud              Inside one of the 'N' nodes:
  *                   N1                               T  _topLocal**
  *                 /   \                            /  \
  *         N2 (_nleft)  N3 (_nrite)         T (_left)   T (_rite)
  *
  *                  **: T is also _topGlobal if N==N1
  *
  *    These fields get set in the <code>SetupLocal0<code> call. Let's see what it does:
  *
  *     Diagram 2:
  *     ----------
  *       dfork on N1
  *         - _topGlobal=true
  *         - _nlo=0
  *         - _nhi=CLOUD_SIZE
  *                ||
  *                ||
  *                ||
  *                ==>       setupLocal0 on N1
  *                            - topLocal=true
  *                            - _fs = new Futures()
  *                            - nmid = (_nlo + _nhi) >> 1 => split the range of nodes (divide-conquer)
  *                            - _nleft = remote_compute(_nlo,nmid) => chooses a node in range and does new RPC().call()
  *                            - _nrite = remote_compute(nmid,_nhi)    serializing MRTask and call dinvoke on remote.
  *                           /                                 \
  *                         /                                     \
  *                       /                                         \
  *                  dinvoke on N2                              dinvoke on N3
  *                   setupLocal0 on N2                           setupLocal0 on N3
  *                     - topLocal=true                             - topLocal=true
  *                     - _fs = new Futures()                       - _fs = new Futures()
  *                     - (continue splitting)                      - (continue splitting)
  *                   H2O.submitTask(this) => compute2            H2O.submitTask(this) => compute2
  *
  */

  public MRTask() { super(); }
  protected MRTask(H2O.H2OCountedCompleter cmp) {super(cmp); }
  protected MRTask(byte prior) { super(prior); }

  /**
   * This Frame instance is the handle for computation over a set of Vec instances. Recall
   * that a Frame is a collection Vec instances, so this includes any invocation of
   * <code>doAll</code> with Frame and Vec[] instances. Top-level calls to
   * <code>doAll</code> wrap Vec instances into a new Frame instance and set this into
   * <code>_fr</code> during a call to <code>dfork</code>.
   */
  public Frame _fr;

  /** This <code>Key[]</code> instance is the handle used for computation when
   *  an MRTask is invoked over an array of <code>Key</code>instances. */
  public Key[] _keys;

  /** The number and type of output Vec instances produced by an MRTask.  If
   *  null then there are no outputs, _appendables will be null, and calls to
   *  <code>outputFrame</code> will return null. */
  private byte _output_types[];

  /** First reserved VectorGroup key index for all output Vecs */
  private int _vid;

  /** New Output vectors; may be null.
   * @return the set of AppendableVec instances or null if _output_types is null  */
  public AppendableVec[] appendables() { return _appendables; }

  /** Appendables are treated separately (roll-ups computed in map/reduce
   *  style, can not be passed via K/V store).*/
  protected AppendableVec[] _appendables;

  /** Internal field to track the left &amp; right remote nodes/JVMs to work on */
  transient protected RPC<T> _nleft, _nrite;

  /** Internal field to track if this is a top-level local call */
  transient protected boolean _topLocal; // Top-level local call, returning results over the wire

  /** Internal field to track if this is a top-level call. */
  transient boolean _topGlobal = false;

  /** Internal field to track the left &amp; right sub-range of chunks to work on */
  transient protected T _left, _rite; // In-progress execution tree

  /** Internal field upon which all reduces occur. */
  transient private T _res;           // Result

  /** The range of Nodes to work on remotely */
  protected short _nlo, _nhi;

  /** Internal field to track a range of local Chunks to work on */
  transient protected int _lo, _hi;

  /** We can add more things to block on - in case we want a bunch of lazy
   *  tasks produced by children to all end before this top-level task ends.
   *  Semantically, these will all complete before we return from the top-level
   *  task.  Pragmatically, we block on a finer grained basis. */
  transient protected Futures _fs; // More things to block on

  /** If true, run entirely local - which will pull all the data locally. */
  protected boolean _run_local;

  public String profString() { return _profile != null ? _profile.toString() : "Profiling turned off"; }
  MRProfile _profile;

  /** Used to invoke profiling.  Call as: <code>new MRTask().profile().doAll();*/
  public T profile() { _profile = new MRProfile(this); return (T)this; }

  /** Get the resulting Frame from this invoked MRTask.  <b>This Frame is not
   *  in the DKV.</b> AppendableVec instances are closed into Vec instances,
   *  which then appear in the DKV.
   *
   *  @return null if no outputs, otherwise returns the resulting Frame from
   *  the MRTask.  The Frame has no column names nor domains.
   */
  public Frame outputFrame() { return outputFrame(null,null,null); }

  /** Get the resulting Frame from this invoked MRTask.  <b>This Frame is not in
   *  the DKV.</b> AppendableVec instances are closed into Vec instances, which
   *  then appear in the DKV.
   *
   *  @param names The names of the columns in the resulting Frame.
   *  @param domains The domains of the columns in the resulting Frame.
   *  @return The result Frame, or null if no outputs
   */
  public Frame outputFrame(String [] names, String [][] domains){ return outputFrame(null,names,domains); }

  /**
   * Get the resulting Frame from this invoked MRTask. If the passed in <code>key</code>
   * is not null, then the resulting Frame will appear in the DKV. AppendableVec instances
   * are closed into Vec instances, which then appear in the DKV.
   *
   * @param key If null, then the Frame will not appear in the DKV. Otherwise, this result
   *            will appear in the DKV under this key.
   * @param names The names of the columns in the resulting Frame.
   * @param domains The domains of the columns in the resulting Frame.
   * @return null if _noutputs is 0, otherwise returns a Frame.
   */
  public Frame outputFrame(Key<Frame> key, String [] names, String [][] domains){
    Futures fs = new Futures();
    Frame res = closeFrame(key, names, domains, fs);
    if( key != null ) DKV.put(res,fs);
    fs.blockForPending();
    return res;
  }

  // the work-horse for the outputFrame calls
  private Frame closeFrame(Key key, String[] names, String[][] domains, Futures fs) {
    if( _output_types == null ) return null;
    final int noutputs = _output_types.length;
    Vec[] vecs = new Vec[noutputs];
    if( _appendables==null || _appendables.length == 0)  // Zero rows?
      for( int i = 0; i < noutputs; i++ )
        vecs[i] = _fr.anyVec().makeZero();
    else {
      int rowLayout = _appendables[0].compute_rowLayout();
      for( int i = 0; i < noutputs; i++ ) {
        _appendables[i].setDomain(domains==null ? null : domains[i]);
        vecs[i] = _appendables[i].close(rowLayout,fs);
      }
    }
    return new Frame(key,names,vecs);
  }

  /** Override with your map implementation.  This overload is given a single
   *  <strong>local</strong> input Chunk.  It is meant for map/reduce jobs that use a
   *  single column in a input Frame.  All map variants are called, but only one is
   *  expected to be overridden. */
  public void map( Chunk c ) { }
  public void map( Chunk c, NewChunk nc ) { }

  /** Override with your map implementation.  This overload is given two
   *  <strong>local</strong> Chunks.  All map variants are called, but only one
   *  is expected to be overridden. */
  public void map( Chunk c0, Chunk c1 ) { }
  //public void map( Chunk c0, Chunk c1, NewChunk nc) { }
  //public void map( Chunk c0, Chunk c1, NewChunk nc1, NewChunk nc2 ) { }

  /** Override with your map implementation.  This overload is given three
   * <strong>local</strong> input Chunks.  All map variants are called, but only one
   * is expected to be overridden. */
  public void map( Chunk c0, Chunk c1, Chunk c2 ) { }
  //public void map( Chunk c0, Chunk c1, Chunk c2, NewChunk nc ) { }
  //public void map( Chunk c0, Chunk c1, Chunk c2, NewChunk nc1, NewChunk nc2 ) { }

  /** Override with your map implementation.  This overload is given an array
   *  of <strong>local</strong> input Chunks, for Frames with arbitrary column
   *  numbers.  All map variants are called, but only one is expected to be
   *  overridden. */
  public void map( Chunk cs[] ) { }

  /** The handy method to generate a new vector based on existing vectors.
   *
   * Note: This method is used by Sparkling Water examples.
   *
   * @param cs  input vectors
   * @param nc  output vector
   */
  public void map( Chunk cs[], NewChunk nc ) { }
  public void map( Chunk cs[], NewChunk nc1, NewChunk nc2 ) { }
  public void map( Chunk cs[], NewChunk [] ncs ) { }

  /** Override with your map implementation.  Used when doAll is called with
   *  an array of Keys, and called once-per-Key on the Key's Home node */
  public void map( Key key ) { }

  /** Override to combine results from 'mrt' into 'this' MRTask.  Both 'this'
   *  and 'mrt' are guaranteed to either have map() run on them, or be the
   *  results of a prior reduce().  Reduce is optional if, e.g., the result is
   *  some output vector.  */
  public void reduce( T mrt ) { }

  /** Override to do any remote initialization on the 1st remote instance of
   *  this object, for initializing node-local shared data structures.  */
  protected void setupLocal() {}
  /** Override to do any remote cleaning on the last remote instance of
   *  this object, for disposing of node-local shared data structures.  */
  protected void closeLocal() { }

  /** Compute a permissible node index on which to launch remote work. */
  private int addShift( int x ) { x += _nlo; int sz = H2O.CLOUD.size(); return x < sz ? x : x-sz; }
  private int subShift( int x ) { x -= _nlo; int sz = H2O.CLOUD.size(); return x <  0 ? x+sz : x; }
  private short selfidx() { int idx = H2O.SELF.index(); if( idx>= 0 ) return (short)idx; assert H2O.SELF._heartbeat._client; return 0; }

  // Profiling support.  Time for each subpart of a single M/R task, plus any
  // nested MRTasks.  All numbers are CTM stamps or millisecond times.
  private static class MRProfile extends Iced {
    String _clz;
    public MRProfile(MRTask mrt) {
      _clz = mrt.getClass().toString();
      _localdone = System.currentTimeMillis();
    }
    // See where these are set to understand their meaning.  If we split the
    // job, then _lstart & _rstart are the start of left & right jobs.  If we
    // do NOT split, then _rstart is 0 and _lstart is for the user map job(s).
    long _localstart, _rpcLstart, _rpcRstart, _rpcRdone, _localdone; // Local setup, RPC network i/o times
    long _mapstart, _userstart, _closestart, _mapdone; // MAP phase
    long _onCstart, _reducedone, _closeLocalDone, _remoteBlkDone, _localBlkDone, _onCdone; // REDUCE phase
    // If we split the job left/right, then we get a total recording of the
    // last job, and the exec time & completion time of 1st job done.
    long _time1st, _done1st;
    int _size_rez0, _size_rez1; // i/o size in bytes during reduce
    MRProfile _last;
    long sumTime() { return _onCdone - (_localstart==0 ? _mapstart : _localstart); }
    void gather( MRProfile p, int size_rez ) {
      p._clz=null;
      if( _last == null ) { _last=p; _time1st = p.sumTime(); _done1st = p._onCdone; }
      else {
        MRProfile first = _last._onCdone <= p._onCdone ? _last : p;
        _last           = _last._onCdone >  p._onCdone ? _last : p;
        if( first._onCdone > _done1st ) { _time1st = first.sumTime(); _done1st = first._onCdone; }
      }
      if( size_rez !=0 )        // Record i/o result size
        if( _size_rez0 == 0 ) _size_rez0=size_rez;
        else                  _size_rez1=size_rez;
      assert _userstart !=0 || _last != null;
      assert _last._onCdone >= _done1st;
    }

    @Override public String toString() { return print(new StringBuilder(),0).toString(); }
    private StringBuilder print(StringBuilder sb, int d) {
      if( d==0 ) sb.append(_clz).append("\n");
      for( int i=0; i<d; i++ ) sb.append("  ");
      if( _localstart != 0 ) sb.append("Node local ").append(_localdone - _localstart).append("ms, ");
      if( _last != null ) {   // Forked job?
        sb.append("Slow wait ").append(_mapstart-_localdone).append("ms + work ").append(_last.sumTime()).append("ms, ");
        sb.append("Fast work ").append(_time1st).append("ms + wait ").append(_onCstart-_done1st).append("ms\n");
        _last.print(sb,d+1); // Nested slow-path print
        for( int i=0; i<d; i++ ) sb.append("  ");
        sb.append("join-i/o ").append(_onCstart-_last._onCdone).append("ms, ");
      }
      if( _userstart != 0 ) {                  // Leaf map call?
        sb.append("Map ").append(_mapdone - _mapstart).append("ms (prep ").append(_userstart - _mapstart);
        sb.append("ms, user ").append(_closestart-_userstart);
        sb.append("ms, closeChk ").append(_mapdone-_closestart).append("ms), ");
      }
      sb.append("Red ").append(_onCdone - _onCstart);
      sb.append("ms (locRed ").append(_reducedone-_onCstart).append("ms");
      if( _remoteBlkDone!=0 ) {
        sb.append(  ", close " ).append(_closeLocalDone-    _reducedone);
        sb.append("ms, remBlk ").append( _remoteBlkDone-_closeLocalDone);
        sb.append("ms, locBlk ").append(  _localBlkDone- _remoteBlkDone);
        sb.append("ms, close " ).append(       _onCdone-  _localBlkDone);
        sb.append("ms, size "  ).append(PrettyPrint.bytes(_size_rez0)).append("+").append(PrettyPrint.bytes(_size_rez1));
      }
      sb.append(")\n");
      return sb;
    }
  }

  // Support for fluid-programming with strong types
  protected T self() { return (T)this; }

  /** Invokes the map/reduce computation over the given Vecs.  This call is
   *  blocking. */
  public final T doAll( Vec... vecs ) { return doAll(null,vecs); }
  public final T doAll(byte[] types, Vec... vecs ) { return doAll(types,new Frame(vecs), false); }
  public final T doAll(byte type, Vec... vecs ) { return doAll(new byte[]{type},new Frame(vecs), false); }
  public final T doAll( Vec vec, boolean run_local ) { return doAll(null,vec, run_local); }
  public final T doAll(byte[] types, Vec vec, boolean run_local ) { return doAll(types,new Frame(vec), run_local); }

  /** Invokes the map/reduce computation over the given Frame.  This call is
   *  blocking.  */
  public final T doAll( Frame fr, boolean run_local) { return doAll(null,fr, run_local); }
  public final T doAll( Frame fr ) { return doAll(null,fr, false); }
  public final T doAll( byte[] types, Frame fr) {return doAll(types,fr,false);}
  public final T doAll( byte type, Frame fr) {return doAll(new byte[]{type},fr,false);}
  public final T doAll( byte[] types, Frame fr, boolean run_local) {
    dfork(types,fr, run_local);
    return getResult();
  }
  // Output is several vecs of the same type
  public final T doAll( int nouts, byte type, Frame fr) {
    byte[] types = new byte[nouts];
    Arrays.fill(types, type);
    return doAll(types,fr,false);
  }

  // Special mode doing 1 map per key.  No frame
  public T doAll( Key... keys ) {
    dfork(keys);
    return getResult();         // Block For All
  }
  // Special mode doing 1 map per key.  No frame
  public void dfork(Key... keys ) {
    _topGlobal = true;
    _keys = keys;
    _nlo = selfidx(); _nhi = (short)H2O.CLOUD.size(); // Do Whole Cloud
    setupLocal0();              // Local setup
    H2O.submitTask(this);       // Begin normal execution on a FJ thread
  }

  // Special mode to run once-per-node
  public T doAllNodes() { return doAll((Key[])null); }

  public void asyncExecOnAllNodes() { dfork((Key[]) null); }

  /**
   * Invokes the map/reduce computation over the given Vec instances and produces
   * <code>outputs</code> Vec instances. This call is asynchronous. It returns 'this', on
   * which <code>getResult</code> may be invoked by the caller to block for pending
   * computation to complete.
   *
   * @param types The type of output Vec instances to create.
   * @param vecs The input set of Vec instances upon which computation is performed.
   * @return this
   */
  public final T dfork( byte[] types, Vec... vecs) { return dfork(types,new Frame(vecs),false); }

  public final T dfork(Vec... vecs){ return dfork(null,new Frame(vecs),false); }
  /**
   * Invokes the map/reduce computation over the given Frame instance. This call is
   * asynchronous. It returns 'this', on which <code>getResult</code> may be invoked
   * by the caller to block for pending computation to complete. This call produces no
   * output Vec instances or Frame instances.
   *
   * @param fr Perform the computation on this Frame instance.
   * @return this
   */
  public final T dfork(Frame fr){ return dfork(null,fr,false); }

  /** Fork the task in strictly non-blocking fashion.
   *  Same functionality as dfork, but does not raise priority, so user is should
   *  *never* block on it.
   *  Because it does not raise priority, these can be tail-call chained together
   *  for any length.
   */
  public final T dfork( byte[] types, Frame fr, boolean run_local) {
    _topGlobal = true;
    _output_types = types;
    if( types != null && types.length > 0 )
      _vid = fr.anyVec().group().reserveKeys(types.length);
    _fr = fr;                   // Record vectors to work on
    _nlo = selfidx(); _nhi = (short)H2O.CLOUD.size(); // Do Whole Cloud
    _run_local = run_local;     // Run locally by copying data, or run globally?
    setupLocal0();              // Local setup
    H2O.submitTask(this);       // Begin normal execution on a FJ thread
    return self();
  }

  /** Block for and get any final results from a dfork'd MRTask.
   *  Note: the desired name 'get' is final in ForkJoinTask.  */
  public final T getResult(boolean fjManagedBlock) {
    assert getCompleter()==null; // No completer allowed here; FJ never awakens threads with completers
    do {
      try {
        if(fjManagedBlock)
          ForkJoinPool.managedBlock(this);
        else
          // For the cases when we really want to block this thread without FJ framework scheduling a new worker thread.
          // Model use is in MultifileParseTask - we want to be parsing at most cluster ncores files in parallel.
          block();
        join(); // Throw any exception the map call threw
      } catch (InterruptedException ignore) {
        // do nothing
      } catch (Throwable re) {
        onExceptionalCompletion(re,null); // block for left and rite
        throw (re instanceof DistributedException)?new DistributedException(re.getMessage(),re.getCause()):new DistributedException(re);
      }
    } while( !isReleasable());
    assert _topGlobal:"lost top global flag";
    return self();
  }
  /** Block for and get any final results from a dfork'd MRTask.
   *  Note: the desired name 'get' is final in ForkJoinTask.  */
  public final T getResult() {return getResult(true);}

  // Return true if blocking is unnecessary, which is true if the Task isDone.
  public boolean isReleasable() {  return isDone();  }
  // Possibly blocks the current thread.  Returns true if isReleasable would
  // return true.  Used by the FJ Pool management to spawn threads to prevent
  // deadlock is otherwise all threads would block on waits.
  public boolean block() throws InterruptedException {
    while( !isDone() ) join();
    return true;
  }

  /** Called once on remote at top level, probably with a subset of the cloud.
   *  Called internal by D/F/J.  Not expected to be user-called.  */
  @Override public final void dinvoke(H2ONode sender) {
    setupLocal0();              // Local setup
    H2O.submitTask(this);
  }

  /*
   * Set top-level fields and fire off remote work (if there is any to do) to 2 selected
   * child JVM/nodes. Setup for local work: fire off any global work to cloud neighbors; do all
   * chunks; call user's init.
   */
  private void setupLocal0() {
    if(_profile != null)
      (_profile = new MRProfile(this))._localstart = System.currentTimeMillis();
    // Make a blockable Futures for both internal and user work to block on.
    _fs = new Futures();
    _topLocal = true;
    // Check for global vs local work
    int selfidx = selfidx();
    int nlo = subShift(selfidx);
    assert nlo < _nhi;
    final int nmid = (nlo+_nhi)>>>1; // Mid-point

    // Run remote IF:
    // - Not forced to run local (no remote jobs allowed) AND
    // - - There's remote work, or Client mode (always remote work)
    if( (!_run_local) && ((nlo+1 < _nhi) || H2O.ARGS.client) ) {
      if(_profile!=null) _profile._rpcLstart = System.currentTimeMillis();
      _nleft = remote_compute(H2O.ARGS.client ? nlo : nlo+1,nmid);
      if(_profile!=null) _profile._rpcRstart = System.currentTimeMillis();
      _nrite = remote_compute( nmid,_nhi);
      if(_profile!=null) _profile._rpcRdone  = System.currentTimeMillis();
    } else {
      if(_profile!=null)
        _profile._rpcLstart = _profile._rpcRstart = _profile._rpcRdone = System.currentTimeMillis();
    }

    if( _fr != null ) {                       // Doing a Frame
      _lo = 0;  _hi = _fr.numCols()==0 ? 0 : _fr.anyVec().nChunks(); // Do All Chunks
      // get the Vecs from the K/V store, to avoid racing fetches from the map calls
      _fr.vecs();
    } else if( _keys != null ) {    // Else doing a set of Keys
      _lo = 0;  _hi = _keys.length; // Do All Keys
    }
    // Setup any user's shared local structures for both normal cluster nodes
    // and any client; want this for possible reduction ONTO client
    setupLocal();
    if(_profile!=null) _profile._localdone = System.currentTimeMillis();
  }

  // Make an RPC call to some node in the middle of the given range.  Add a
  // pending completion to self, so that we complete when the RPC completes.
  private RPC<T> remote_compute( int nlo, int nhi ) {
    if( nlo < nhi ) {  // have remote work
      int node = addShift(nlo);
      assert node != H2O.SELF.index(); // Not the same as selfidx() if this is a client
      T mrt = copyAndInit();
      mrt._nhi = (short) nhi;
      addToPendingCount(1); // Not complete until the RPC returns
      // Set self up as needing completion by this RPC: when the ACK comes back
      // we'll get a wakeup.
      // Note the subtle inter-play of onCompletion madness here:
      // - when run on the remote, the RPCCall (NOT RPC!) is completed by the
      //   last map/compute2 call, signals end of the remote work, and ACK's
      //   back the result. i.e., last-map calls RPCCall.onCompletion.
      // - when launched on the local (right here, in this next line of code)
      //   the completed RPC calls our self completion.  i.e. the completed RPC
      //   calls MRTask.onCompletion
      return new RPC<>(H2O.CLOUD._memary[node], mrt).addCompleter(this).call();
    }
    return null; // nlo >= nhi => no remote work
  }

  /** Called from FJ threads to do local work.  The first called Task (which is
   *  also the last one to Complete) also reduces any global work.  Called
   *  internal by F/J.  Not expected to be user-called.  */
  @Override public final void compute2() {
    assert _left == null && _rite == null && _res == null;
    if(_profile!=null) _profile._mapstart = System.currentTimeMillis();
    if( (_hi-_lo) >= 2 ) { // Multi-chunk case: just divide-and-conquer to 1 chunk
      final int mid = (_lo+_hi)>>>1; // Mid-point
      _left = copyAndInit();
      _rite = copyAndInit();
      _left._hi = mid;          // Reset mid-point
      _rite._lo = mid;          // Also set self mid-point
      addToPendingCount(1);     // One fork awaiting completion
      if( !isCompletedAbnormally() ) _left.fork();     // Runs in another thread/FJ instance
      if( !isCompletedAbnormally() ) _rite.compute2(); // Runs in THIS F/J thread
      if(_profile!=null) _profile._mapdone = System.currentTimeMillis();
      return;                   // Not complete until the fork completes
    }
    // Zero or 1 chunks, and further chunk might not be homed here
    if( _fr==null ) {           // No Frame, so doing Keys?
      if( _keys == null ||     // Once-per-node mode
          _hi > _lo && _keys[_lo].home() ) {
        assert(_keys == null || !H2O.ARGS.client) : "Client node should not process any keys in MRTask!";
        if(_profile!=null) _profile._userstart = System.currentTimeMillis();
        if( _keys != null ) map(_keys[_lo]);
        _res = self();        // Save results since called map() at least once!
        if(_profile!=null) _profile._closestart = System.currentTimeMillis();
      }
    } else if( _hi > _lo ) {    // Frame, Single chunk?
      Vec v0 = _fr.anyVec();
      if( _run_local || v0.chunkKey(_lo).home() ) { // And chunk is homed here?
        assert(_run_local || !H2O.ARGS.client) : "Client node should not process any keys in MRTask!";

        // Make decompression chunk headers for these chunks
        Vec vecs[] = _fr.vecs();
        Chunk bvs[] = new Chunk[vecs.length];
        NewChunk [] appendableChunks = null;
        for( int i=0; i<vecs.length; i++ )
          if( vecs[i] != null ) {
            assert _run_local || vecs[i].chunkKey(_lo).home()
              : "Chunk="+_lo+" v0="+v0+", k="+v0.chunkKey(_lo)+"   v["+i+"]="+vecs[i]+", k="+vecs[i].chunkKey(_lo);
            bvs[i] = vecs[i].chunkForChunkIdx(_lo);
          }

        if(_output_types != null) {
          final VectorGroup vg = vecs[0].group();
          _appendables = new AppendableVec[_output_types.length];
          appendableChunks = new NewChunk[_output_types.length];
          for(int i = 0; i < _appendables.length; ++i) {
            _appendables[i] = new AppendableVec(vg.vecKey(_vid+i),_output_types[i]);
            appendableChunks[i] = _appendables[i].chunkForChunkIdx(_lo);
          }
        }
        // Call all the various map() calls that apply
        if(_profile!=null)
          _profile._userstart = System.currentTimeMillis();
        if( _fr.vecs().length == 1 ) map(bvs[0]);
        if( _fr.vecs().length == 2 ) map(bvs[0], bvs[1]);
        if( _fr.vecs().length == 3 ) map(bvs[0], bvs[1], bvs[2]);
        if( true                  )  map(bvs );
        if( _output_types != null && _output_types.length == 1 ) { // convenience versions for cases with single output.
          if( appendableChunks == null ) throw H2O.fail(); // Silence IdeaJ warnings
          if( _fr.vecs().length == 1 ) map(bvs[0], appendableChunks[0]);
          if( _fr.vecs().length == 2 ) map(bvs[0], bvs[1],appendableChunks[0]);
          //if( _fr.vecs().length == 3 ) map(bvs[0], bvs[1], bvs[2],appendableChunks[0]);
          //if( true                  )  map(bvs,    appendableChunks[0]);
        }
        if( _output_types != null && _output_types.length == 2) { // convenience versions for cases with 2 outputs (e.g split).
          if( appendableChunks == null ) throw H2O.fail(); // Silence IdeaJ warnings
          if( _fr.vecs().length == 1 ) map(bvs[0], appendableChunks[0],appendableChunks[1]);
          //if( _fr.vecs().length == 2 ) map(bvs[0], bvs[1],appendableChunks[0],appendableChunks[1]);
          //if( _fr.vecs().length == 3 ) map(bvs[0], bvs[1], bvs[2],appendableChunks[0],appendableChunks[1]);
          if( true                  )  map(bvs,    appendableChunks[0],appendableChunks[1]);
        }
        map(bvs,appendableChunks);
        _res = self();          // Save results since called map() at least once!
        // Further D/K/V put any new vec results.
        if(_profile!=null)
          _profile._closestart = System.currentTimeMillis();
        for( Chunk bv : bvs )  bv.close(_lo,_fs);
        if( _output_types != null) for(NewChunk nch:appendableChunks)nch.close(_lo, _fs);
      }
    }
    if(_profile!=null)
      _profile._mapdone = System.currentTimeMillis();
    tryComplete();
  }

  /** OnCompletion - reduce the left and right into self.  Called internal by
   *  F/J.  Not expected to be user-called. */
  @Override public final void onCompletion( CountedCompleter caller ) {
    if(_profile!=null) _profile._onCstart = System.currentTimeMillis();
    // Reduce results into 'this' so they collapse going up the execution tree.
    // NULL out child-references so we don't accidentally keep large subtrees
    // alive since each one may be holding large partial results.
    reduce2(_left); _left = null;
    reduce2(_rite); _rite = null;
    if(_profile!=null) _profile._reducedone = System.currentTimeMillis();
    // Only on the top local call, have more completion work
    if( _topLocal ) postLocal0();
    if(_profile!=null) _profile._onCdone = System.currentTimeMillis();
  }


  // Call 'reduce' on pairs of mapped MRTask's.
  // Collect all pending Futures from both parties as well.
  private void reduce2( MRTask<T> mrt ) {
    if( mrt == null ) return;
    if(_profile!=null)
      _profile.gather(mrt._profile,0);
    if( _res == null ) _res = mrt._res;
    else if( mrt._res != null ) _res.reduce4(mrt._res);
    // Futures are shared on local node and transient (so no remote updates)
    assert _fs == mrt._fs;
  }

  protected void postGlobal(){}

  // Work done after all the main local work is done.
  // Gather/reduce remote work.
  // User cleanup.
  // Block for other queued pending tasks.
  // Copy any final results into 'this', such that a return of 'this' has the results.
  private void postLocal0() {
    closeLocal();               // User's node-local cleanup
    if(_profile!=null) _profile._closeLocalDone = System.currentTimeMillis();
    reduce3(_nleft);            // Reduce global results from neighbors.
    reduce3(_nrite);
    if(_profile!=null) _profile._remoteBlkDone = System.currentTimeMillis();
    _fs.blockForPending();      // Block any pending user tasks
    if(_profile!=null) _profile._localBlkDone = System.currentTimeMillis();
    // Finally, must return all results in 'this' because that is the API -
    // what the user expects
    if( _res == null ) _nhi=-1; // Flag for no local results *at all*
    else if( _res != this ) {   // There is a local result, and its not self
      _res._profile = _profile; // Use my profile (not child's)
      copyOver(_res);           // So copy into self
    }
    if( _topGlobal ) {
      if (_fr != null)     // Do any post-writing work (zap rollup fields, etc)
        _fr.postWrite(_fs).blockForPending();
      postGlobal();             // User's continuation work
    }

  }

  // Block for RPCs to complete, then reduce global results into self results
  private void reduce3( RPC<T> rpc ) {
    if( rpc == null ) return;
    T mrt = rpc.get();          // This is a blocking remote call
    // Note: because _fs is transient it is not set or cleared by the RPC.
    // Because the MRT object is a clone of 'self' it's likely to contain a ptr
    // to the self _fs which will be not-null and still have local pending
    // blocks.  Not much can be asserted there.
    if(_profile!=null)
      _profile.gather(mrt._profile, rpc.size_rez());
    // Unlike reduce2, results are in mrt directly not mrt._res.
    if( mrt._nhi != -1L ) {     // Any results at all?
      if( _res == null ) _res = mrt;
      else _res.reduce4(mrt);
    }
  }

  /** Call user's reduction.  Also reduce any new AppendableVecs.  Called
   *  internal by F/J.  Not expected to be user-called.  */
  void reduce4( T mrt ) {
    // Reduce any AppendableVecs
    if( _output_types != null )
      for( int i=0; i<_appendables.length; i++ )
        _appendables[i].reduce(mrt._appendables[i]);
    if( _ex == null ) _ex = mrt._ex;
    // User's reduction
    reduce(mrt);
  }

  // Full local work-tree cancellation
  void self_cancel2() { if( !isDone() ) { cancel(true); self_cancel1(); } }
  private void self_cancel1() {
    T l = _left; if( l != null ) { l.self_cancel2(); }
    T r = _rite; if( r != null ) { r.self_cancel2(); }
  }

  /** Cancel/kill all work as we can, then rethrow... do not invisibly swallow
   *  exceptions (which is the F/J default).  Called internal by F/J.  Not
   *  expected to be user-called.  */
  @Override public final boolean onExceptionalCompletion( Throwable ex, CountedCompleter caller ) {
    self_cancel1();
    // Block for completion - we don't want the work, but we want all the
    // workers stopped before we complete this task.  Otherwise this task quits
    // early and begins post-task processing (generally cleanup from the
    // exception) but the work is still on-going - often trying to use the same
    // Keys as are being cleaned-up!

    // Since blocking can throw (generally the same exception, again and again)
    // catch & ignore, keeping only the first one we already got.
    RPC<T> nl = _nleft; if( nl != null ) try { nl.get(); } catch( Throwable ignore ) { } _nleft = null;
    RPC<T> nr = _nrite; if( nr != null ) try { nr.get(); } catch( Throwable ignore ) { } _nrite = null;
    return true;
  }

  // Make copy, setting final-field completer and clearing out a bunch of fields
  private T copyAndInit() {
    T x = clone();
    x._topGlobal = false;
    x.setCompleter(this); // Set completer, what used to be a final field
    x._topLocal = false;  // Not a top job
    x._nleft = x._nrite = null;
    x. _left = x. _rite = null;
    x._fs = _fs;
    if( _profile!=null )  x._profile = new MRProfile(this);
    else                  x._profile = null;    // Clone needs its own profile
    x.setPendingCount(0); // Volatile write for completer field; reset pending count also
    return x;
  }
}
