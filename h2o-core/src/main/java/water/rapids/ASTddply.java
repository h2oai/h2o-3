package water.rapids;

import sun.misc.Unsafe;
import water.*;
import water.fvec.*;
import water.nbhm.UtilUnsafe;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;


/** plyr's ddply: GroupBy by any other name.
 *  Sample AST: (h2o.ddply $frame {1;5;10} $fun)
 *
 *  First arg is the frame we'll be working over.
 *  Second arg is column selection to group by.
 *  Third arg is the function to apply to each group.
 */
public class ASTddply extends ASTOp {
  long[] _cols;
  String _fun;
  AST[] _fun_args;
  static final String VARS[] = new String[]{ "ary", "{cols}", "FUN"};
  public ASTddply( ) { super(VARS); }

  @Override String opStr(){ return "h2o.ddply";}
  @Override ASTOp make() {return new ASTddply();}

  @Override ASTddply parse_impl(Exec E) {
    // get the frame to work
    AST ary = E.parse();

    // Get the col ids
    AST s=E.parse();
    if( s instanceof ASTLongList) _cols = ((ASTLongList)s)._l;
    else if( s instanceof ASTNum) _cols = new long[]{(long)((ASTNum)s)._d};
    else throw new IllegalArgumentException("Columns expected to be a llist or number. Got: " + s.getClass());

    // get the fun
    _fun = ((ASTId)E.parse())._id;

    // get any fun args
    ArrayList<AST> fun_args = new ArrayList<>();
    while( !E.isEnd() )
      fun_args.add(E.parse());

    if (fun_args.size() > 0) {
      _fun_args = fun_args.toArray(new AST[fun_args.size()]);
    } else {
      _fun_args = null;
    }

    E.eatEnd();
    ASTddply res = (ASTddply)clone();
    res._asts = new AST[]{ary};
    return res;
  }

  @Override void apply(Env env) {
    Frame fr = env.popAry();    // The Frame to work on

    // sanity check cols
    for (long l : _cols) {
      if (l > fr.numCols() || l < 0) throw new IllegalArgumentException("Column "+(l+1)+" out of range for frame columns "+fr.numCols());
    }


    // *** LEGACY *** //
//    Was pondering a SIMD-like execution model, running the fcn "once" - but
//    in parallel for all groups.  But this isn't going to work: each fcn
//    execution will take different control paths.  Also the functions side-
//    effects' must only happen once, and they will make multiple passes over
//    the Frame passed in.
//
//    GroupIDs' can vary from 1 group to 1-per-row.  Are formed by the cross-
//    product of the selection cols.  Will be hashed to find Group - NBHML
//    mapping row-contents to group.  Index is a sample row.  NBHML per-node,
//    plus roll-ups.  Result/Value is Group structure pointing to NewChunks
//    holding row indices.
//
//    Pass 1: Find Groups.
//    Build a NBHSet of unique double[]'s holding selection cols.
//    These are the unique groups, found per-node, rolled-up globally
//    Record the rows belonging to each group, locally.
//    ddplyPass1 p1 = new ddplyPass1(true,_cols).doAll(fr);
    // *** LEGACY *** //



    // End up building a "transient" Frame for each group anyhow.
    // So finding the groups and the size of each group is relatively cheap!
    // pass1A, finds the number of groups and the size of each group.
    // as a follow up to pass1A, pass1B fills in an array of longs for each group
    Pass1A p1a = new Pass1A(_cols).doAll(fr);                    // pass 1 over all data
    H2O.submitTask(new ParallelGroupProcess(p1a._grps)).join();  // parallel pass over all groups to instantiate concurrent array list...
    new Pass1B(p1a._grps,_cols).doAll(fr);                       // nutha pass over sdata

    // pass2 here does the nominal work of building all of the groups.
    // for lots of tiny groups, this is probably lots of data transfer
    // this chokes the H2O cloud and can even cause it to OOM!
    // this issue is addressed by ASTGroupBy
    Pass2 p2;
    Group[] grps = p1a._grps._g.toArray(new Group[p1a._grps._g.size()]);
    H2O.submitTask(p2=new Pass2(fr,grps)).join();

    // Pass 3: Send Groups 'round the cluster
    Key[] groupFrames = p2._keys;

    Pass3 p3;
    H2O.submitTask(p3=new Pass3(groupFrames,ASTOp.get(_fun).make(), grps,_fun_args)).join();
    Vec layoutVec = Vec.makeZero(p3._remoteTasks.length);
    final RemoteRapids[] results = p3._remoteTasks;

    final int ncols = results[0]._result.length;

    String names[] = new String[ncols];
    String[][] domains = new String[ncols][];
    int i=0;
    for(;i<_cols.length;) {
      names[i] = fr.names()[(int)_cols[i]];
      domains[i] = fr.domains()[(int)_cols[i++]];
    }
    int j=1;
    for(;i<ncols;) {
      names[i++] = "C"+j++;
    }

    Frame fr2 = new MRTask() {
      @Override public void map(Chunk[] c, NewChunk[] nc) {
        int start = (int)c[0].start();
        double d;
        for(int i=0;i<c[0]._len;++i) {
          d = results[i+start]._result[nc.length-1];
          if( Double.isNaN(d) ) continue; // skip NA group results
          for(int j=0;j<nc.length;++j)
            nc[j].addNum(results[i+start]._result[j]);
        }
      }
    }.doAll(ncols, layoutVec).outputFrame(names,domains);

    env.pushAry(fr2);

//    // Auto-Rebalance afterwards, as ddply's often make few fat chunks
//    int chunks = (int)Math.min( 4 * H2O.NUMCPUS * H2O.CLOUD.size(), res.numRows());
//    if( res.anyVec().nChunks() < chunks && res.numRows() > 10*chunks ) { // Rebalance
//      Key newKey = Key.make(".chunks" + chunks);
//      RebalanceDataSet rb = new RebalanceDataSet(res, newKey, chunks);
//      H2O.submitTask(rb);
//      rb.join();
//      res.delete();
//      res = DKV.getGet(newKey);
//    }
//
//    // Delete the group row vecs
//    env.pushAry(res);
  }

