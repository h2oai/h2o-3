package water.rapids;

import water.*;
import water.fvec.*;
import water.nbhm.NonBlockingHashMap;
import water.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;


/** plyr's ddply: GroupBy by any other name.
 *  Sample AST: (h2o.ddply $frame {1;5;10} $fun)
 *
 *  First arg is the frame we'll be working over.
 *  Second arg is column selection to group by.
 *  Third arg is the function to apply to each group.
 */
public class ASTddply extends ASTOp {
  protected static long[] _cols;
  protected static String _fun;
  protected static AST[] _fun_args;
  static final String VARS[] = new String[]{ "ary", "{cols}", "FUN"};
  public ASTddply( ) { super(VARS); }

  @Override String opStr(){ return "h2o.ddply";}
  @Override ASTOp make() {return new ASTddply();}

  @Override ASTddply parse_impl(Exec E) {
    // get the frame to work
    AST ary = E.parse();

    // Get the col ids
    AST s=null;
    try {
      s = E.skipWS().parse(); // this jumps out to le squiggly du parse
      _cols = ((ASTSeries)s).toArray(); // do the dump to array here -- no worry about efficiency or speed

      // SANITY CHECK COLS:
      if (_cols.length > 1000) throw new IllegalArgumentException("Too many columns selected. Please select < 1000 columns.");

    } catch (ClassCastException e) {

      try {
        _cols = new long[]{(long)((ASTNum)s).dbl()};
      } catch (ClassCastException e2) {
        throw new IllegalArgumentException("Badly formed AST. Columns argument must be a ASTSeries or ASTNum");
      }
    }

    // get the fun
    _fun = ((ASTId)E.skipWS().parse())._id;

    // get any fun args
    ArrayList<AST> fun_args = new ArrayList<>();
    while(E.skipWS().hasNext()) {
      fun_args.add(E.parse());
    }
    ASTddply res = (ASTddply)clone();
    res._asts = new AST[]{ary};
    if (fun_args.size() > 0) {
      _fun_args = fun_args.toArray(new AST[fun_args.size()]);
    } else {
      _fun_args = null;
    }
    return res;
  }

