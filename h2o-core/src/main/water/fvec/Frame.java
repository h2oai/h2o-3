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

  public Frame( Vec... vecs ){ this(null,vecs);}
  public Frame( String names[], Vec vecs[] ) { this(null,names,vecs); }
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

  /** All the domains for enum columns; null for non-enum columns.  */
  public String[][] domains() {
    String ds[][] = new String[vecs().length][];
    for( int i=0; i<vecs().length; i++ )
      ds[i] = vecs()[i].domain();
    return ds;
  }

  public String[] names() { return _names; }

  // For MRTask: allow rollups for all written-into vecs
  public Futures postWrite(Futures fs) {
    for( Vec v : vecs() ) v.postWrite(fs);
    return fs;
  }

  /** Actually remove/delete all Vecs from memory, not just from the Frame. */
  @Override public Futures remove(Futures fs) {
    for( Vec v : vecs() ) v.remove(fs);
    _names = new String[0];
    _vecs = new Vec[0];
    _keys = new Key[0];
    super.remove(fs);
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
  public final long byteSize() { throw H2O.unimpl(); }

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



}