  // ---
  // Group description: unpacked selected double columns
  public static class Group extends ASTGroupBy.G {
    public Group() { super(); }
    public Group(int len) { super(len); }
    public Group( double ds[] ) { super(ds); }

    public void add(long l) { a.add(l); }
    ConcurrentFixedSizeArrayList a;
  }


  private static class Pass1A extends MRTask<Pass1A> {
    private final long _gbCols[];
    ASTGroupBy.IcedNBHS<Group> _grps;
    Pass1A(long[] cols) { _gbCols=cols; }
    @Override public void setupLocal() { _grps = new ASTGroupBy.IcedNBHS<>(); }
    @Override public void map(Chunk[] c) {
      Group g = new Group(_gbCols.length);
      Group gOld;
      for(int i=0;i<c[0]._len;++i) {
        g.fill(i,c,_gbCols);
        if( _grps.add(g) ) {
          gOld=g;
          g= new Group(_gbCols.length);
        } else {
          gOld=_grps.get(g);
          if( gOld==null )
            while( gOld==null ) gOld=_grps.get(g);
        }
        long cnt=gOld._N;
        while( !Group.CAS_N(gOld,cnt,cnt+1))
          cnt=gOld._N;
      }
    }
    @Override public void reduce(Pass1A t) {
      if( _grps!= t._grps ) {
        ASTGroupBy.IcedNBHS<Group> l = _grps;
        ASTGroupBy.IcedNBHS<Group> r = t._grps;
        if( l.size() < r.size() ) { l=r; r=_grps; }
        for( Group rg: r ) {
          if( !l.add(rg) ) {  // try to add it to the set on the left.. if left already has it, then combine
            Group lg = l.get(rg);
            long L = lg._N;
            while(!Group.CAS_N(lg,L,L+rg._N))
              L = lg._N;
          }
        }
        _grps=l;
        t._grps=null;
      }
    }
  }

  private static class ParallelGroupTask extends H2O.H2OCountedCompleter<ParallelGroupTask> {
    final Group _g;
    ParallelGroupTask(H2O.H2OCountedCompleter cc, Group g) { super(cc); _g=g; }
    @Override protected void compute2() {
      _g.a = new ConcurrentFixedSizeArrayList((int)_g._N);
      tryComplete();
    }
  }

  private static class ParallelGroupProcess extends H2O.H2OCountedCompleter<ParallelGroupProcess> {
    private final ASTGroupBy.IcedNBHS<Group> _grps;
    ParallelGroupProcess(ASTGroupBy.IcedNBHS<Group> grps) { _grps=grps; }

    @Override protected void compute2() {
      for(Group g: _grps)
        new ParallelGroupTask(this, g).fork();
    }
  }

  private static class Pass1B extends MRTask<Pass1B> {
    final ASTGroupBy.IcedNBHS<Group> _grps;
    final long[] _cols;
    Pass1B(ASTGroupBy.IcedNBHS<Group> grps, long[] cols) { _grps=grps; _cols=cols; }
    @Override public void map(Chunk[] c) {
      Group g = new Group(_cols.length);
      Group gg;
      long start = c[0].start();
      for(int i=0;i<c[0]._len;++i) {
        g.fill(i,c,_cols);
        while( _grps.get(g).a==null ) gg=_grps.get(g);
        _grps.get(g).add(start+i);
      }
    }
  }