  @Override void apply(Env env) {
    Frame fr = env.pop0Ary();    // The Frame to work on

    // sanity check cols some moar
    for (long l : _cols) {
      if (l > fr.numCols() || l < 0) throw new IllegalArgumentException("Column "+(l+1)+" out of range for frame columns "+fr.numCols());
    }

    // Was pondering a SIMD-like execution model, running the fcn "once" - but
    // in parallel for all groups.  But this isn't going to work: each fcn
    // execution will take different control paths.  Also the functions side-
    // effects' must only happen once, and they will make multiple passes over
    // the Frame passed in.
    //
    // GroupIDs' can vary from 1 group to 1-per-row.  Are formed by the cross-
    // product of the selection cols.  Will be hashed to find Group - NBHML
    // mapping row-contents to group.  Index is a sample row.  NBHML per-node,
    // plus roll-ups.  Result/Value is Group structure pointing to NewChunks
    // holding row indices.

    // Pass 1: Find Groups.
    // Build a NBHSet of unique double[]'s holding selection cols.
    // These are the unique groups, found per-node, rolled-up globally
    // Record the rows belonging to each group, locally.
    ddplyPass1 p1 = new ddplyPass1(true,_cols).doAll(fr);

    // Pass 2: Build Groups.
    // Wrap Vec headers around all the local row-counts.
    int numgrps = p1._groups.size();
    int csz = H2O.CLOUD.size();
    ddplyPass2 p2 = new ddplyPass2(p1,numgrps,csz).doAllNodes();
    // vecs[] iteration order exactly matches p1._groups.keySet()
    Vec vecs[] = p2.close();
    // Push the execution env around the cluster
    Key envkey = Key.make();
//    DKV.put(envkey, env);

    // Pass 3: Send Groups 'round the cluster
    // Single-threaded per-group work.
    // Send each group to some remote node for execution
    int grpnum=0; // vecs[] iteration order exactly matches p1._groups.keySet()
    int nlocals[] = new int[csz]; // Count of local group#
    ArrayList<AppendableVec> grpCols = new ArrayList<AppendableVec>();
    ArrayList<NewChunk> nchks = new ArrayList<NewChunk>();
    for (long col : _cols) {
      AppendableVec av = new AppendableVec(Vec.VectorGroup.VG_LEN1.addVec());
      grpCols.add(av);
      nchks.add(new NewChunk(av, 0));
    }
    Futures fs = new Futures();
    int ncols;
    for( Group g : p1._groups.keySet() ) {
      // vecs[] iteration order exactly matches p1._groups.keySet()
      Vec rows = vecs[grpnum++]; // Rows for this Vec
      Vec[] data = fr.vecs();    // Full data columns
      Vec[] gvecs = new Vec[data.length];
      Key[] keys = rows.group().addVecs(data.length);
      for( int c=0; c<data.length; c++ ) {
        gvecs[c] = new SubsetVec(rows._key, data[c]._key, keys[c], rows.get_espc());
        gvecs[c].setDomain(data[c].domain());
        DKV.put(gvecs[c]._key, gvecs[c]);
      }
      Key grpkey = Key.make("ddply_grpkey_"+(grpnum-1));
      Frame fg = new Frame(grpkey, fr._names,gvecs);
      DKV.put(grpkey, fg);
      // Non-blocking, send a group to a remote node for execution
      final int nidx = g.hashCode()%csz;
      fs.add(RPC.call(H2O.CLOUD._memary[nidx],(new RemoteExec((grpnum-1),p2._nlocals[nidx],g._ds,fg,envkey, _fun, _fun_args))));
    }
    fs.blockForPending();       // Wait for all functions to finish

    //Fold results together; currently stored in Iced Result objects
    grpnum = 0;
    for (Group g: p1._groups.keySet()) {
      int c = 0;
      for (double d : g._ds) nchks.get(c++).addNum(d);
      Key rez_key = Key.make("ddply_RemoteRez_"+grpnum++);
      Result rg = DKV.get(rez_key).get();
      if (rg == null) Log.info("Result was null: grp_id = " + (grpnum - 1) + " rez_key = " + rez_key);
      assert rg != null;
      ncols = rg.isRow() ? rg.resultR().length : 1;
      if (nchks.size() < ncols + _cols.length) {
        for(int i = 0; i < ncols;++i) {
          AppendableVec av = new AppendableVec(Vec.VectorGroup.VG_LEN1.addVec());
          grpCols.add(av);
          nchks.add(new NewChunk(av, 0));
        }
      }
      for (int i = 0; i < ncols; ++i) nchks.get(c++).addNum(rg.isRow() ? rg.resultR()[i] : rg.resultD());
      DKV.remove(rez_key);
    }

    Vec vres[] = new Vec[grpCols.size()];
    for (int i = 0; i < vres.length; ++i) {
      nchks.get(i).close(0, fs);
      vres[i] = grpCols.get(i).close(fs);
    }
    fs.blockForPending();
    // Result Frame
    String[] names = new String[grpCols.size()];
    for( int i = 0; i < _cols.length; i++) {
      names[i] = fr._names[(int)_cols[i]];
      vres[i].setDomain(fr.vecs()[(int)_cols[i]].domain());
    }
    for( int i = _cols.length; i < names.length; i++) names[i] = "C"+(i-_cols.length+1);
    Frame ff = new Frame(names,vres);

    // Cleanup pass: Drop NAs (groups with no data and NA groups, basically does na.omit: drop rows with NA)
    boolean anyNA = false;
    Frame res = ff;
    for (Vec v : ff.vecs()) if (v.naCnt() != 0) { anyNA = true; break; } // stop on first vec with naCnt != 0
    if (anyNA) {
      res = new MRTask() {
        @Override
        public void map(Chunk[] cs, NewChunk[] nc) {
          int rows = cs[0]._len;
          int cols = cs.length;
          boolean[] NACols = new boolean[cols];
          ArrayList<Integer> xrows = new ArrayList<Integer>();
          for (int i = 0; i < cols; ++i) NACols[i] = (cs[i].vec().naCnt() != 0);
          for (int r = 0; r < rows; ++r)
            for (int c = 0; c < cols; ++c)
              if (NACols[c])
                if (cs[c].isNA0(r)) {
                  xrows.add(r);
                  break;
                }
          for (int r = 0; r < rows; ++r) {
            if (xrows.contains(r)) continue;
            for (int c = 0; c < cols; ++c) {
              if (cs[c].vec().isEnum()) nc[c].addEnum((int) cs[c].at80(r));
              else nc[c].addNum(cs[c].at0(r));
            }
          }
        }
      }.doAll(ff.numCols(), ff).outputFrame(null, ff.names(), ff.domains());
      ff.delete();
    }
    // Delete the group row vecs
    Keyed.remove(envkey);
    env.push(new ValFrame(res));
  }

