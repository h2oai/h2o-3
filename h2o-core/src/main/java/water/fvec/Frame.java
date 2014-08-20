package water.fvec;

import java.util.Arrays;
import java.util.HashMap;

import water.*;
import water.util.ArrayUtils;
import water.util.Log;

/**
 * A collection of named Vecs.  Essentially an R-like data-frame.  Multiple
 * Frames can reference the same Vecs.  A Frame is a lightweight object, it is
 * meant to be cheaply created and discarded for data munging purposes.
 * E.g. to exclude a Vec from a computation on a Frame, create a new Frame that
 * references all the Vecs but this one.
 */
public class Frame extends Lockable {
  public String[] _names;
  private Key[] _keys;           // Keys for the vectors
  private transient Vec[] _vecs; // The Vectors (transient to avoid network traffic)
  private transient Vec _col0; // First readable vec; fast access to the VectorGroup's Chunk layout

  public Frame( String name ) { this(Key.make(name),null,new Vec[0]); } // Empty frame, lazily filled
  public Frame( Vec... vecs ){ this(null,vecs);}
  public Frame( String names[], Vec vecs[] ) { this(null,names,vecs); }
  public Frame( Key key ) { this(key,null,new Vec[0]); }
  public Frame( Key key, String names[], Vec vecs[] ) { 
    super(key);

    // Require all Vecs already be installed in the K/V store
    for( Vec vec : vecs ) DKV.prefetch(vec._key);
    for( Vec vec : vecs ) assert DKV.get(vec._key) != null;

    // Always require names
    if( names==null ) {
      names = new String[vecs.length];
      for( int i=0; i<vecs.length; i++ ) names[i] = "C"+(i+1);
    } 
    assert names.length == vecs.length;

    // Make empty to dodge asserts, then "add()" them all which will check for
    // compatible Vecs & names.
    _names = new String[0];
    _keys  = new Key   [0];
    _vecs  = new Vec   [0];
    add(names,vecs);
  }
  // Add a bunch of vecs
  private void add( String[] names, Vec[] vecs ) {
    for( int i=0; i<vecs.length; i++ )
      add(names[i],vecs[i]);
  }
  // Append a default-named Vec
  public Vec add( Vec vec ) { return add("C"+(numCols()+1),vec); }
  // Append a named Vec
  public Vec add( String name, Vec vec ) {
    checkCompatible(name=uniquify(name),vec);  // Throw IAE is mismatch
    int ncols = _keys.length;
    _names = Arrays.copyOf(_names,ncols+1);  _names[ncols] = name;
    _keys  = Arrays.copyOf(_keys ,ncols+1);  _keys [ncols] = vec._key;
    _vecs  = Arrays.copyOf(_vecs ,ncols+1);  _vecs [ncols] = vec;
    return vec;
  }
  // Append a Frame
  public Frame add( Frame fr ) { add(fr._names,fr.vecs()); return this; }

  // Allow sorting of columns based on some function
  public void swap( int lo, int hi ) {
    assert 0 <= lo && lo < _keys.length;
    assert 0 <= hi && hi < _keys.length;
    if( lo==hi ) return;
    Vec vecs[] = vecs();
    Vec v   = vecs [lo]; vecs  [lo] = vecs  [hi]; vecs  [hi] = v;
    Key k   = _keys[lo]; _keys [lo] = _keys [hi]; _keys [hi] = k;
    String n=_names[lo]; _names[lo] = _names[hi]; _names[hi] = n;
  }

  /** Check that the vectors are all compatible.  All Vecs have their content
   *  sharded using same number of rows per chunk, and all names are unique.
   *  Throw an IAE if something does not match.  */
  private void checkCompatible( String name, Vec vec ) {
    if( ArrayUtils.find(_names,name) != -1 ) throw new IllegalArgumentException("Duplicate name '"+name+"' in Frame");
    if( vec instanceof AppendableVec ) return; // New Vectors are endlessly compatible
    Vec v0 = anyVec();
    if( v0 == null ) return; // No fixed-size Vecs in the Frame
    // Vector group has to be the same, or else the layout has to be the same,
    // or else the total length has to be small.
    if( !v0.checkCompatible(vec) )
      throw new IllegalArgumentException("Vector groups differs - adding vec '"+name+"' into the frame " + Arrays.toString(_names));
    if( v0.length() != vec.length() )
      throw new IllegalArgumentException("Vector lengths differ - adding vec '"+name+"' into the frame " + Arrays.toString(_names));
  }