  private static class Pass2 extends H2O.H2OCountedCompleter<Pass2> {
    private final Frame _fr;
    private final Group[] _grps;

    Pass2(Frame f, Group[] grps) { _fr=f; _grps=grps; }
    Pass2Task[] _tasks;  // want to get out _key from each Pass2Task
    Key[] _keys;

    @Override protected void compute2() {
      addToPendingCount(_grps.length-1);
      // build subset vecs for each group...
      int numnodes = H2O.CLOUD.size();
      _tasks=new Pass2Task[_grps.length];
      _keys=new Key[_grps.length];
      for( int i=0;i<_grps.length;++i ) {
        (_tasks[i]=new Pass2Task(this,i%numnodes,_grps[i],_fr._key)).fork();
        _keys[i] = _tasks[i]._key;
      }
    }
  }

  private static class Pass2Task extends H2O.H2OCountedCompleter<Pass2Task> {
    // round robin spread these Vecs
    private final int _nodeID;
    private final Group _g;
    private final Key _frameKey;
    // group frame key
    Key _key;
    H2ONode _n;
    Pass2Task(H2O.H2OCountedCompleter cc, int nodeID, Group g, Key frameKey) { super(cc); _nodeID=nodeID; _g=g; _frameKey=frameKey; _n=H2O.CLOUD.members()[_nodeID]; _key=Key.make(_n); }
    @Override protected void compute2() {
      H2ONode n = H2O.CLOUD.members()[_nodeID];
      Futures fs = new Futures();
      fs.add(RPC.call(n, new BuildGroup(_key,_g.a.safePublish(),_frameKey)));
      fs.blockForPending();
      tryComplete();
    }
  }

  private static class BuildGroup extends DTask<BuildGroup> implements Freezable {
    private final Key _frameKey; // the frame key
    private final Key _key;     // this is the Vec key for the rows for the group...
    private final long[] _rows; // these are the rows numbers for the group
    BuildGroup(Key key, long[] rows, Key frameKey) {
      _key=key;
      _rows=rows;
      _frameKey=frameKey;
      // Always 1 higher priority than calling thread... because the caller will
      // block & burn a thread waiting for this MRTask to complete.
      Thread cThr = Thread.currentThread();
      _priority = (byte)((cThr instanceof H2O.FJWThr) ? ((H2O.FJWThr)cThr)._priority+1 : super.priority());
    }

    final private byte _priority;
    @Override public byte priority() { return _priority; }

    @Override protected void compute2() {
      assert _key.home() : "Key was not homed to this node!";
      Futures fs = new Futures();

      // get a layout Vec just for the vector group
      Vec layout = Vec.makeZero(_rows.length);
      Key key = layout.group().addVec(); // get a new key
      layout.remove();

      // create the vec of rows numbers
      AppendableVec v = new AppendableVec(key);
      NewChunk n = new NewChunk(v, 0);
      for(long l: _rows) n.addNum(l);
      n.close(0, fs);
      Vec rows = v.close(fs);  // this puts into the DKV!
      fs.blockForPending();

      Frame f = DKV.getGet(_frameKey);                // fetch the Frame we're subsetting
      Vec[] data = f.vecs();                          // Full data columns
      Vec[] gvecs = new Vec[data.length];             // the group vecs, all aligned with the rows Vec
      Key[] keys = rows.group().addVecs(data.length); // generate keys from the vector group...

      // loop over and subset each column, ...one at a time...
      for (int c = 0; c < data.length; c++) {
        gvecs[c] = new SubsetVec(keys[c], rows.get_espc(), data[c]._key, rows._key);
        gvecs[c].setDomain(data[c].domain());
        DKV.put(gvecs[c]._key, gvecs[c]);
      }
      // finally put the constructed group into the DKV
      Frame aa = new Frame(_key, f._names, gvecs);
      DKV.put(_key,aa); // _key is homed to this node!
      assert DKV.getGet(_key) !=null;
      tryComplete();
    }
  }

  private static class Pass3 extends H2O.H2OCountedCompleter<Pass3> {
    private final int numNodes=H2O.CLOUD.size();
    private final int _maxP=1;//1000*numNodes; // run 1000 per node
    private final AtomicInteger _ctr;

    private final Key[] _frameKeys;
    private final ASTOp _FUN;
    private final Group[] _grps;
    private final AST[] _funArgs;

    RemoteRapids[] _remoteTasks;

