package water.rapids;

import water.*;
import water.fvec.*;
import water.util.IcedHashMap;

import java.util.ArrayList;
import java.util.HashSet;


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
    // pass1A, finds the number of groups and the size of each group, as well as the row numbers for each group (stashed inside of a nbhm instead of newchunks...)
    Pass1A p1a = new Pass1A(_cols).doAll(fr);                    // pass 1 over all data
    Group[] grps = p1a._grps.keySet().toArray(new Group[p1a._grps.size()]);
    int ngrps = grps.length;
    while( grps[ngrps-1] == null ) ngrps--;   // chop out any null groups hanging at the end.
    Group[] groups = new Group[ngrps];
    System.arraycopy(grps,0,groups,0,ngrps);
    grps = groups;

    // pass2 here does the nominal work of building all of the groups.
    // for lots of tiny groups, this is probably lots of data transfer
    // this chokes the H2O cloud and can even cause it to OOM!
    // this issue is addressed by ASTGroupBy
    Pass2 p2;
    H2O.submitTask(p2=new Pass2(fr,grps)).join();

    // Pass 3: Send Groups 'round the cluster
    Key[] groupFrames = p2._keys;

    Pass3 p3;
    (p3 = new Pass3(groupFrames,ASTOp.get(_fun).make(), grps,_fun_args)).go();
    Vec layoutVec = Vec.makeZero(p3._remoteTasks.length);
    final RemoteRapids[] results = p3._remoteTasks;

    for( int k=0;k<p2._tasks.length;++k ) {
      for(Key key: p2._tasks[k]._subsetVecKeys) Keyed.remove(key);  // remove all of the subset vecs
    }

    int nonnull=-1;
    for(int i=0; i<results.length; ++i) {
      results[i] = results[i]._result==null?null:results[i];
      if(results[i]!=null)nonnull=i;
    }
    if( nonnull==-1 ) { env.pushAry(new Frame(Vec.makeCon(0, 0))); return; }
    final int ncols = results[nonnull]._result.length;
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
          if( results[i+start]==null ) continue;
          d = results[i+start]._result[nc.length-1];
          if( Double.isNaN(d) ) continue; // skip NA group results
          for(int j=0;j<nc.length;++j)
            nc[j].addNum(results[i+start]._result[j]);
        }
      }
    }.doAll(ncols, layoutVec).outputFrame(names,domains);
    layoutVec.remove();
    env.pushAry(fr2);
  }

  // ---
  // Group description: unpacked selected double columns
  public static class Group extends ASTGroupBy.G {
    public Group() { super(); }
    public Group(int len) { super(len); a=new IcedHashMap<>(); }
    public Group( double ds[] ) { super(ds); }

    IcedHashMap<Integer,String> a;
  }


  private static class Pass1A extends MRTask<Pass1A> {
    private final long _gbCols[];
    IcedHashMap<Group,String> _grps;
    Pass1A(long[] cols) { _gbCols=cols; }
    @Override public void setupLocal() { _grps = new IcedHashMap<>(); }
    @Override public void map(Chunk[] c) {
      Group g = new Group(_gbCols.length);
      Group gOld;
      int start = (int)c[0].start();
      for(int i=0;i<c[0]._len;++i) {
        g.fill(i,c,_gbCols);
        String old_g = _grps.putIfAbsent(g, "");
        if( old_g==null ) {
          gOld=g;
          g= new Group(_gbCols.length);
        } else {
          gOld=_grps.getk(g);
          if( gOld==null )
            while( gOld==null ) gOld=_grps.getk(g);
        }
        long cnt=gOld._N;
        while( !Group.CAS_N(gOld,cnt,cnt+1))
          cnt=gOld._N;
        gOld.a.put(start+i,"");
      }
    }
    @Override public void reduce(Pass1A t) {
      if( _grps!= t._grps ) {
        IcedHashMap<Group,String> l = _grps;
        IcedHashMap<Group,String> r = t._grps;
        if( l.size() < r.size() ) { l=r; r=_grps; }
        for( Group rg: r.keySet() ) {
          if( l.containsKey(rg) ) {  // try to add it to the set on the left.. if left already has it, then combine
            Group lg = l.getk(rg);
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
    Key[] _subsetVecKeys;
    Pass2Task(H2O.H2OCountedCompleter cc, int nodeID, Group g, Key frameKey) { super(cc); _nodeID=nodeID; _g=g; _frameKey=frameKey; _n=H2O.CLOUD.members()[_nodeID]; _key=Key.make(_n); }
    @Override protected void compute2() {
      H2ONode n = H2O.CLOUD.members()[_nodeID];
      Futures fs = new Futures();
      long[] rows = new long[_g.a.size()];
      int i=0;
      for(long l: _g.a.keySet() ) rows[i++]=l;
      BuildGroup b;
      fs.add(RPC.call(n, b=new BuildGroup(_key,rows,_frameKey)));
      fs.blockForPending();
      _subsetVecKeys = b._subsetVecKeys;
      tryComplete();
    }
  }

  private static class BuildGroup extends DTask<BuildGroup> implements Freezable {
    private final Key _frameKey; // the frame key
    private final Key _key;     // this is the Vec key for the rows for the group...
    private final long[] _rows; // these are the rows numbers for the group
    private Key[] _subsetVecKeys;
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
      _subsetVecKeys = keys;                          // store these for later removal...

      // loop over and subset each column, ...one at a time...
      for (int c = 0; c < data.length; c++) {
        gvecs[c] = new SubsetVec(keys[c], rows.get_espc(), data[c]._key, rows._key);
        gvecs[c].setDomain(data[c].domain());
        DKV.put(gvecs[c]._key, gvecs[c]);
      }
      // finally put the constructed group into the DKV
      Frame aa = new Frame(_key, f._names, gvecs);
      DKV.put(_key,aa); // _key is homed to this node!
      assert _key.home(): "Key should be homed to the node! Somehow remapped during this compute2.";
      assert DKV.getGet(_key) !=null;
      tryComplete();
    }
  }

  private static class Pass3 {
    private final Key[] _frameKeys;
    private final ASTOp _FUN;
    private final Group[] _grps;
    private final AST[] _funArgs;

    RemoteRapids[] _remoteTasks;

    Pass3(Key[] frameKeys, ASTOp FUN, Group[] grps, AST[] args) {
      _frameKeys=frameKeys; _FUN=FUN; _grps=grps; _funArgs=args;
      _remoteTasks=new RemoteRapids[_frameKeys.length]; // gather up the remote tasks...
    }

    // stupid single threaded pass over all groups...
    private void go() {
      Futures fs = new Futures();
      for( int i=0;i<_frameKeys.length;++i) {
        assert DKV.getGet(_frameKeys[i]) !=null : "Frame #" + i + " was NULL: " + _frameKeys[i];
        fs.add(RPC.call(_frameKeys[i].home_node(), _remoteTasks[i] = new RemoteRapids(_frameKeys[i], _FUN, _funArgs, _grps[i]._ds)));
      }
      fs.blockForPending();
    }
  }

  private static class RemoteRapids extends DTask<RemoteRapids> implements Freezable {
    private final Key _frameKey;   // the group to process...
    private final ASTOp _FUN;      // the ast to execute on the group
    private final AST[] _funArgs;  // any additional arguments to the _FUN
    private final double[] _ds;    // the "group" itself
    private double[] _result;      // result is 1 row per group!

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
      if( !e.isNul() ) {
        // grab up the results
        Frame fr = null;
        if (e.isAry() && (fr = e.popAry()).numRows() != 1)
          throw new IllegalArgumentException("Result of ddply can only return 1 row but instead returned " + fr.numRows());
        int ncols = fr == null ? 1 : fr.numCols();
        _result = new double[_ds.length + ncols]; // fill in the results
        System.arraycopy(_ds, 0, _result, 0, _ds.length);
        int j = _ds.length;
        for (int i = 0; i < ncols; ++i) {
          if (e.isStr()) _result[j++] = e.popStr().equals("TRUE") ? 1 : 0;
          else if (e.isNum()) _result[j++] = e.popDbl();
          else if (fr != null) _result[j++] = fr.vecs()[i].at(0);
        }
      }
      groupFrame.delete();
      tryComplete();
    }
  }
}