  // ---
  // Group descrption: unpacked selected double columns
  public static class Group extends Iced {
    public double _ds[];
    public int _hash;
    public Group(int len) { _ds = new double[len]; }
    Group( double ds[] ) { _ds = ds; _hash=hash(); }
    // Efficiently allow groups to be hashed & hash-probed
    public void fill(int row, Chunk chks[], long cols[]) {
      for( int c=0; c<cols.length; c++ ) // For all selection cols
        _ds[c] = chks[(int)cols[c]].at0(row); // Load into working array
      _hash = hash();
    }
    private int hash() {
      long h=0;                 // hash is sum of field bits
      for( double d : _ds ) h += Double.doubleToRawLongBits(d);
      // Doubles are lousy hashes; mix up the bits some
      h ^= (h>>>20) ^ (h>>>12);
      h ^= (h>>> 7) ^ (h>>> 4);
      return (int)((h^(h>>32))&0x7FFFFFFF);
    }
    public boolean has(double ds[]) { return Arrays.equals(_ds, ds); }
    @Override public boolean equals( Object o ) {
      return o instanceof Group && Arrays.equals(_ds,((Group)o)._ds); }
    @Override public int hashCode() { return _hash; }
    @Override public String toString() { return Arrays.toString(_ds); }
  }

  private static class Result extends Iced {
    double   _d;  // Result was a single double
    double[] _r;  // Result was a row
    Result(double d, double[] r) {_d = d; _r = r; }
    boolean isRow() { return _r != null; }
    double[] resultR () { return _r; }
    double resultD () { return _d; }
  }


  // ---
  // Pass1: Find unique groups, based on a subset of columns.
  // Collect rows-per-group, locally.
  protected static class ddplyPass1 extends MRTask<ddplyPass1> {
    // INS:
    private boolean _gatherRows; // TRUE if gathering rows-per-group, FALSE if just getting the groups
    private long _cols[];   // Selection columns
    private Key _uniq;     // Unique Key for this entire ddply pass
    ddplyPass1( boolean rows, long cols[] ) { _gatherRows=rows; _cols = cols; _uniq=Key.make(); }
    // OUTS: mapping from groups to row#s that are in that group
    protected NonBlockingHashMap<Group,NewChunk> _groups;

    // *Local* results from ddplyPass1 are kept locally in this tmp structure.
    // Pass2 reads them out & reclaims the space.
    private static NonBlockingHashMap<Key,ddplyPass1> PASS1TMP = new NonBlockingHashMap<>();

    // Make a NewChunk to hold rows, that has a random Key and is not
    // associated with any Vec.  We'll fold these into a Vec later when we know
    // cluster-wide what the Groups (and hence Vecs) are.
    private static final NewChunk XNC = new NewChunk(null,H2O.SELF.index());
    private NewChunk makeNC( ) { return !_gatherRows ? XNC : new NewChunk(null,H2O.SELF.index()); }