  // Used by tests to "go slow" when comparing mis-aligned Frames
  public boolean checkCompatible( Frame fr ) {
    if( numCols() != fr.numCols() ) return false;
    if( numRows() != fr.numRows() ) return false;
    for( int i=0; i<vecs().length; i++ )
      if( !vecs()[i].checkCompatible(fr.vecs()[i]) )
        return false;
    return true;
  }

  private String uniquify( String name ) {
    String n = name;
    int cnt=0, again;
    do {
      again = cnt;
      for( String s : _names )
        if( n.equals(s) )
          n = name+(cnt++);
    } while( again != cnt );
    return n;
  }

  // Deep copy of Vecs & Keys & Names (but not data!) to a new named Key.  The
  // resulting Frame does not share with the original, so the set of Vecs can
  // be freely hacked without disturbing the original Frame.
  public Frame( Frame fr ) {
    super( Key.make() );
    _names= fr._names.clone();
    _keys = fr._keys .clone();
    _vecs = fr.vecs().clone();
  }

  // Deep Copy of the *data*.  Can get expensive quick.
  public Frame deepcopy() {
    return new MRTask() {
      @Override public void map(Chunk []cs, NewChunk []ncs) {
        for( int col = 0; col < cs.length; col++ ) {
          Chunk c = cs[col];
          NewChunk nc = ncs[col];
          for( int row = 0; row < c.len(); row++ )
            if( c._vec.isUUID() ) nc.addUUID(c,row);
            else nc.addNum(c.at0(row));
        }
      }
    }.doAll(numCols(),this).outputFrame(names(),domains());
  }
  /** Returns a subframe of this frame containing only vectors with desired names.
   *
   * @param names list of vector names
   * @return a new frame which collects vectors from this frame with desired names.
   * @throws IllegalArgumentException if there is no vector with desired name in this frame.
   */
  public Frame subframe(String[] names) { return subframe(names, false, 0)[0]; }
  /** Returns a new frame composed of vectors of this frame selected by given names.
   * The method replaces missing vectors by a constant column filled by given value.
   * @param names names of vector to compose a subframe
   * @param c value to fill missing columns.
   * @return two frames, the first contains subframe, the second contains newly created constant vectors or null
   */
  public Frame[] subframe(String[] names, double c) { return subframe(names, true, c); }

  /** Create a subframe from this frame based on desired names.
   * Throws an exception if desired column is not in this frame and <code>replaceBy</code> is <code>false</code>.
   * Else replace a missing column by a constant column with given value.
   *
   * @param names list of column names to extract
   * @param replaceBy should be missing column replaced by a constant column
   * @param c value for constant column
   * @return array of 2 frames, the first is containing a desired subframe, the second one contains newly created columns or null
   * @throws IllegalArgumentException if <code>replaceBy</code> is false and there is a missing column in this frame
   */
  private Frame[] subframe(String[] names, boolean replaceBy, double c){
    Vec [] vecs     = new Vec[names.length];
    Vec [] cvecs    = replaceBy ? new Vec   [names.length] : null;
    String[] cnames = replaceBy ? new String[names.length] : null;
    int ccv = 0; // counter of constant columns
    vecs();                     // Preload the vecs
    HashMap<String, Integer> map = new HashMap<>((int) ((names.length/0.75f)+1)); // avoid rehashing by set up initial capacity
    for(int i = 0; i < _names.length; ++i) map.put(_names[i], i);
    for(int i = 0; i < names.length; ++i)
      if(map.containsKey(names[i])) vecs[i] = _vecs[map.get(names[i])];
      else if (replaceBy) {
        Log.warn("Column " + names[i] + " is missing, filling it in with " + c);
        assert cnames != null;
        cnames[ccv] = names[i];
        vecs[i] = cvecs[ccv++] = anyVec().makeCon(c);
      }
    return new Frame[] { new Frame(names,vecs), ccv>0 ?  new Frame(Arrays.copyOf(cnames, ccv), Arrays.copyOf(cvecs,ccv)) : null };
  }

  /** Returns the first readable vector. */
  public final Vec anyVec() {
    Vec c0 = _col0; // single read
    if( c0 != null ) return c0;
    for( Vec v : vecs() )
      if( v.readable() )
        return (_col0 = v);
    return null;
  }

