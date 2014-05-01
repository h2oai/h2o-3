package water;

import jsr166y.CountedCompleter;
import jsr166y.ForkJoinPool;
import water.fvec.*;
import water.util.PrettyPrint;
import water.fvec.Vec.VectorGroup;

/**
 * Map/Reduce style distributed computation.
 * <nl>
 * MRTask provides several <code>map</code> and <code>reduce</code> methods that can be
 * overriden to specify a computation. Several instances of this class will be
 * created to distribute the computation over F/J threads and machines.  Non-transient
 * fields are copied and serialized to instances created for map invocations. Reduce
 * methods can store their results in fields. Results are serialized and reduced all the
 * way back to the invoking node. When the last reduce method has been called, fields
 * of the initial MRTask instance contains the computation results.
 * <nl>
 * Apart from small reduced POJO returned to the calling node, MRtask2 can
 * produce output vector(s) as a result.  These will have chunks co-located
 * with the input dataset, however, their number of lines will generally
 * differ, (so they won't be strictly compatible with the original). To produce
 * output vectors, call doAll.dfork version with required number of outputs and
 * override appropriate <code>map</code> call taking required number of
 * NewChunks.  MRTask will automatically close the new Appendable vecs and
 * produce an output frame with newly created Vecs.
 */
public abstract class MRTask<T extends MRTask<T>> extends DTask<T> implements ForkJoinPool.ManagedBlocker {

  /** The Vectors (or Keys) to work on. */
  public Frame _fr;
  public Key[] _keys;
  // appendables are treated separately (roll-ups computed in map/reduce style, can not be passed via K/V store).
  protected AppendableVec [] _appendables;
  private int _vid;
  private int _noutputs;
  // If TRUE, run entirely local - which will pull all the data locally.
  private boolean _run_local;

  private byte _priority;
  @Override public byte priority() { return _priority; }