    // Build a Map mapping Groups to a NewChunk of row #'s
    @Override public void map( Chunk chks[] ) {
      _groups = new NonBlockingHashMap<>();
      Group g = new Group(_cols.length);
      NewChunk nc = makeNC();
      Chunk C = chks[(int)_cols[0]];
      int len = C._len;
      long start = C.start();
      for( int row=0; row<len; row++ ) {
        // Temp array holding the column-selection data
        g.fill(row,chks,_cols);
        NewChunk nc_old = _groups.putIfAbsent(g,nc);
        if( nc_old==null ) {    // Add group signature if not already present
          nc_old = nc;          // Jammed 'nc' into the table to hold rows
          g = new Group(_cols.length); // Need a new <Group,NewChunk> pair
          nc = makeNC();
        }
        if( _gatherRows )             // Gathering rows?
          nc_old.addNum(start+row,0); // Append rows into the existing group
      }
    }
    // Fold together two Group/NewChunk Maps.  For the same Group, append
    // NewChunks (hence gathering rows together).  Since the custom serializers
    // do not send the rows over the wire, we have only *local* row-counts.
    @Override public void reduce( ddplyPass1 p1 ) {
      assert _groups != p1._groups;
      // Fold 2 hash tables together.
      // Get the larger hash table in m0, smaller in m1
      NonBlockingHashMap<Group,NewChunk> m0 =    _groups;
      NonBlockingHashMap<Group,NewChunk> m1 = p1._groups;
      if( m0.size() < m1.size() ) { m0=m1; m1=this._groups; }
      // Iterate over smaller table, folding into larger table.
      for( Group g : m1.keySet() ) {
        NewChunk nc0 = m0.get(g);
        NewChunk nc1 = m1.get(g);
        if( nc0 == null || nc0._len == 0) m0.put(g,nc1);
          // unimplemented: expected to blow out on large row counts, where we
          // actually need a collection of chunks, not 1 uber-chunk
        else if( _gatherRows ) {
          // All longs are monotonically in-order.  Not sure if this is needed
          // but it's an easy invariant to keep and it makes reading row#s easier.
          if( nc0._len > 0 && nc1._len > 0 && // len==0 for reduces from remotes (since no rows sent)
                  nc0.at8_impl(nc0._len-1) >= nc1.at8_impl(0) )   nc0.addr(nc1);
          else if (nc1._len != 0)                             nc0.add (nc1);
        }
      }
      _groups = m0;
      p1._groups = null;
    }
    @Override public String toString() { return _groups==null ? null : _groups.toString(); }
    // Save local results for pass2
    @Override public void closeLocal() { if( _gatherRows ) PASS1TMP.put(_uniq,this); }

    // Custom serialization for NBHM.  Much nicer when these are auto-gen'd.
    // Only sends Groups over the wire, NOT NewChunks with rows.
    @Override public AutoBuffer write_impl( AutoBuffer ab ) {
//      super.write(ab);
      ab.putZ(_gatherRows);
      ab.putA8(_cols);
      ab.put(_uniq);
      if( _groups == null ) return ab.put4(0);
      ab.put4(_groups.size());
      for( Group g : _groups.keySet() ) ab.put(g);
      return ab;
    }

    @Override public ddplyPass1 read_impl( AutoBuffer ab ) {
//      super.read(ab);
      assert _groups == null;
      _gatherRows = ab.getZ();
      _cols = ab.getA8();
      _uniq = ab.get();
      int len = ab.get4();
      if( len == 0 ) return this;
      _groups = new NonBlockingHashMap<Group,NewChunk>();
      for( int i=0; i<len; i++ )
        _groups.put(ab.get(Group.class),new NewChunk(null,-99));
      return this;
    }

    // no copyOver ?
//    @Override public void copyOver( Freezable dt ) {
//      ddplyPass1 that = (ddplyPass1)dt;
//      super.copyOver(that);
//      this._gatherRows = that._gatherRows;
//      this._cols   = that._cols;
//      this._uniq   = that._uniq;
//      this._groups = that._groups;
//    }
  }

  // ---
  // Pass 2: Build Groups.
  // Wrap Frame/Vec headers around all the local row-counts.
  private static class ddplyPass2 extends MRTask<ddplyPass2> {
    // Key uniquely identifying a pass1 collection of NewChunks
    Key _p1key;
    // One new Vec per Group, holding just rows
    AppendableVec _avs[];
    // The Group descripters
    double _dss[][];
    // Count of groups-per-node (computed once on home node)
    transient int _nlocals[];

    ddplyPass2( ddplyPass1 p1, int numgrps, int csz ) {
      _p1key = p1._uniq;        // Key to finding the pass1 data
      // One new Vec per Group, holding just rows
      _avs = new AppendableVec[numgrps];
      _dss = new double       [numgrps][];
      _nlocals = new int      [csz];
      int i=0;
      for( Group g : p1._groups.keySet() ) {
        _dss[i] = g._ds;
        _avs[i++] = new AppendableVec(Vec.VectorGroup.VG_LEN1.addVec());
        _nlocals[g.hashCode()%csz]++;
      }
    }