  public final Vec[] vecs() { 
    Vec[] tvecs = _vecs; // read the content
    return tvecs == null ? (_vecs=vecs_impl()) : tvecs;
  }
  // Compute vectors for caching
  private Vec[] vecs_impl() {
    // Load all Vec headers; load them all in parallel by starting prefetches
    for( Key key : _keys ) DKV.prefetch(key);
    Vec [] vecs = new Vec[_keys.length];
    for( int i=0; i<_keys.length; i++ ) vecs[i] = DKV.get(_keys[i]).get();
    return vecs;
  }

  /** true/false whether each Vec is a UUID */
  public boolean[] uuids() {
    boolean bs[] = new boolean[vecs().length];
    for( int i=0; i<vecs().length; i++ )
      bs[i] = vecs()[i].isUUID();
    return bs;
  }

  /** true/false whether each Vec is a string */
  public boolean[] strings() {
    boolean bs[] = new boolean[vecs().length];
    for( int i=0; i<vecs().length; i++ )
      bs[i] = vecs()[i].isString();
    return bs;
  }

  /** Time status for every Vec */
  public byte[] times() {
    byte bs[] = new byte[vecs().length];
    for( int i=0; i<vecs().length; i++ )
      bs[i] = vecs()[i]._time;
    return bs;
  }

  /** All the domains for enum columns; null for non-enum columns.  */
  public String[][] domains() {
    String ds[][] = new String[vecs().length][];
    for( int i=0; i<vecs().length; i++ )
      ds[i] = vecs()[i].domain();
    return ds;
  }

  public String[][] domains(int [] cols){
    Vec [] vecs = vecs();
    String [][] res = new String[cols.length][];
    for(int i = 0; i < cols.length; ++i)
      res[i] = vecs[cols[i]]._domain;
    return res;
  }

  public String [] names(int [] cols){
    if(_names == null)return null;
    String [] res = new String[cols.length];
    for(int i = 0; i < cols.length; ++i)
      res[i] = _names[cols[i]];
    return res;
  }

  public String[] names() { return _names; }

  public long byteSize() {
    long sum=0;
    Vec[] vecs = vecs();
    for (Vec vec : vecs) sum += vec.byteSize();
    return sum;
  }

  // For MRTask: allow rollups for all written-into vecs
  public Futures postWrite(Futures fs) {
    for( Vec v : vecs() ) v.postWrite(fs);
    return fs;
  }

  /** Actually remove/delete all Vecs from memory, not just from the Frame. */
  @Override public Futures remove_impl(Futures fs) {
    for( Vec v : vecs() ) v.remove(fs);
    _names = new String[0];
    _vecs = new Vec[0];
    _keys = new Key[0];
    return fs;
  }
  public Vec replace(int col, Vec nv) {
    Vec rv = vecs()[col];
    assert rv.group().equals(nv.group());
    _vecs[col] = nv;
    _keys[col] = nv._key;
    if( DKV.get(nv._key)==null )    // If not already in KV, put it there
      DKV.put(nv._key, nv);
    return rv;
  }
  public Frame extractFrame(int startIdx, int endIdx) {
    Frame f = subframe(startIdx, endIdx);
    remove(startIdx, endIdx);
    return f;
  }
  /** Create a subframe from given interval of columns.
   *
   * @param startIdx index of first column (inclusive)
   * @param endIdx index of the last column (exclusive)
   * @return a new frame containing specified interval of columns
   */
  Frame subframe(int startIdx, int endIdx) {
    return new Frame(Arrays.copyOfRange(_names,startIdx,endIdx),Arrays.copyOfRange(vecs(),startIdx,endIdx));
  }

  @Override public String errStr() { return "Dataset"; }

  public int  numCols() { return _keys.length; }
  public long numRows() { return anyVec().length(); }

  public Vec lastVec() {
    final Vec [] vecs = vecs();
    return vecs[vecs.length-1];
  }

  public Vec vec(String name){
    Vec [] vecs = vecs();
    for(int i = 0; i < _names.length; ++i)
      if(_names[i].equals(name))return vecs[i];
    return null;
  }
  /** Returns the vector by given index.
   * <p>The call is direct equivalent to call <code>vecs()[i]</code> and
   * it does not do any array bounds checking.</p>
   * @param idx idx of column
   * @return this frame idx-th vector, never returns <code>null</code>
   */
  public Vec vec(int idx) {
    Vec[] vecs = vecs();
    return vecs[idx];
  }