  public Frame outputFrame(String [] names, String [][] domains){ return outputFrame(null,names,domains); }
  public Frame outputFrame(Key key, String [] names, String [][] domains){
    Futures fs = new Futures();
    Frame res = outputFrame(key, names, domains, fs);
    fs.blockForPending();
    return res;
  }
  public Frame outputFrame(Key key, String [] names, String [][] domains, Futures fs){
    if(_noutputs == 0)return null;
    Vec [] vecs = new Vec[_noutputs];
    for(int i = 0; i < _noutputs; ++i) {
      if( _appendables==null )  // Zero rows?
        vecs[i] = _fr.anyVec().makeZero();
      else {
        _appendables[i].setDomain(domains==null ? null : domains[i]);
        vecs[i] = _appendables[i].close(fs);
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
  public void map( Chunk c0, Chunk c1, NewChunk nc) { }
  public void map( Chunk c0, Chunk c1, NewChunk nc1, NewChunk nc2 ) { }

  /** Override with your map implementation.  This overload is given three
   * <strong>local</strong> input Chunks.  All map variants are called, but only one
   * is expected to be overridden. */
  public void map( Chunk c0, Chunk c1, Chunk c2 ) { }
  public void map( Chunk c0, Chunk c1, Chunk c2, NewChunk nc ) { }
  public void map( Chunk c0, Chunk c1, Chunk c2, NewChunk nc1, NewChunk nc2 ) { }

  /** Override with your map implementation.  This overload is given an array
   *  of <strong>local</strong> input Chunks, for Frames with arbitrary column
   *  numbers.  All map variants are called, but only one is expected to be
   *  overridden. */
  public void map( Chunk cs[] ) { }
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

  /** Internal field to track a range of remote nodes/JVMs to work on */
  protected short _nxx, _nhi;   // Range of Nodes to work on - remotely
  private int addShift( int x ) { x += _nxx; int sz = H2O.CLOUD.size(); return x < sz ? x : x-sz; }
  private int subShift( int x ) { x -= _nxx; int sz = H2O.CLOUD.size(); return x <  0 ? x+sz : x; }
  /** Internal field to track the left & right remote nodes/JVMs to work on */
  transient protected RPC<T> _nleft, _nrite;
  /** Internal field to track if this is a top-level local call */
  transient protected boolean _topLocal; // Top-level local call, returning results over the wire
  /** Internal field to track a range of local Chunks to work on */
  transient protected int _lo, _hi;   // Range of Chunks to work on - locally
  /** Internal field to track the left & right sub-range of chunks to work on */
  transient protected T _left, _rite; // In-progress execution tree

  transient private T _res;           // Result

  /** We can add more things to block on - in case we want a bunch of lazy
   *  tasks produced by children to all end before this top-level task ends.
   *  Semantically, these will all complete before we return from the top-level
   *  task.  Pragmatically, we block on a finer grained basis. */
  transient protected Futures _fs; // More things to block on

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
    long _onCstart, _reducedone, _remoteBlkDone, _localBlkDone, _onCdone; // REDUCE phase
    // If we split the job left/right, then we get a total recording of the
    // last job, and the exec time & completion time of 1st job done.
    long _time1st, _done1st;
    int _size_rez0, _size_rez1; // i/o size in bytes during reduce
    MRProfile _last;
    long sumTime() { return _onCdone - (_localstart==0 ? _mapstart : _localstart); }
    void gather( MRProfile p, int size_rez ) {
      p._clz=null;
      if( _last == null ) _last=p;
      else {
        MRProfile first = _last._onCdone <= p._onCdone ? _last : p;
        _last           = _last._onCdone >  p._onCdone ? _last : p;
        if( first._onCdone > _done1st ) { _time1st = first.sumTime(); _done1st = first._onCdone; }
      }
      if( size_rez !=0 )        // Record i/o result size
        if( _size_rez0 == 0 ) {      _size_rez0=size_rez; }
        else { /*assert _size_rez1==0;*/ _size_rez1=size_rez; }
      assert _last._onCdone >= _done1st;
    }

    @Override public String toString() { return print(new StringBuilder(),0).toString(); }
    private StringBuilder print(StringBuilder sb, int d) {
      if( d==0 ) sb.append(_clz).append("\n");
      for( int i=0; i<d; i++ ) sb.append("  ");
      if( _localstart != 0 ) sb.append("Node local ").append(_localdone - _localstart).append("ms, ");
      if( _userstart == 0 ) {   // Forked job?
        sb.append("Slow wait ").append(_mapstart-_localdone).append("ms + work ").append(_last.sumTime()).append("ms, ");
        sb.append("Fast work ").append(_time1st).append("ms + wait ").append(_onCstart-_done1st).append("ms\n");
        _last.print(sb,d+1); // Nested slow-path print
        for( int i=0; i<d; i++ ) sb.append("  ");
        sb.append("join-i/o ").append(_onCstart-_last._onCdone).append("ms, ");
      } else {                  // Leaf map call?
        sb.append("Map ").append(_mapdone - _mapstart).append("ms (prep ").append(_userstart - _mapstart);
        sb.append("ms, user ").append(_closestart-_userstart);
        sb.append("ms, closeChk ").append(_mapdone-_closestart).append("ms), ");
      }
      sb.append("Red ").append(_onCdone - _onCstart).append("ms (locRed ");
      sb.append(_reducedone-_onCstart).append("ms");
      if( _remoteBlkDone!=0 ) {
        sb.append(", remBlk ").append(_remoteBlkDone-_reducedone).append("ms, locBlk ");
        sb.append(_localBlkDone-_remoteBlkDone).append("ms, close ");
        sb.append(_onCdone-_localBlkDone).append("ms, size ");
        sb.append(PrettyPrint.bytes(_size_rez0)).append("+").append(PrettyPrint.bytes(_size_rez1));
      }
      sb.append(")\n");
      return sb;
    }
  }
  MRProfile _profile;
  public String profString() { return _profile.toString(); }

  // Support for fluid-programming with strong types
  private T self() { return (T)this; }

  /** Invokes the map/reduce computation over the given Vecs.  This call is
   *  blocking. */
  public final T doAll( Vec... vecs ) { return doAll(0,vecs); }
  public final T doAll(int outputs, Vec... vecs ) { return doAll(outputs,new Frame(null,vecs), false); }

  /** Invokes the map/reduce computation over the given Frame.  This call is
   *  blocking.  */
  public final T doAll( Frame fr, boolean run_local) { return doAll(0,fr, run_local); }
  public final T doAll( Frame fr ) { return doAll(0,fr, false); }
  public final T doAll( int outputs, Frame fr) {return doAll(outputs,fr,false);}
  public final T doAll( int outputs, Frame fr, boolean run_local) {
    dfork(outputs,fr, run_local);
    return getResult();
  }

  public final void asyncExec(Vec... vecs){asyncExec(0,new Frame(vecs),false);}
  public final void asyncExec(Frame fr){asyncExec(0,fr,false);}

  /** Fork the task in strictly non-blocking fashion.
   *  Same functionality as dfork, but does not raise priority, so user is should
   *  *never* block on it.
   *  Because it does not raise priority, these can be tail-call chained together
   *  for any length.
   */
  public final void asyncExec( int outputs, Frame fr, boolean run_local){
    // Use first readable vector to gate home/not-home
    if((_noutputs = outputs) > 0) _vid = fr.anyVec().group().reserveKeys(outputs);
    _fr = fr;                   // Record vectors to work on
    _nxx = (short)H2O.SELF.index(); _nhi = (short)H2O.CLOUD.size(); // Do Whole Cloud
    _run_local = run_local;     // Run locally by copying data, or run globally?
    setupLocal0();              // Local setup
    H2O.submitTask(this);       // Begin normal execution on a FJ thread
  }
  /** Invokes the map/reduce computation over the given Frame.  This call is
   *  asynchronous.  It returns 'this', on which getResult() can be invoked
   *  later to wait on the computation.  */
  public final T dfork( Vec...vecs ) {return dfork(0,vecs);}
  public T dfork( Frame fr ) {return dfork(0,fr,false);}
  public final T dfork( int outputs, Vec... vecs) {
    return dfork(outputs,new Frame(vecs),false);
  }
  public final T dfork( int outputs, Frame fr, boolean run_local) {
    // Raise the priority, so that if a thread blocks here, we are guaranteed
    // the task completes (perhaps using a higher-priority thread from the
    // upper thread pools).  This prevents thread deadlock.
    _priority = nextThrPriority();
    asyncExec(outputs,fr,run_local);
    return self();
  }

  /** Block for & get any final results from a dfork'd MRTask.
   *  Note: the desired name 'get' is final in ForkJoinTask.  */
  public final T getResult() {
    try { ForkJoinPool.managedBlock(this); }
    catch( InterruptedException ignore ) { }
    catch( RuntimeException re ) { if( !hasException() ) setException(re);  }
    DException.DistributedException de = getDException();
    if( de != null ) throw de;
    return self();
  }

  // Return true if blocking is unnecessary, which is true if the Task isDone.
  public boolean isReleasable() {  return isDone();  }
  // Possibly blocks the current thread.  Returns true if isReleasable would
  // return true.  Used by the FJ Pool management to spawn threads to prevent
  // deadlock is otherwise all threads would block on waits.
  public boolean block() {
    while( !isDone() ) join();
    return true;
  }

  /** Called once on remote at top level, probably with a subset of the cloud.
   *  Called internal by D/F/J.  Not expected to be user-called.  */
  @Override public final void dinvoke(H2ONode sender) {
    setupLocal0();              // Local setup
    try {
      compute2();               // Do The Main Work
    } catch( Throwable ex ) { setException(ex); throw ex; }
    // nothing here... must do any post-work-cleanup in onCompletion
  }

  // Special mode to run once-per-node
  public T doAllNodes() { return doAll((Key[])null); }

  // Special mode doing 1 map per key.  No frame
  public T doAll( Key... keys ) {
    _keys = keys;
    _nxx = (short)H2O.SELF.index(); _nhi = (short)H2O.CLOUD.size(); // Do Whole Cloud
    setupLocal0();              // Local setup
    H2O.submitTask(this);       // Begin normal execution on a FJ thread
    return getResult();         // Block For All
  }

  // Setup for local work: fire off any global work to cloud neighbors; do all
  // chunks; call user's init.
  private void setupLocal0() {
    assert _profile==null;
    _fs = new Futures();
    _profile = new MRProfile(this);
    _profile._localstart = System.currentTimeMillis();
    _topLocal = true;
    // Check for global vs local work
    int selfidx = H2O.SELF.index();
    int nlo = subShift(selfidx);
    assert nlo < _nhi;
    final int nmid = (nlo+_nhi)>>>1; // Mid-point
    if( !_run_local && nlo+1 < _nhi ) { // Have global work?
      _profile._rpcLstart = System.currentTimeMillis();
      _nleft = remote_compute(nlo+1,nmid);
      _profile._rpcRstart = System.currentTimeMillis();
      _nrite = remote_compute( nmid,_nhi);
      _profile._rpcRdone  = System.currentTimeMillis();
    }
    if( _fr != null ) {                       // Doing a Frame
      _lo = 0;  _hi = _fr.anyVec().nChunks(); // Do All Chunks
      // If we have any output vectors, make a blockable Futures for them to
      // block on.
      // get the Vecs from the K/V store, to avoid racing fetches from the map calls
      _fr.vecs();
    } else if( _keys != null ) {    // Else doing a set of Keys
      _lo = 0;  _hi = _keys.length; // Do All Keys
    }
    setupLocal();               // Setup any user's shared local structures
    _profile._localdone = System.currentTimeMillis();
  }

  // Make an RPC call to some node in the middle of the given range.  Add a
  // pending completion to self, so that we complete when the RPC completes.
  private RPC<T> remote_compute( int nlo, int nhi ) {
    // No remote work?
    if( !(nlo < nhi) ) return null;
    int node = addShift(nlo);
    assert node != H2O.SELF.index();
    T mrt = copyAndInit();
    mrt._nhi = (short)nhi;
    addToPendingCount(1);       // Not complete until the RPC returns
    // Set self up as needing completion by this RPC: when the ACK comes back
    // we'll get a wakeup.
    return new RPC<>(H2O.CLOUD._memary[node], mrt).addCompleter(this).call();
  }

  /** Called from FJ threads to do local work.  The first called Task (which is
   *  also the last one to Complete) also reduces any global work.  Called
   *  internal by F/J.  Not expected to be user-called.  */
  @Override public final void compute2() {
    assert _left == null && _rite == null && _res == null;
    _profile._mapstart = System.currentTimeMillis();
    if( _hi-_lo >= 2 ) { // Multi-chunk case: just divide-and-conquer to 1 chunk
      final int mid = (_lo+_hi)>>>1; // Mid-point
      _left = copyAndInit();
      _rite = copyAndInit();
      _left._profile = new MRProfile(this);
      _rite._profile = new MRProfile(this);
      _left._hi = mid;          // Reset mid-point
      _rite._lo = mid;          // Also set self mid-point
      addToPendingCount(1);     // One fork awaiting completion
      _left.fork();             // Runs in another thread/FJ instance
      _rite.compute2();         // Runs in THIS F/J thread
      _profile._mapdone = System.currentTimeMillis();
      return;                   // Not complete until the fork completes
    }
    // Zero or 1 chunks, and further chunk might not be homed here
    if( _fr==null ) {           // No Frame, so doing Keys?
      if( _keys == null ||     // Once-per-node mode
          _hi > _lo && _keys[_lo].home() ) {
        _profile._userstart = System.currentTimeMillis();
        if( _keys != null ) map(_keys[_lo]);
        _res = self();        // Save results since called map() at least once!
        _profile._closestart = System.currentTimeMillis();
      }
    } else if( _hi > _lo ) {    // Frame, Single chunk?
      Vec v0 = _fr.anyVec();
      if( _run_local || v0.chunkKey(_lo).home() ) { // And chunk is homed here?

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
        if(_noutputs > 0){
          final VectorGroup vg = vecs[0].group();
          _appendables = new AppendableVec[_noutputs];
          appendableChunks = new NewChunk[_noutputs];
          for(int i = 0; i < _appendables.length; ++i){
            _appendables[i] = new AppendableVec(vg.vecKey(_vid+i));
            appendableChunks[i] = (NewChunk)_appendables[i].chunkForChunkIdx(_lo);
          }
        }
        // Call all the various map() calls that apply
        _profile._userstart = System.currentTimeMillis();
        if( _fr.vecs().length == 1 ) map(bvs[0]);
        if( _fr.vecs().length == 2 ) map(bvs[0], bvs[1]);
        if( _fr.vecs().length == 3 ) map(bvs[0], bvs[1], bvs[2]);
        if( true                  )  map(bvs );
        if(_noutputs == 1){ // convenience versions for cases with single output.
          if( appendableChunks == null ) throw H2O.fail(); // Silence IdeaJ warnings
          if( _fr.vecs().length == 1 ) map(bvs[0], appendableChunks[0]);
          if( _fr.vecs().length == 2 ) map(bvs[0], bvs[1],appendableChunks[0]);
          if( _fr.vecs().length == 3 ) map(bvs[0], bvs[1], bvs[2],appendableChunks[0]);
          if( true                  )  map(bvs,    appendableChunks[0]);
        }
        if(_noutputs == 2){ // convenience versions for cases with 2 outputs (e.g split).
          if( appendableChunks == null ) throw H2O.fail(); // Silence IdeaJ warnings
          if( _fr.vecs().length == 1 ) map(bvs[0], appendableChunks[0],appendableChunks[1]);
          if( _fr.vecs().length == 2 ) map(bvs[0], bvs[1],appendableChunks[0],appendableChunks[1]);
          if( _fr.vecs().length == 3 ) map(bvs[0], bvs[1], bvs[2],appendableChunks[0],appendableChunks[1]);
          if( true                  )  map(bvs,    appendableChunks[0],appendableChunks[1]);
        }
        map(bvs,appendableChunks);
        _res = self();          // Save results since called map() at least once!
        // Further D/K/V put any new vec results.
        _profile._closestart = System.currentTimeMillis();
        for( Chunk bv : bvs ) bv.close(_lo,_fs);
        if(_noutputs > 0) for(NewChunk nch:appendableChunks)nch.close(_lo, _fs);
      }
    }
    _profile._mapdone = System.currentTimeMillis();
    tryComplete();              // And this task is complete
  }

  /** OnCompletion - reduce the left & right into self.  Called internal by
   *  F/J.  Not expected to be user-called. */
  @Override public final void onCompletion( CountedCompleter caller ) {
    _profile._onCstart = System.currentTimeMillis();
    // Reduce results into 'this' so they collapse going up the execution tree.
    // NULL out child-references so we don't accidentally keep large subtrees
    // alive since each one may be holding large partial results.
    reduce2(_left); _left = null;
    reduce2(_rite); _rite = null;
    // Only on the top local call, have more completion work
    _profile._reducedone = System.currentTimeMillis();
    if( _topLocal ) postLocal();
    _profile._onCdone = System.currentTimeMillis();
  }

  // Call 'reduce' on pairs of mapped MRTask's.
  // Collect all pending Futures from both parties as well.
  private void reduce2( MRTask<T> mrt ) {
    if( mrt == null ) return;
    _profile.gather(mrt._profile,0);
    if( _res == null ) _res = mrt._res;
    else if( mrt._res != null ) _res.reduce4(mrt._res);
    // Futures are shared on local node and transient (so no remote updates)
    assert _fs == mrt._fs;
  }

  protected void postGlobal(){}
  // Work done after all the main local work is done.
  // Gather/reduce remote work.
  // Block for other queued pending tasks.
  // Copy any final results into 'this', such that a return of 'this' has the results.
  private void postLocal() {
    reduce3(_nleft);            // Reduce global results from neighbors.
    reduce3(_nrite);
    _profile._remoteBlkDone = System.currentTimeMillis();
    _fs.blockForPending();
    _profile._localBlkDone = System.currentTimeMillis();
    // Finally, must return all results in 'this' because that is the API -
    // what the user expects
    int nlo = subShift(H2O.SELF.index());
    int nhi = _nhi;             // Save before copyOver crushes them
    if( _res == null ) _nhi=-1; // Flag for no local results *at all*
    else if( _res != this ) {   // There is a local result, and its not self
      _res._profile = _profile; // Use my profile (not childs)
      copyOver(_res);           // So copy into self
    }
    closeLocal();          // User's node-local cleanup
    if( _fr != null )      // Do any post-writing work (zap rollup fields, etc)
      _fr.postWrite(_fs).blockForPending();
    if( nlo==0 && nhi == H2O.CLOUD.size() )
      postGlobal();             // User's continuation work
  }

  // Block for RPCs to complete, then reduce global results into self results
  private void reduce3( RPC<T> rpc ) {
    if( rpc == null ) return;
    T mrt = rpc.get();          // This is a blocking remote call
    // Note: because _fs is transient it is not set or cleared by the RPC.
    // Because the MRT object is a clone of 'self' it's likely to contain a ptr
    // to the self _fs which will be not-null and still have local pending
    // blocks.  Not much can be asserted there.
    _profile.gather(mrt._profile, rpc.size_rez());
    // Unlike reduce2, results are in mrt directly not mrt._res.
    if( mrt._nhi != -1L ) {     // Any results at all?
      if( _res == null ) _res = mrt;
      else _res.reduce4(mrt);
    }
  }

  /** Call user's reduction.  Also reduce any new AppendableVecs.  Called
   *  internal by F/J.  Not expected to be user-called.  */
  protected void reduce4( T mrt ) {
    // Reduce any AppendableVecs
    if( _noutputs > 0 )
      for( int i=0; i<_appendables.length; i++ )
        _appendables[i].reduce(mrt._appendables[i]);
    if( _ex == null ) _ex = mrt._ex;
    // User's reduction
    reduce(mrt);
  }

  /** Cancel/kill all work as we can, then rethrow... do not invisibly swallow
   *  exceptions (which is the F/J default).  Called internal by F/J.  Not
   *  expected to be user-called.  */
  @Override public final boolean onExceptionalCompletion( Throwable ex, CountedCompleter caller ) {
    if( !hasException() ) setException(ex);
    if( _nleft != null ) _nleft.cancel(true); _nleft = null;
    if( _nrite != null ) _nrite.cancel(true); _nrite = null;
    if(  _left != null )  _left.cancel(true);  _left = null;
    if(  _rite != null )  _rite.cancel(true);  _rite = null;
    return super.onExceptionalCompletion(ex, caller);
  }

  // Make copy, setting final-field completer and clearing out a bunch of fields 
  private T copyAndInit() { 
    T x = (T)clone();
    x.setCompleter(this); // Set completer, what used to be a final field
    x._topLocal = false;  // Not a top job
    x._nleft = x._nrite = null;
    x. _left = x. _rite = null;
    x._fs = _fs;
    x._profile = null;    // Clone needs its own profile
    x.setPendingCount(0); // Volatile write for completer field; reset pending count also
    return x;
  }
}