    // Local (per-Node) work.  Gather the chunks together into the Vecs
    @Override public void setupLocal() {
      ddplyPass1 p1 = ddplyPass1.PASS1TMP.remove(_p1key);
      Futures fs = new Futures();
      int cidx = H2O.SELF.index();
      for( int i=0; i<_dss.length; i++ ) { // For all possible groups
        // Get the newchunk of local rows for a group
        Group g = new Group(_dss[i]);
        NewChunk nc = p1._groups == null ? null : p1._groups.get(g);
        if( nc != null && nc._len > 0 ) { // Fill in fields we punted on during construction
          nc.set_vec(_avs[i]);
//          nc.set_ = _avs[i];  // Assign a proper vector
          nc.close(cidx,fs);  // Close & compress chunk
        } else {              // All nodes have a chunk, even if its empty
          DKV.put(_avs[i].chunkKey(cidx), new C0LChunk(0,0),fs);
        }
      }
      fs.blockForPending();
      _p1key = null;            // No need to return these
      _dss = null;
      tryComplete();
    }
    @Override public void reduce( ddplyPass2 p2 ) {
      for( int i=0; i<_avs.length; i++ )
        _avs[i].reduce(p2._avs[i]);
    }
    // Close all the AppendableVecs & return normal Vecs.
    Vec[] close() {
      Futures fs = new Futures();
      Vec vs[] = new Vec[_avs.length];
      for( int i=0; i<_avs.length; i++ ) vs[i] = _avs[i].close(fs);
      fs.blockForPending();
      return vs;
    }
  }

  // ---
  // Called once-per-group, it executes the given function on the group.
  private static class RemoteExec extends DTask<RemoteExec> implements Freezable {
    // INS
    final int _grpnum, _numgrps; // This group # out of total groups
    double _ds[];                // Displayable name for this group
    Frame _fr;                   // Frame for this group
    Key _envkey;                 // Key for the execution environment
    String _fun;
    AST[] _fun_args;
    // OUTS
    int _ncols;                  // Number of result columns

    RemoteExec( int grpnum, int numgrps, double ds[], Frame fr, Key envkey, String fun, AST[] fun_args ) {
      _grpnum = grpnum; _numgrps = numgrps; _ds=ds; _fr=fr; _envkey=envkey; _fun = fun; _fun_args = fun_args;
      // Always 1 higher priority than calling thread... because the caller will
      // block & burn a thread waiting for this MRTask2 to complete.
      Thread cThr = Thread.currentThread();
      _priority = (byte)((cThr instanceof H2O.FJWThr) ? ((H2O.FJWThr)cThr)._priority+1 : super.priority());
    }

    final private byte _priority;
    @Override public byte priority() { return _priority; }

    // Execute the function on the group
    @Override public void compute2() {
//      Env shared_env = DKV.get(_envkey).get();
      // Clone a private copy of the environment for local execution
      Env env = new Env(new HashSet<Key>());
      final ASTOp op = (ASTOp)ASTOp.get(_fun).clone();
      Key fr_key = Key.make("ddply_grpkey_" + _grpnum);
      Frame aa = DKV.get(fr_key).get();
//      Frame tmp = new Frame(new String[]{aa.names()[0]}, new Vec[]{aa.vecs()[0].makeCopy()});
      op.exec(env, new ASTFrame(aa), _fun_args);

      // Inspect the results; figure the result column count
      Frame fr = null;
      if( env.isAry() && (fr=env.pop0Ary()).numRows() != 1 )
        throw new IllegalArgumentException("Result of ddply can only return 1 row but instead returned "+fr.numRows());
      _ncols = fr == null ? 1 : fr.numCols();

      double[] r = null;
      double d = Double.NaN;
      if (fr == null) d = env.popDbl();
      else {
        r = new double[_ncols];
        for (int i = 0; i < _ncols; ++i) r[i] = fr.vecs()[i].at(0);
      }
      Key resultKey = Key.make("ddply_RemoteRez_"+_grpnum);
      Result rez = new Result(d, r);
      Futures fs = new Futures();
      DKV.put(resultKey, rez, fs);
      fs.blockForPending();

      // No need to return any results here.
      _fr.delete();
      aa.delete();
      _fr = null;
      _ds = null;
      _envkey= null;
      tryComplete();
    }
  }
}