  // Force a cache-flush & reload, assuming vec mappings were altered remotely
  public final Vec[] reloadVecs() { _vecs=null; return vecs(); }

  /** Finds the first column with a matching name.  */
  public int find( String name ) {
    if (_names!=null)
      for( int i=0; i<_names.length; i++ )
        if( name.equals(_names[i]) )
          return i;
    return -1;
  }

  public int find( Vec vec ) {
    for( int i=0; i<_vecs.length; i++ )
      if( vec.equals(_vecs[i]) )
        return i;
    return -1;
  }

   /** Removes the first column with a matching name.  */
  public Vec remove( String name ) { return remove(find(name)); }

  /** Removes a numbered column. */
  public Vec [] remove( int [] idxs ) {
    for(int i :idxs)if(i < 0 || i > _vecs.length)
      throw new ArrayIndexOutOfBoundsException();
    Arrays.sort(idxs);
    Vec [] res = new Vec[idxs.length];
    Vec [] rem = new Vec[_vecs.length-idxs.length];
    String [] names = new String[rem.length];
    Key    [] keys  = new Key   [rem.length];
    int j = 0;
    int k = 0;
    int l = 0;
    for(int i = 0; i < _vecs.length; ++i) {
      if(j < idxs.length && i == idxs[j]) {
        ++j;
        res[k++] = _vecs[i];
      } else {
        rem  [l] = _vecs [i];
        names[l] = _names[i];
        keys [l] = _keys [i];
        ++l;
      }
    }
    _vecs = rem;
    _names = names;
    _keys = keys;
    assert l == rem.length && k == idxs.length;
    return res;
  }
  /** Removes a numbered column. */
  public Vec remove( int idx ) {
    int len = _names.length;
    if( idx < 0 || idx >= len ) return null;
    Vec v = vecs()[idx];
    System.arraycopy(_names,idx+1,_names,idx,len-idx-1);
    System.arraycopy(_vecs ,idx+1,_vecs ,idx,len-idx-1);
    System.arraycopy(_keys ,idx+1,_keys ,idx,len-idx-1);
    _names = Arrays.copyOf(_names,len-1);
    _vecs  = Arrays.copyOf(_vecs ,len-1);
    _keys  = Arrays.copyOf(_keys ,len-1);
    if( v == _col0 ) _col0 = null;
    return v;
  }

  /**
   * Remove given interval of columns from frame. Motivated by R intervals.
   * @param startIdx - start index of column (inclusive)
   * @param endIdx - end index of column (exclusive)
   * @return an array of remove columns
   */
  Vec[] remove(int startIdx, int endIdx) {
    int len = _names.length;
    int nlen = len - (endIdx-startIdx);
    String[] names = new String[nlen];
    Key[] keys = new Key[nlen];
    Vec[] vecs = new Vec[nlen];
    reloadVecs(); // force vecs reload
    if (startIdx > 0) {
      System.arraycopy(_names, 0, names, 0, startIdx);
      System.arraycopy(_vecs,  0, vecs,  0, startIdx);
      System.arraycopy(_keys,  0, keys,  0, startIdx);
    }
    nlen -= startIdx;
    if (endIdx < _names.length+1) {
      System.arraycopy(_names, endIdx, names, startIdx, nlen);
      System.arraycopy(_vecs,  endIdx, vecs,  startIdx, nlen);
      System.arraycopy(_keys,  endIdx, keys,  startIdx, nlen);
    }

    Vec[] vec = Arrays.copyOfRange(vecs(),startIdx,endIdx);
    _names = names;
    _vecs = vecs;
    _keys = keys;
    _col0 = null;
    return vec;
  }

  // --------------------------------------------
  // Utilities to help external Frame constructors, e.g. Spark.

  // Make an initial Frame & lock it for writing.  Build Vec Keys.
  public void preparePartialFrame( String[] names ) {
    // Nuke any prior frame (including freeing storage) & lock this one
    if( _keys != null ) delete_and_lock(null);
    else write_lock(null);
    _names = names;
    _keys = new Vec.VectorGroup().addVecs(names.length);
    // No Vectors tho!!! These will be added *after* the import
  }

  // Only serialize strings, not H2O internal structures