    Pass3(Key[] frameKeys, ASTOp FUN, Group[] grps, AST[] args) {
      _frameKeys=frameKeys; _FUN=FUN; _grps=grps; _funArgs=args; _ctr=new AtomicInteger(_maxP-1);
      _remoteTasks=new RemoteRapids[_frameKeys.length]; // gather up the remote tasks...
    }

    @Override protected void compute2(){
      addToPendingCount(_frameKeys.length-1);
      for( int i=0;i<Math.min(_frameKeys.length,_maxP);++i) frkTsk(i);
    }

    private void frkTsk(final int i) {
      Futures fs = new Futures();
      H2ONode n = H2O.CLOUD._memary[i%numNodes];
      assert DKV.getGet(_frameKeys[i]) !=null : "Frame was NULL: " + _frameKeys[i];
      RPC rpc = new RPC(n,_remoteTasks[i]=new RemoteRapids(_frameKeys[i], _FUN, _funArgs, _grps[i]._ds));
      rpc.addCompleter(new Callback());
      fs.add(rpc.call());
      fs.blockForPending();
    }

    private class Callback extends H2O.H2OCallback {
      public Callback(){super(Pass3.this);}
      @Override public void callback(H2O.H2OCountedCompleter cc) {
        int i = _ctr.incrementAndGet();
        if( i < _frameKeys.length )
          frkTsk(i);
      }
    }
  }

  private static class RemoteRapids extends DTask<RemoteRapids> implements Freezable {
    private final Key _frameKey;   // the group to process...
    private final ASTOp _FUN;      // the ast to execute on the group
    private final AST[] _funArgs;  // any additional arguments to the _FUN
    private final double[] _ds;     // the "group" itself
    private double[] _result; // result is 1 row per group!

    RemoteRapids(Key frameKey, ASTOp FUN, AST[] args, double[] ds) {
      _frameKey=frameKey; _FUN=FUN; _funArgs=args; _ds=ds;
      // Always 1 higher priority than calling thread... because the caller will
      // block & burn a thread waiting for this MRTask to complete.
      Thread cThr = Thread.currentThread();
      _priority = (byte)((cThr instanceof H2O.FJWThr) ? ((H2O.FJWThr)cThr)._priority+1 : super.priority());
    }

    final private byte _priority;
    @Override public byte priority() { return _priority; }

    @Override public void compute2() {
      assert _frameKey.home();
      Env e = Env.make(new HashSet<Key>());
      Frame groupFrame = DKV.getGet(_frameKey);
      assert groupFrame!=null : "Frame ID: " + _frameKey;
      AST[] args = new AST[_funArgs==null?1:_funArgs.length+1];
      args[0] = new ASTFrame(groupFrame);
      if( _funArgs!=null ) System.arraycopy(_funArgs,0,args,1,_funArgs.length);

      _FUN.make().exec(e,args);

      // grab up the results
      Frame fr = null;
      if( e.isAry() && (fr = e.popAry()).numRows() != 1 )
        throw new IllegalArgumentException("Result of ddply can only return 1 row but instead returned " + fr.numRows());
      int ncols = fr == null ? 1 : fr.numCols();
      _result = new double[_ds.length+ncols]; // fill in the results
      System.arraycopy(_ds,0,_result,0,_ds.length);
      int j=_ds.length;
      for (int i = 0; i < ncols; ++i) {
        if( e.isStr() )      _result[j++] = e.popStr().equals("TRUE")?1:0;
        else if( e.isNum() ) _result[j++] = e.popDbl();
        else if( fr!=null )  _result[j++] = fr.vecs()[i].at(0);
      }
      tryComplete();
    }
  }

  private static class ConcurrentFixedSizeArrayList extends Iced {
    public void add( long l ) { _cal.add(l); }
    public long[] safePublish() { return _cal._l; }
    public long internal_size() { return _cal._cur; }
    private volatile CAL _cal; // the underlying concurrent array list
    ConcurrentFixedSizeArrayList(int sz) { _cal=new CAL(sz); }

    private static class CAL implements Serializable {
      private static final Unsafe U =  UtilUnsafe.getUnsafe();
      protected int _cur;  // the current index into the array, updated atomically
      private long[] _l; // the backing array
      static private final long _curOffset;
      static {
        try {
          _curOffset = U.objectFieldOffset(CAL.class.getDeclaredField("_cur"));
        } catch(Exception e) {
          throw new RuntimeException("Could not set offset with theUnsafe");
        }
      }
      CAL(int s) { _cur=0; _l=new long[s]; }
      private void add( long l ) {
        int c = _cur;
        while(!U.compareAndSwapInt(this,_curOffset,c,c+1))
          c=_cur;
        _l[c]=l;
      }
    }
  }
}