  // Make NewChunks to for holding data from e.g. Spark
  public static NewChunk[] createNewChunks( String name, int cidx ) {
    Frame fr = DKV.get(Key.make(name)).get();
    NewChunk[] nchks = new NewChunk[fr.numCols()];
    for( int i=0; i<nchks.length; i++ )
      nchks[i] = new NewChunk(new AppendableVec(fr._keys[i]),cidx);
    return nchks;
  }

  // Compress & DKV.put NewChunks
  public static void closeNewChunks( NewChunk[] nchks ) {
    Futures fs = new Futures();
    for( NewChunk nchk : nchks ) nchk.close(fs);
    fs.blockForPending();
  }

  // Build real Vecs from loose Chunks, and finalize this Frame
  public void finalizePartialFrame( long[] espc ) {
    // Compute elems-per-chunk.
    // Roll-up elem counts, so espc[i] is the starting element# of chunk i.
    int nchunk = espc.length;
    long espc2[] = new long[nchunk+1]; // Shorter array
    long x=0;                   // Total row count so far
    for( int i=0; i<nchunk; i++ ) {
      espc2[i] = x;             // Start elem# for chunk i
      x += espc[i];             // Raise total elem count
    }
    espc2[nchunk]=x;            // Total element count in last

    // For all Key/Vecs - insert Vec header
    System.out.println("GROINK!");
    Futures fs = new Futures();
    _vecs = new Vec[_keys.length];
    for( int i=0; i<_keys.length; i++ ) {
      // Insert Vec header
      Vec vec = _vecs[i] = new Vec(_keys[i], espc2, null/*no enum*/, false/*not UUID*/, false/*not String*/, (byte)-1/*not Time*/);
      DKV.put(_keys[i],vec,fs);             // Inject the header
      System.out.println(vec.toString());
    }
    fs.blockForPending();
    System.out.println("GROK!");
  }

  // --------------------------------------------------------------------------
  // In support of R, a generic Deep Copy & Slice.
  // Semantics are a little odd, to match R's.
  // Each dimension spec can be:
  //   null - all of them
  //   a sorted list of negative numbers (no dups) - all BUT these
  //   an unordered list of positive - just these, allowing dups
  // The numbering is 1-based; zero's are not allowed in the lists, nor are out-of-range.
  final int MAX_EQ2_COLS = 100000;      // FIXME.  Put this in a better spot.
  public Frame deepSlice( Object orows, Object ocols ) {
    // ocols is either a long[] or a Frame-of-1-Vec
    long[] cols;
    if( ocols == null ) cols = null;
    else if (ocols instanceof long[]) cols = (long[])ocols;
    else if (ocols instanceof Frame) {
      Frame fr = (Frame) ocols;
      if (fr.numCols() != 1)
        throw new IllegalArgumentException("Columns Frame must have only one column (actually has " + fr.numCols() + " columns)");
      long n = fr.anyVec().length();
      if (n > MAX_EQ2_COLS)
        throw new IllegalArgumentException("Too many requested columns (requested " + n +", max " + MAX_EQ2_COLS + ")");
      cols = new long[(int)n];
      Vec v = fr.anyVec();
      for (long i = 0; i < v.length(); i++)
        cols[(int)i] = v.at8(i);
    } else
      throw new IllegalArgumentException("Columns is specified by an unsupported data type (" + ocols.getClass().getName() + ")");

    // Since cols is probably short convert to a positive list.
    int c2[];
    if( cols==null ) {
      c2 = new int[numCols()];
      for( int i=0; i<c2.length; i++ ) c2[i]=i;
    } else if( cols.length==0 ) {
      c2 = new int[0];
    } else if( cols[0] >= 0 ) {
      c2 = new int[cols.length];
      for( int i=0; i<cols.length; i++ )
        c2[i] = (int)cols[i]; // Conversion of 1-based cols to 0-based is handled by a 1-based front-end!
    } else {
      c2 = new int[numCols()-cols.length];
      int j=0;
      for( int i=0; i<numCols(); i++ ) {
        if( j >= cols.length || i < (-cols[j]) ) c2[i-j] = i;
        else j++;
      }
    }
    for (int aC2 : c2)
      if (aC2 >= numCols())
        throw new IllegalArgumentException("Trying to select column " + (aC2 + 1) + " but only " + numCols() + " present.");
    if( c2.length==0 )
      throw new IllegalArgumentException("No columns selected (did you try to select column 0 instead of column 1?)");

    // Do Da Slice
    // orows is either a long[] or a Vec
    if (orows == null)
      return new DeepSlice(null,c2,vecs()).doAll(c2.length,this).outputFrame(names(c2),domains(c2));
    else if (orows instanceof long[]) {
      final long CHK_ROWS=1000000;
      final long[] rows = (long[])orows;
      if (this.numRows() == 0) {
        return this;
      }
      if( rows.length==0 || rows[0] < 0 ) {
        if (rows.length != 0 && rows[0] < 0) {
          Vec v0 = this.anyVec().makeZero();
          Vec v = new MRTask() {
            @Override public void map(Chunk cs) {
              for (long er : rows) {
                if (er >= 0) continue;
                er = Math.abs(er);
                if (er < cs._start || er > (cs.len() + cs._start - 1)) continue;
                cs.set0((int) (er - cs._start), 1);
              }
            }
          }.doAll(v0).getResult()._fr.anyVec();
          Keyed.remove(v0._key);
          Frame slicedFrame = new DeepSlice(rows, c2, vecs()).doAll(c2.length, this.add("select_vec", v)).outputFrame(names(c2), domains(c2));
          Keyed.remove(v._key);
          Keyed.remove(this.remove(this.numCols()-1)._key);
          return slicedFrame;
        } else {
          return new DeepSlice(rows.length == 0 ? null : rows, c2, vecs()).doAll(c2.length, this).outputFrame(names(c2), domains(c2));
        }
      }
      // Vec'ize the index array
      Futures fs = new Futures();
      AppendableVec av = new AppendableVec(Vec.newKey(Key.make("rownames")));
      int r = 0;
      int c = 0;
      while (r < rows.length) {
        NewChunk nc = new NewChunk(av, c);
        long end = Math.min(r+CHK_ROWS, rows.length);
        for (; r < end; r++) {
          nc.addNum(rows[r]);
        }
        nc.close(c++, fs);
      }
      Vec c0 = av.close(fs);   // c0 is the row index vec
      fs.blockForPending();
      Frame ff = new Frame(new String[]{"rownames"}, new Vec[]{c0});
      Frame fr2 = new Slice(c2, this).doAll(c2.length,ff)
              .outputFrame(names(c2), domains(c2));
      ff.delete();
      Keyed.remove(c0._key);  // Remove hidden vector
      Keyed.remove(av._key);
      return fr2;
    }
    Frame frows = (Frame)orows;
    Vec vrows = frows.anyVec();
    // It's a compatible Vec; use it as boolean selector.
    // Build column names for the result.
    Vec [] vecs = new Vec[c2.length+1];
    String [] names = new String[c2.length+1];
    for(int i = 0; i < c2.length; ++i){
      vecs[i] = _vecs[c2[i]];
      names[i] = _names[c2[i]];
    }
    vecs[c2.length] = vrows;
    names[c2.length] = "predicate";
    Frame ff = new Frame(names, vecs);
    Frame sliced = new DeepSelect().doAll(c2.length,ff).outputFrame(names(c2),domains(c2));
    ff.delete(); Keyed.remove(vrows._key); frows.delete();
    for (Vec v : vecs) Keyed.remove(v._key);
    return sliced;
  }

  // Slice and return in the form of new chunks.
  private static class Slice extends MRTask<Slice> {
    final Frame  _base;   // the base frame to slice from
    final int[]  _cols;
    Slice(int[] cols, Frame base) { _cols = cols; _base = base; }
    @Override public void map(Chunk[] ix, NewChunk[] ncs) {
      final Vec[] vecs = new Vec[_cols.length];
      final Vec   anyv = _base.anyVec();
      final long  nrow = anyv.length();
      long  r    = ix[0].at80(0);
      int   last_ci = anyv.elem2ChunkIdx(r<nrow?r:0); // memoize the last chunk index
      long  last_c0 = anyv._espc[last_ci];            // ...         last chunk start
      long  last_c1 = anyv._espc[last_ci + 1];        // ...         last chunk end
      Chunk[] last_cs = new Chunk[vecs.length];       // ...         last chunks
      for (int c = 0; c < _cols.length; c++) {
        vecs[c] = _base.vecs()[_cols[c]];
        last_cs[c] = vecs[c].chunkForChunkIdx(last_ci);
      }
      for (int i = 0; i < ix[0].len(); i++) {
        // select one row
        r = ix[0].at80(i);   // next row to select
        if (r < 0) continue;
        if (r >= nrow) {
          for (int c = 0; c < vecs.length; c++) ncs[c].addNum(Double.NaN);
        } else {
          if (r < last_c0 || r >= last_c1) {
            last_ci = anyv.elem2ChunkIdx(r);
            last_c0 = anyv._espc[last_ci];
            last_c1 = anyv._espc[last_ci + 1];
            for (int c = 0; c < vecs.length; c++)
              last_cs[c] = vecs[c].chunkForChunkIdx(last_ci);
          }
          for (int c = 0; c < vecs.length; c++)
            if( vecs[c].isUUID() ) ncs[c].addUUID(last_cs[c],r);
            else                   ncs[c].addNum (last_cs[c].at(r));
        }
      }
    }
  }

  // Bulk (expensive) copy from 2nd cols into 1st cols.
  // Sliced by the given cols & rows
  private static class DeepSlice extends MRTask<DeepSlice> {
    final int  _cols[];
    final long _rows[];
    final byte _isInt[];
    DeepSlice( long rows[], int cols[], Vec vecs[] ) {
      _cols=cols;
      _rows=rows;
      _isInt = new byte[cols.length];
      for( int i=0; i<cols.length; i++ )
        _isInt[i] = (byte)(vecs[cols[i]].isInt() ? 1 : 0);
    }

    @Override public boolean logVerbose() { return false; }

    @Override public void map( Chunk chks[], NewChunk nchks[] ) {
      long rstart = chks[0].start();
      int rlen = chks[0].len();  // Total row count
      int rx = 0;               // Which row to in/ex-clude
      int rlo = 0;              // Lo/Hi for this block of rows
      int rhi = rlen;
      while (true) {           // Still got rows to include?
        if (_rows != null) {   // Got a row selector?
          if (rx >= _rows.length) break; // All done with row selections
          long r = _rows[rx++];// Next row selector
          if (r < rstart) continue;
          rlo = (int) (r - rstart);
          rhi = rlo + 1;        // Stop at the next row
          while (rx < _rows.length && (_rows[rx] - rstart) == rhi && rhi < rlen) {
            rx++;
            rhi++;      // Grab sequential rows
          }
        }
        // Process this next set of rows
        // For all cols in the new set
        for (int i = 0; i < _cols.length; i++) {
          Chunk oc = chks[_cols[i]];
          NewChunk nc = nchks[i];
          if (_isInt[i] == 1) { // Slice on integer columns
            for (int j = rlo; j < rhi; j++)
              if (oc._vec.isUUID()) nc.addUUID(oc, j);
              else if (oc.isNA0(j)) nc.addNA();
              else nc.addNum(oc.at80(j), 0);
          } else {                // Slice on double columns
            for (int j = rlo; j < rhi; j++)
              nc.addNum(oc.at0(j));
          }
        }
        rlo = rhi;
        if (_rows == null) break;
      }
    }
  }

  /**
   *  Last column is a bit vec indicating whether or not to take the row.
   */
  private static class DeepSelect extends MRTask<DeepSelect> {
    @Override public void map( Chunk chks[], NewChunk nchks[] ) {
      Chunk pred = chks[chks.length-1];
      for(int i = 0; i < pred.len(); ++i) {
        if(pred.at0(i) != 0) {
          for( int j = 0; j < chks.length - 1; j++ ) {
            Chunk chk = chks[j];
            if( chk._vec.isUUID() ) nchks[j].addUUID(chk,i);
            else nchks[j].addNum(chk.at0(i));
          }
        }
      }
    }
  }

  private Frame copyRollups( Frame fr, boolean isACopy ) {
    if( !isACopy ) return fr; // Not a clean copy, do not copy rollups (will do rollups "the hard way" on first ask)
    Vec vecs0[] = vecs();
    Vec vecs1[] = fr.vecs();
    for( int i=0; i<fr._names.length; i++ ) {
      assert vecs1[i].naCnt()== -1; // not computed yet, right after slice
      Vec v0 = vecs0[find(fr._names[i])];
      Vec v1 = vecs1[i];
//      v1.setRollupStats(v0);
    }
    return fr;
  }
}
