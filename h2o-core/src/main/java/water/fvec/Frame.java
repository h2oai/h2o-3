package water.fvec;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;

import water.DKV;
import water.Futures;
import water.H2O;
import water.Iced;
import water.Key;
import water.Keyed;
import water.Lockable;
import water.MRTask;
import water.Value;
import water.parser.BufferedString;
import water.util.Log;
import water.util.PrettyPrint;
import water.util.TwoDimTable;

/** A collection of named {@link Vec}s, essentially an R-like Distributed Data Frame.
 *
 *  <p>Frames represent a large distributed 2-D table with named columns
 *  ({@link Vec}s) and numbered rows.  A reasonable <em>column</em> limit is
 *  100K columns, but there's no hard-coded limit.  There's no real <em>row</em>
 *  limit except memory; Frames (and Vecs) with many billions of rows are used
 *  routinely.
 *
 *  <p>A Frame is a collection of named Vecs; a Vec is a collection of numbered
 *  {@link Chunk}s.  A Frame is small, cheaply and easily manipulated, it is
 *  commonly passed-by-Value.  It exists on one node, and <em>may</em> be
 *  stored in the {@link DKV}.  Vecs, on the other hand, <em>must</em> be stored in the
 *  {@link DKV}, as they represent the shared common management state for a collection
 *  of distributed Chunks.
 *
 *  <p>Multiple Frames can reference the same Vecs, although this sharing can
 *  make Vec lifetime management complex.  Commonly temporary Frames are used
 *  to work with a subset of some other Frame (often during algorithm
 *  execution, when some columns are dropped from the modeling process).  The
 *  temporary Frame can simply be ignored, allowing the normal GC process to
 *  reclaim it.  Such temp Frames usually have a {@code null} key.
 *
 *  <p>All the Vecs in a Frame belong to the same {@link Vec.VectorGroup} which
 *  then enforces {@link Chunk} row alignment across Vecs (or at least enforces
 *  a low-cost access model).  Parallel and distributed execution touching all
 *  the data in a Frame relies on this alignment to get good performance.
 *  
 *  <p>Example: Make a Frame from a CSV file:<pre>
 *  File file = ...
 *  NFSFileVec nfs = NFSFileVec.make(file); // NFS-backed Vec, lazily read on demand
 *  Frame fr = water.parser.ParseDataset.parse(Key.make("myKey"),nfs._key);
 *  </pre>
 * 
 *  <p>Example: Find and remove the Vec called "unique_id" from the Frame,
 *  since modeling with a unique_id can lead to overfitting:
 *  <pre>
 *  Vec uid = fr.remove("unique_id");
 *  </pre>
 *
 *  <p>Example: Move the response column to the last position:
 *  <pre>
 *  fr.add("response",fr.remove("response"));
 *  </pre>
 *
 */
public class Frame extends Lockable<Frame> {
  /** Vec names */
  public String[] _names;
  private boolean _lastNameBig; // Last name is "Cxxx" and has largest number
  private Key<Vec>[] _keys;     // Keys for the vectors
  private transient Vec[] _vecs; // The Vectors (transient to avoid network traffic)
  private transient Vec _col0; // First readable vec; fast access to the VectorGroup's Chunk layout

  /** Creates an internal frame composed of the given Vecs and default names.  The frame has no key. */
  public Frame( Vec... vecs ){ this(null,vecs);}
  /** Creates an internal frame composed of the given Vecs and names.  The frame has no key. */
  public Frame( String names[], Vec vecs[] ) { this(null,names,vecs); }
  /** Creates an empty frame with given key. */
  public Frame( Key key ) {
    this(key,null,new Vec[0]);
  }

  /**
   * Special constructor for data with unnamed columns (e.g. svmlight) bypassing *all* checks.
   * @param key
   * @param vecs
   * @param noChecks
   */
  public Frame( Key key, Vec vecs[], boolean noChecks) {
    super(key);
    assert noChecks;
    _vecs = vecs;
    _names = new String[vecs.length];
    _keys = new Key[vecs.length];
    for (int i = 0; i < vecs.length; i++) {
      _names[i] = defaultColName(i);
      _keys[i] = vecs[i]._key;
    }
  }

  /** Creates a frame with given key, names and vectors. */
  public Frame( Key key, String names[], Vec vecs[] ) {
    super(key);

    // Require all Vecs already be installed in the K/V store
    for( Vec vec : vecs ) DKV.prefetch(vec._key);
    for( Vec vec : vecs ) assert DKV.get(vec._key) != null : " null vec: "+vec._key;

    // Always require names
    if( names==null ) {         // Make default names, all known to be unique
      _names = new String[vecs.length];
      _keys  = new Key   [vecs.length];
      _vecs  = vecs;
      for( int i=0; i<vecs.length; i++ ) _names[i] = defaultColName(i);
      for( int i=0; i<vecs.length; i++ ) _keys [i] = vecs[i]._key;
      for( int i=0; i<vecs.length; i++ ) checkCompatible(_names[i],vecs[i]);
      _lastNameBig = true;
    } else {
      // Make empty to dodge asserts, then "add()" them all which will check
      // for compatible Vecs & names.
      _names = new String[0];
      _keys  = new Key   [0];
      _vecs  = new Vec   [0];
      add(names,vecs);
    }
    assert _names.length == vecs.length;
  }

  /** Deep copy of Vecs and Keys and Names (but not data!) to a new random Key.
   *  The resulting Frame does not share with the original, so the set of Vecs
   *  can be freely hacked without disturbing the original Frame. */
  public Frame( Frame fr ) {
    super( Key.make() );
    _names= fr._names.clone();
    _keys = fr._keys .clone();
    _vecs = fr.vecs().clone();
    _lastNameBig = fr._lastNameBig;
  }

  /** Default column name maker */
  public static String defaultColName( int col ) { return "C"+(1+col); }

  // Make unique names.  Efficient for the special case of appending endless
  // versions of "C123" style names where the next name is +1 over the prior
  // name.  All other names take the O(n^2) lookup.
  private int pint( String name ) {
    try { return Integer.valueOf(name.substring(1)); } 
    catch( NumberFormatException fe ) { }
    return 0;
  }
  public String uniquify( String name ) {
    String n = name;
    int lastName = 0;
    if( name.length() > 0 && name.charAt(0)=='C' )
      lastName = pint(name);
    if( _lastNameBig && _names.length > 0 ) {
      String last = _names[_names.length-1];
      if( !last.equals("") && last.charAt(0)=='C' && lastName == pint(last)+1 )
        return name;
    }
    int cnt=0, again, max=0;
    do {
      again = cnt;
      for( String s : _names ) {
        if( lastName > 0 && s.charAt(0)=='C' )
          max = Math.max(max,pint(s));
        if( n.equals(s) )
          n = name+(cnt++);
      }
    } while( again != cnt );
    if( lastName == max+1 ) _lastNameBig = true;
    return n;
  }

  /** Check that the vectors are all compatible.  All Vecs have their content
   *  sharded using same number of rows per chunk, and all names are unique.
   *  Throw an IAE if something does not match.  */
  private void checkCompatible( String name, Vec vec ) {
    if( vec instanceof AppendableVec ) return; // New Vectors are endlessly compatible
    Vec v0 = anyVec();
    if( v0 == null ) return; // No fixed-size Vecs in the Frame
    // Vector group has to be the same, or else the layout has to be the same,
    // or else the total length has to be small.
    if( !v0.checkCompatible(vec) ) {
      if(!Vec.VectorGroup.sameGroup(v0,vec))
        Log.err("Unexpected incompatible vector group, " + v0.group() + " != " + vec.group());
      if(!Arrays.equals(v0.espc(), vec.espc()))
        Log.err("Unexpected incompatible espc, " + Arrays.toString(v0.espc()) + " != " + Arrays.toString(vec.espc()));
      throw new IllegalArgumentException("Vec " + name + " is not compatible with the rest of the frame");
    }
  }

  /** Quick compatibility check between Frames.  Used by some tests for efficient equality checks. */
  public boolean isCompatible( Frame fr ) {
    if( numCols() != fr.numCols() ) return false;
    if( numRows() != fr.numRows() ) return false;
    for( int i=0; i<vecs().length; i++ )
      if( !vecs()[i].checkCompatible(fr.vecs()[i]) )
        return false;
    return true;
  }

  /** Number of columns
   *  @return Number of columns */
  public int  numCols() { return _keys.length; }
  /** Number of columns with categoricals expanded
   * @return Number of columns with categoricals expanded into indicator columns */
  public int numColsExp() { return numColsExp(true, false); }
  public int numColsExp(boolean useAllFactorLevels, boolean missingBucket) {
    if(_vecs == null) return 0;
    int cols = 0;
    for(int i = 0; i < _vecs.length; i++) {
      if(_vecs[i].isCategorical() && _vecs[i].domain() != null)
        cols += _vecs[i].domain().length - (useAllFactorLevels ? 0 : 1) + (missingBucket ? 1 : 0);
      else cols++;
    }
    return cols;
  }
  /** Number of rows
   *  @return Number of rows */
  public long numRows() { Vec v = anyVec(); return v==null ? 0 : v.length(); }

  /**
   * Number of degrees of freedom (#numerical columns + sum(#categorical levels))
   * @return Number of overall degrees of freedom
   */
  public long degreesOfFreedom() {
    long dofs = 0;
    String[][] dom = domains();
    for (int i=0; i<numCols(); ++i) {
      if (dom[i] == null) {
        dofs++;
      } else {
        dofs+=dom[i].length;
      }
    }
    return dofs;
  }

  /** Returns the first readable vector. 
   *  @return the first readable Vec */
  public final Vec anyVec() {
    Vec c0 = _col0; // single read
    if( c0 != null ) return c0;
    for( Vec v : vecs() )
      if( v.readable() )
        return (_col0 = v);
    return null;
  }

  /** The array of column names.
   *  @return the array of column names */
  public String[] names() { return _names; }

  /** A single column name.
   *  @return the column name */
  public String name(int i) { return _names[i]; } // TODO: saw a non-reproducible NPE here

  /** The array of keys.
   * @return the array of keys for each vec in the frame.
   */
  public Key[] keys() { return _keys; }

  /** The internal array of Vecs.  For efficiency Frames contain an array of
   *  Vec Keys - and the Vecs themselves are lazily loaded from the {@link DKV}.
   *  @return the internal array of Vecs */
  public final Vec[] vecs() {
    Vec[] tvecs = _vecs; // read the content
    return tvecs == null ? (_vecs=vecs_impl()) : tvecs;
  }
  public final Vec[] vecs(int [] idxs) {
    Vec [] all = vecs();
    Vec [] res = new Vec[idxs.length];
    for(int i = 0; i < idxs.length; ++i)
      res[i] = all[idxs[i]];
    return res;
  }

  public Vec[] vecs(String[] names) {
    Vec [] res = new Vec[names.length];
    for(int i = 0; i < names.length; ++i)
      res[i] = vec(names[i]);
    return res;
  }

  // Compute vectors for caching
  private Vec[] vecs_impl() {
    // Load all Vec headers; load them all in parallel by starting prefetches
    for( Key<Vec> key : _keys ) DKV.prefetch(key);
    Vec [] vecs = new Vec[_keys.length];
    for( int i=0; i<_keys.length; i++ ) vecs[i] = _keys[i].get();
    return vecs;
  }

  /** Convenience to accessor for last Vec 
   *  @return last Vec */
  public Vec lastVec() { vecs(); return _vecs [_vecs.length -1]; }
  /** Convenience to accessor for last Vec name
   *  @return last Vec name */
  public String lastVecName() {  return _names[_names.length-1]; }

  /** Force a cache-flush and reload, assuming vec mappings were altered
   *  remotely, or that the _vecs array was shared and now needs to be a
   *  defensive copy. 
   *  @return the new instance of the Frame's Vec[] */
  public final Vec[] reloadVecs() { _vecs=null; return vecs(); }
  
  /** Returns the Vec by given index, implemented by code: {@code vecs()[idx]}.
   *  @param idx idx of column
   *  @return this frame idx-th vector, never returns <code>null</code> */
  public final Vec vec(int idx) { return vecs()[idx]; }

  /**  Return a Vec by name, or null if missing
   *  @return a Vec by name, or null if missing */
  public Vec vec(String name) { int idx = find(name); return idx==-1 ? null : vecs()[idx]; }

  /**   Finds the column index with a matching name, or -1 if missing
   *  @return the column index with a matching name, or -1 if missing */
  public int find( String name ) {
    if( name == null ) return -1;
    assert _names != null;
    for( int i=0; i<_names.length; i++ )
      if( name.equals(_names[i]) )
        return i;
    return -1;
  }

  /**   Finds the matching column index, or -1 if missing
   *  @return the matching column index, or -1 if missing */
  public int find( Vec vec ) {
    Vec[] vecs = vecs(); //warning: side-effect
    if (vec == null) return -1;
    for( int i=0; i<vecs.length; i++ )
      if( vec.equals(vecs[i]) )
        return i;
    return -1;
  }

  /**   Finds the matching column index, or -1 if missing
   *  @return the matching column index, or -1 if missing */
  public int find( Key key ) {
    for( int i=0; i<_keys.length; i++ )
      if( key.equals(_keys[i]) )
        return i;
    return -1;
  }

  /** Bulk {@link #find(String)} api
   *  @return An array of column indices matching the {@code names} array */
  public int[] find(String[] names) {
    if( names == null ) return null;
    int[] res = new int[names.length];
    for(int i = 0; i < names.length; ++i)
      res[i] = find(names[i]);
    return res;
  }

  public void insertVec(int i, String name, Vec vec) {
    String [] names = new String[_names.length+1];
    Vec [] vecs = new Vec[_vecs.length+1];
    Key [] keys = new Key[_keys.length+1];
    System.arraycopy(_names,0,names,0,i);
    System.arraycopy(_vecs,0,vecs,0,i);
    System.arraycopy(_keys,0,keys,0,i);
    names[i] = name;
    vecs[i] = vec;
    keys[i] = vec._key;
    System.arraycopy(_names,i,names,i+1,_names.length-i);
    System.arraycopy(_vecs,i,vecs,i+1,_vecs.length-i);
    System.arraycopy(_keys,i,keys,i+1,_keys.length-i);
    _names = names;
    _vecs = vecs;
    _keys = keys;
  }

  /** Pair of (column name, Frame key). */
  public static class VecSpecifier extends Iced {
    public Key<Frame> _frame;
    String _column_name;

    public Vec vec() {
      Value v = DKV.get(_frame);
      if (null == v) return null;
      Frame f = v.get();
      if (null == f) return null;
      return f.vec(_column_name);
    }
  }

  /** Type for every Vec */
  public byte[] types() {
    Vec[] vecs = vecs();
    byte bs[] = new byte[vecs.length];
    for( int i=0; i<vecs.length; i++ )
      bs[i] = vecs[i]._type;
    return bs;
  }

  /** String name for each Vec type */
  public String[] typesStr() {  // typesStr not strTypes since shows up in intelliJ next to types
    Vec[] vecs = vecs();
    String s[] = new String[vecs.length];
    for(int i=0;i<vecs.length;++i)
      s[i] = vecs[i].get_type_str();
    return s;
  }

  /** All the domains for categorical columns; null for non-categorical columns.
   *  @return the domains for categorical columns */
  public String[][] domains() {
    Vec[] vecs = vecs();
    String ds[][] = new String[vecs.length][];
    for( int i=0; i<vecs.length; i++ )
      ds[i] = vecs[i].domain();
    return ds;
  }

  /** Number of categorical levels for categorical columns; -1 for non-categorical columns.
   * @return the number of levels for categorical columns */
  public int[] cardinality() {
    Vec[] vecs = vecs();
    int[] card = new int[vecs.length];
    for( int i=0; i<vecs.length; i++ )
      card[i] = vecs[i].cardinality();
    return card;
  }

  private Vec[] bulkRollups() {
    Futures fs = new Futures();
    Vec[] vecs = vecs();
    for(Vec v : vecs)  v.startRollupStats(fs);
    fs.blockForPending();
    return vecs;
  }

  /** Majority class for categorical columns; -1 for non-categorical columns.
   * @return the majority class for categorical columns */
  public int[] modes() {
    Vec[] vecs = bulkRollups();
    int[] modes = new int[vecs.length];
    for( int i = 0; i < vecs.length; i++ ) {
      modes[i] = vecs[i].isCategorical() ? vecs[i].mode() : -1;
    }
    return modes;
  }

  /** All the column means.
   *  @return the mean of each column */
  public double[] means() {
    Vec[] vecs = bulkRollups();
    double[] means = new double[vecs.length];
    for( int i = 0; i < vecs.length; i++ )
      means[i] = vecs[i].mean();
    return means;
  }

  /** One over the standard deviation of each column.
   *  @return Reciprocal the standard deviation of each column */
  public double[] mults() {
    Vec[] vecs = bulkRollups();
    double[] mults = new double[vecs.length];
    for( int i = 0; i < vecs.length; i++ ) {
      double sigma = vecs[i].sigma();
      mults[i] = standardize(sigma) ? 1.0 / sigma : 1.0;
    }
    return mults;
  }

  private static boolean standardize(double sigma) {
    // TODO unify handling of constant columns
    return sigma > 1e-6;
  }

  /** The {@code Vec.byteSize} of all Vecs
   *  @return the {@code Vec.byteSize} of all Vecs */
  public long byteSize() {
    Vec[] vecs = bulkRollups();
    long sum=0;
    for (Vec vec : vecs) sum += vec.byteSize();
    return sum;
  }

  /** 64-bit checksum of the checksums of the vecs.  SHA-265 checksums of the
   *  chunks are XORed together.  Since parse always parses the same pieces of
   *  files into the same offsets in some chunk this checksum will be
   *  consistent across reparses.
   *  @return 64-bit Frame checksum */
  @Override protected long checksum_impl() {
    Vec[] vecs = vecs();
    long _checksum = 0;
    for( int i = 0; i < _names.length; ++i ) {
      long vec_checksum = vecs[i].checksum();
      _checksum ^= vec_checksum;
      long tmp = (2147483647L * i);
      _checksum ^= tmp;
    }
    _checksum *= (0xBABE + Arrays.hashCode(_names));

    // TODO: include column types?  Vec.checksum() should include type?
    return _checksum;
  }

  // Add a bunch of vecs
  public void add( String[] names, Vec[] vecs) {
    bulkAdd(names, vecs);
  }
  public void add( String[] names, Vec[] vecs, int cols ) {
    if (null == vecs || null == names) return;
    if (cols == names.length && cols == vecs.length) {
      bulkAdd(names, vecs);
    } else {
      for (int i = 0; i < cols; i++)
        add(names[i], vecs[i]);
    }
  }

  /** Append multiple named Vecs to the Frame.  Names are forced unique, by appending a
   *  unique number if needed.
   */
  private void bulkAdd(String[] names, Vec[] vecs) {
    String[] tmpnames = names.clone();
    int N = names.length;
    assert(names.length == vecs.length):"names = " + Arrays.toString(names) + ", vecs len = " + vecs.length;
    for (int i=0; i<N; ++i) {
      vecs[i] = vecs[i] != null ? makeCompatible(new Frame(vecs[i])).anyVec() : null;
      checkCompatible(tmpnames[i]=uniquify(tmpnames[i]),vecs[i]);  // Throw IAE is mismatch
    }

    int ncols = _keys.length;
    _names = Arrays.copyOf(_names, ncols+N);
    _keys = Arrays.copyOf(_keys, ncols+N);
    _vecs = Arrays.copyOf(_vecs, ncols+N);
    for (int i=0; i<N; ++i) {
      _names[ncols+i] = tmpnames[i];
      _keys[ncols+i] = vecs[i]._key;
      _vecs[ncols+i] = vecs[i];
    }
  }

  /** Append a named Vec to the Frame.  Names are forced unique, by appending a
   *  unique number if needed.
   *  @return the added Vec, for flow-coding */
  public Vec add( String name, Vec vec ) {
    vec = makeCompatible(new Frame(vec)).anyVec();
    checkCompatible(name=uniquify(name),vec);  // Throw IAE is mismatch
    int ncols = _keys.length;
    _names = Arrays.copyOf(_names,ncols+1);  _names[ncols] = name;
    _keys  = Arrays.copyOf(_keys ,ncols+1);  _keys [ncols] = vec._key;
    _vecs  = Arrays.copyOf(_vecs ,ncols+1);  _vecs [ncols] = vec;
    return vec;
  }

  /** Append a Frame onto this Frame.  Names are forced unique, by appending
   *  unique numbers if needed.
   *  @return the expanded Frame, for flow-coding */
  public Frame add( Frame fr ) { add(fr._names,fr.vecs(),fr.numCols()); return this; }

  /** Insert a named column as the first column */
  public Frame prepend( String name, Vec vec ) {
    if( find(name) != -1 ) throw new IllegalArgumentException("Duplicate name '"+name+"' in Frame");
    if( _vecs.length != 0 ) {
      if( !anyVec().group().equals(vec.group()) && !Arrays.equals(anyVec().espc(),vec.espc()) )
        throw new IllegalArgumentException("Vector groups differs - adding vec '"+name+"' into the frame " + Arrays.toString(_names));
      if( numRows() != vec.length() )
        throw new IllegalArgumentException("Vector lengths differ - adding vec '"+name+"' into the frame " + Arrays.toString(_names));
    }
    final int len = _names != null ? _names.length : 0;
    String[] _names2 = new String[len+1];
    Vec[]    _vecs2  = new Vec   [len+1];
    Key[]    _keys2  = new Key   [len+1];
    _names2[0] = name;
    _vecs2 [0] = vec ;
    _keys2 [0] = vec._key;
    System.arraycopy(_names, 0, _names2, 1, len);
    System.arraycopy(_vecs,  0, _vecs2,  1, len);
    System.arraycopy(_keys,  0, _keys2,  1, len);
    _names = _names2;
    _vecs  = _vecs2;
    _keys  = _keys2;
    return this;
  }

  /** Swap two Vecs in-place; useful for sorting columns by some criteria */
  public void swap( int lo, int hi ) {
    assert 0 <= lo && lo < _keys.length;
    assert 0 <= hi && hi < _keys.length;
    if( lo==hi ) return;
    Vec vecs[] = vecs();
    Vec v   = vecs [lo]; vecs  [lo] = vecs  [hi]; vecs  [hi] = v;
    Key k   = _keys[lo]; _keys [lo] = _keys [hi]; _keys [hi] = k;
    String n=_names[lo]; _names[lo] = _names[hi]; _names[hi] = n;
  }

  /** Returns a subframe of this frame containing only vectors with desired names.
   *
   *  @param names list of vector names
   *  @return a new frame which collects vectors from this frame with desired names.
   *  @throws IllegalArgumentException if there is no vector with desired name in this frame.
   */
  public Frame subframe(String[] names) { return subframe(names, false, 0)[0]; }

  /** Returns a new frame composed of vectors of this frame selected by given names.
   *  The method replaces missing vectors by a constant column filled by given value.
   *  @param names names of vector to compose a subframe
   *  @param c value to fill missing columns.
   *  @return two frames, the first contains subframe, the second contains newly created constant vectors or null
   */
  public Frame[] subframe(String[] names, double c) { return subframe(names, true, c); }

  /** Create a subframe from this frame based on desired names.
   *  Throws an exception if desired column is not in this frame and <code>replaceBy</code> is <code>false</code>.
   *  Else replace a missing column by a constant column with given value.
   *
   *  @param names list of column names to extract
   *  @param replaceBy should be missing column replaced by a constant column
   *  @param c value for constant column
   *  @return array of 2 frames, the first is containing a desired subframe, the second one contains newly created columns or null
   *  @throws IllegalArgumentException if <code>replaceBy</code> is false and there is a missing column in this frame
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
    return new Frame[] { new Frame(Key.make("subframe"+Key.make().toString()), names,vecs), ccv>0 ?  new Frame(Key.make("subframe"+Key.make().toString()), Arrays.copyOf(cnames, ccv), Arrays.copyOf(cvecs,ccv)) : null };
  }

  /** Allow rollups for all written-into vecs; used by {@link MRTask} once
   *  writing is complete.
   *  @return the original Futures, for flow-coding */
  public Futures postWrite(Futures fs) {
    for( Vec v : vecs() ) v.postWrite(fs);
    return fs;
  }

  /** Actually remove/delete all Vecs from memory, not just from the Frame.
   *  @return the original Futures, for flow-coding */
  @Override public Futures remove_impl(Futures fs) {
    final Key[] keys = _keys;
    if( keys.length==0 ) return fs;
    final int ncs = anyVec().nChunks(); // TODO: do not call anyVec which loads all Vecs... only to delete them
    _names = new String[0];
    _vecs = new Vec[0];
    _keys = new Key[0];
    // Bulk dumb local remove - no JMM, no ordering, no safety.
    new MRTask() {
      @Override public void setupLocal() {
        for( Key k : keys ) if( k != null ) Vec.bulk_remove(k,ncs);
      }
    }.doAllNodes();

    return fs;
  }

  /** Replace one column with another. Caller must perform global update (DKV.put) on
   *  this updated frame.
   *  @return The old column, for flow-coding */
  public Vec replace(int col, Vec nv) {
    Vec rv = vecs()[col];
    nv = ((new Frame(rv)).makeCompatible(new Frame(nv))).anyVec();
    DKV.put(nv);
    assert DKV.get(nv._key)!=null; // Already in DKV
    assert rv.group().equals(nv.group());
    _vecs[col] = nv;
    _keys[col] = nv._key;
    return rv;
  }

  /** Create a subframe from given interval of columns.
   *  @param startIdx  index of first column (inclusive)
   *  @param endIdx index of the last column (exclusive)
   *  @return a new Frame containing specified interval of columns  */
  public Frame subframe(int startIdx, int endIdx) {
    return new Frame(Arrays.copyOfRange(_names,startIdx,endIdx),Arrays.copyOfRange(vecs(),startIdx,endIdx));
  }

  /** Split this Frame; return a subframe created from the given column interval, and
   *  remove those columns from this Frame. 
   *  @param startIdx index of first column (inclusive)
   *  @param endIdx index of the last column (exclusive)
   *  @return a new Frame containing specified interval of columns */
  public Frame extractFrame(int startIdx, int endIdx) {
    Frame f = subframe(startIdx, endIdx);
    remove(startIdx, endIdx);
    return f;
  }

  /** Removes the column with a matching name.  
   *  @return The removed column */
  public Vec remove( String name ) { return remove(find(name)); }

  public Frame remove( String[] names ) { 
    for( String name : names )
      remove(find(name));
    return this;
  }

  /** Removes a list of columns by index; the index list must be sorted
   *  @return an array of the removed columns */
  public Vec[] remove( int[] idxs ) {
    for( int i : idxs )
      if(i < 0 || i >= vecs().length)
        throw new ArrayIndexOutOfBoundsException();
    Arrays.sort(idxs);
    Vec[] res = new Vec[idxs.length];
    Vec[] rem = new Vec[_vecs.length-idxs.length];
    String[] names = new String[rem.length];
    Key   [] keys  = new Key   [rem.length];
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
    _names= names;
    _keys = keys;
    assert l == rem.length && k == idxs.length;
    return res;
  }

  /**  Removes a numbered column. 
   *  @return the removed column */
  public final Vec remove( int idx ) {
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

  /** Remove given interval of columns from frame.  Motivated by R intervals.
   *  @param startIdx - start index of column (inclusive)
   *  @param endIdx - end index of column (exclusive)
   *  @return array of removed columns  */
  Vec[] remove(int startIdx, int endIdx) {
    int len = _names.length;
    int nlen = len - (endIdx-startIdx);
    String[] names = new String[nlen];
    Key[] keys = new Key[nlen];
    Vec[] vecs = new Vec[nlen];
    vecs();
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

    Vec[] vecX = Arrays.copyOfRange(_vecs,startIdx,endIdx);
    _names = names;
    _vecs = vecs;
    _keys = keys;
    _col0 = null;
    return vecX;
  }

  /** Restructure a Frame completely */
  public void restructure( String[] names, Vec[] vecs) {
    restructure(names, vecs, vecs.length);
  }

  /** Restructure a Frame completely, but only for a specified number of columns (counting up)  */
  public void restructure( String[] names, Vec[] vecs, int cols) {
    // Make empty to dodge asserts, then "add()" them all which will check for
    // compatible Vecs & names.
    _names = new String[0];
    _keys  = new Key   [0];
    _vecs  = new Vec   [0];
    add(names,vecs,cols);
  }

  // --------------------------------------------
  // Utilities to help external Frame constructors, e.g. Spark.

  // Make an initial Frame & lock it for writing.  Build Vec Keys.
  void preparePartialFrame( String[] names ) {
    // Nuke any prior frame (including freeing storage) & lock this one
    if( _keys != null ) delete_and_lock(null);
    else write_lock(null);
    _names = names;
    _keys = new Vec.VectorGroup().addVecs(names.length);
    // No Vectors tho!!! These will be added *after* the import
  }

  // Only serialize strings, not H2O internal structures

  // Make NewChunks to for holding data from e.g. Spark.  Once per set of
  // Chunks in a Frame, before filling them.  This can be called in parallel
  // for different Chunk#'s (cidx); each Chunk can be filled in parallel.
  static NewChunk[] createNewChunks(String name, byte[] type, int cidx) {
    Frame fr = (Frame) Key.make(name).get();
    NewChunk[] nchks = new NewChunk[fr.numCols()];
    for (int i = 0; i < nchks.length; i++) {
      nchks[i] = new NewChunk(new AppendableVec(fr._keys[i], type[i]), cidx);
    }
    return nchks;
  }

  // Compress & DKV.put NewChunks.  Once per set of Chunks in a Frame, after
  // filling them.  Can be called in parallel for different sets of Chunks.
  static void closeNewChunks(NewChunk[] nchks) {
    Futures fs = new Futures();
    for (NewChunk nchk : nchks) {
      nchk.close(fs);
    }
    fs.blockForPending();
  }

  // Build real Vecs from loose Chunks, and finalize this Frame.  Called once
  // after any number of [create,close]NewChunks.
  void finalizePartialFrame( long[] espc, String[][] domains, byte[] types ) {
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
    Futures fs = new Futures();
    _vecs = new Vec[_keys.length];
    for( int i=0; i<_keys.length; i++ ) {
      // Insert Vec header
      Vec vec = _vecs[i] = new Vec( _keys[i],
                                    Vec.ESPC.rowLayout(_keys[i],espc2),
                                    domains!=null ? domains[i] : null,
                                    types[i]);
      // Here we have to save vectors since
      // saving during unlock will invoke Frame vector
      // refresh
      DKV.put(_keys[i],vec,fs);
    }
    fs.blockForPending();
    unlock(null);
  }

  // --------------------------------------------------------------------------
  static final int MAX_EQ2_COLS = 100000; // Limit of columns user is allowed to request

  /** In support of R, a generic Deep Copy and Slice.
   *
   *  <p>Semantics are a little odd, to match R's.  Each dimension spec can be:<ul>
   *  <li><em>null</em> - all of them
   *  <li><em>a sorted list of negative numbers (no dups)</em> - all BUT these
   *  <li><em>an unordered list of positive</em> - just these, allowing dups
   *  </ul>
   *
   *  <p>The numbering is 1-based; zero's are not allowed in the lists, nor are out-of-range values.
   *  @return the sliced Frame
   */
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
      Vec.Reader v = fr.anyVec().new Reader();
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
        if( j >= cols.length || i < (-(1+cols[j])) ) c2[i-j] = i;
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
    if (numRows() == 0) {
      return new MRTask() {
        @Override public void map(Chunk[] chks, NewChunk[] nchks) { for (NewChunk nc : nchks) nc.addNA(); }
      }.doAll(types(c2), this).outputFrame(names(c2), domains(c2));
    }
    if (orows == null)
      return new DeepSlice(null,c2,vecs()).doAll(types(c2),this).outputFrame(names(c2),domains(c2));
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
                if (er < cs._start || er > (cs._len + cs._start - 1)) continue;
                cs.set((int) (er - cs._start), 1);
              }
            }
          }.doAll(v0).getResult()._fr.anyVec();
          Keyed.remove(v0._key);
          Frame slicedFrame = new DeepSlice(rows, c2, vecs()).doAll(types(c2), this.add("select_vec", v)).outputFrame(names(c2), domains(c2));
          Keyed.remove(v._key);
          Keyed.remove(this.remove(this.numCols() - 1)._key);
          return slicedFrame;
        } else {
          return new DeepSlice(rows.length == 0 ? null : rows, c2, vecs()).doAll(types(c2), this).outputFrame(names(c2), domains(c2));
        }
      }
      // Vec'ize the index array
      Futures fs = new Futures();
      AppendableVec av = new AppendableVec(Vec.newKey(),Vec.T_NUM);
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
      Vec c0 = av.layout_and_close(fs);   // c0 is the row index vec
      fs.blockForPending();
      Frame ff = new Frame(new String[]{"rownames"}, new Vec[]{c0});
      Frame fr2 = new Slice(c2, this).doAll(types(c2),ff).outputFrame(names(c2), domains(c2));
      Keyed.remove(c0._key);
      Keyed.remove(av._key);
      ff.delete();
      return fr2;
    }
    Frame frows = (Frame)orows;
    // It's a compatible Vec; use it as boolean selector.
    // Build column names for the result.
    Vec [] vecs = new Vec[c2.length+1];
    String [] names = new String[c2.length+1];
    for(int i = 0; i < c2.length; ++i){
      vecs[i] = _vecs[c2[i]];
      names[i] = _names[c2[i]];
    }
    vecs[c2.length] = frows.anyVec();
    names[c2.length] = "predicate";
    Frame ff = new Frame(names, vecs);
    return new DeepSelect().doAll(types(c2),ff).outputFrame(names(c2),domains(c2));
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
      long  r    = ix[0].at8(0);
      int   last_ci = anyv.elem2ChunkIdx(r<nrow?r:0); // memoize the last chunk index
      long  last_c0 = anyv.espc()[last_ci];            // ...         last chunk start
      long  last_c1 = anyv.espc()[last_ci + 1];        // ...         last chunk end
      Chunk[] last_cs = new Chunk[vecs.length];       // ...         last chunks
      for (int c = 0; c < _cols.length; c++) {
        vecs[c] = _base.vecs()[_cols[c]];
        last_cs[c] = vecs[c].chunkForChunkIdx(last_ci);
      }
      for (int i = 0; i < ix[0]._len; i++) {
        // select one row
        r = ix[0].at8(i);   // next row to select
        if (r < 0) continue;
        if (r >= nrow) {
          for (int c = 0; c < vecs.length; c++) ncs[c].addNum(Double.NaN);
        } else {
          if (r < last_c0 || r >= last_c1) {
            last_ci = anyv.elem2ChunkIdx(r);
            last_c0 = anyv.espc()[last_ci];
            last_c1 = anyv.espc()[last_ci + 1];
            for (int c = 0; c < vecs.length; c++)
              last_cs[c] = vecs[c].chunkForChunkIdx(last_ci);
          }
          for (int c = 0; c < vecs.length; c++)
            if( vecs[c].isUUID() ) ncs[c].addUUID(last_cs[c], r);
            else if( vecs[c].isString() ) ncs[c].addStr(last_cs[c],r);
            else                   ncs[c].addNum (last_cs[c].at_abs(r));
        }
      }
    }
  }




  // Convert first 100 rows to a 2-d table
  @Override public String toString( ) { return toString(0,20); }

  // Convert len rows starting at off to a 2-d ascii table
  public String toString( long off, int len ) {
    if( off > numRows() ) off = numRows();
    if( off+len > numRows() ) len = (int)(numRows()-off);

    String[] rowHeaders = new String[len+5];
    rowHeaders[0] = "min";
    rowHeaders[1] = "mean";
    rowHeaders[2] = "stddev";
    rowHeaders[3] = "max";
    rowHeaders[4] = "missing";
    for( int i=0; i<len; i++ ) rowHeaders[i+5]=""+(off+i);

    final int ncols = numCols();
    final Vec[] vecs = vecs();
    String[] coltypes = new String[ncols];
    String[][] strCells = new String[len+5][ncols];
    double[][] dblCells = new double[len+5][ncols];
    for( int i=0; i<ncols; i++ ) {
      Vec vec = vecs[i];
      dblCells[0][i] = vec.min();
      dblCells[1][i] = vec.mean();
      dblCells[2][i] = vec.sigma();
      dblCells[3][i] = vec.max();
      dblCells[4][i] = vec.naCnt();
      switch( vec.get_type() ) {
      case Vec.T_BAD:
        coltypes[i] = "string";
        for( int j=0; j<len; j++ ) { strCells[j+5][i] = null; dblCells[j+5][i] = TwoDimTable.emptyDouble; }
        break;
      case Vec.T_STR :
        coltypes[i] = "string"; 
        BufferedString tmpStr = new BufferedString();
        for( int j=0; j<len; j++ ) { strCells[j+5][i] = vec.isNA(off+j) ? "" : vec.atStr(tmpStr,off+j).toString(); dblCells[j+5][i] = TwoDimTable.emptyDouble; }
        break;
      case Vec.T_CAT:
        coltypes[i] = "string"; 
        for( int j=0; j<len; j++ ) { strCells[j+5][i] = vec.isNA(off+j) ? "" : vec.factor(vec.at8(off+j));  dblCells[j+5][i] = TwoDimTable.emptyDouble; }
        break;
      case Vec.T_TIME:
        coltypes[i] = "string";
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        for( int j=0; j<len; j++ ) { strCells[j+5][i] = fmt.print(vec.at8(off+j)); dblCells[j+5][i] = TwoDimTable.emptyDouble; }
        break;
      case Vec.T_NUM:
        coltypes[i] = vec.isInt() ? "long" : "double"; 
        for( int j=0; j<len; j++ ) { dblCells[j+5][i] = vec.isNA(off+j) ? TwoDimTable.emptyDouble : vec.at(off + j); strCells[j+5][i] = null; }
        break;
      case Vec.T_UUID:
        throw H2O.unimpl();
      default:
        System.err.println("bad vector type during debug print: "+vec.get_type());
        throw H2O.fail();
      }
    }
    return new TwoDimTable("Frame "+_key,numRows()+" rows and "+numCols()+" cols",rowHeaders,/* clone the names, the TwoDimTable will replace nulls with ""*/_names.clone(),coltypes,null, "", strCells, dblCells).toString();
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
      long rstart = chks[0]._start;
      int rlen = chks[0]._len;  // Total row count
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
        // For all cols in the new set;
        BufferedString tmpStr = new BufferedString();
        for (int i = 0; i < _cols.length; i++) {
          Chunk oc = chks[_cols[i]];
          NewChunk nc = nchks[i];
          if (_isInt[i] == 1) { // Slice on integer columns
            for (int j = rlo; j < rhi; j++)
              if (oc._vec.isUUID()) nc.addUUID(oc, j);
              else if (oc.isNA(j)) nc.addNA();
              else nc.addNum(oc.at8(j), 0);
          } else if (oc._vec.isString()) {
            for (int j = rlo; j < rhi; j++)
              nc.addStr(oc.atStr(tmpStr, j));
          } else {// Slice on double columns
            for (int j = rlo; j < rhi; j++)
              nc.addNum(oc.atd(j));
          }
        }
        rlo = rhi;
        if (_rows == null) break;
      }
    }
  }

  /**
   * Create a copy of the input Frame and return that copied Frame. All Vecs in this are copied in parallel.
   * Caller mut do the DKV.put
   * @param keyName Key for resulting frame. If null, no key will be given.
   * @return The fresh copy of fr.
   */
  public Frame deepCopy(String keyName) {
    return new MRTask() {
      @Override public void map(Chunk[] cs, NewChunk[] ncs) {
        for(int col=0;col<cs.length;++col)
          for(int row=0;row<cs[0]._len;++row) {
            if( cs[col].isNA(row) ) ncs[col].addNA();
            else if( cs[col] instanceof CStrChunk ) ncs[col].addStr(cs[col], row);
            else if( cs[col] instanceof C16Chunk ) ncs[col].addUUID(cs[col], row);
            else if( !cs[col].hasFloat() ) ncs[col].addNum(cs[col].at8(row), 0);
            else ncs[col].addNum(cs[col].atd(row));
          }
      }
    }.doAll(this.types(),this).outputFrame(keyName==null?null:Key.make(keyName),this.names(),this.domains());
  }

  // _vecs put into kv store already
  private class DoCopyFrame extends MRTask<DoCopyFrame> {
    final Vec[] _vecs;
    DoCopyFrame(Vec[] vecs) {
      _vecs = new Vec[vecs.length];
      int rowLayout = _vecs[0]._rowLayout;
      for(int i=0;i<vecs.length;++i)
        _vecs[i] = new Vec(vecs[i].group().addVec(),rowLayout, vecs[i].domain(), vecs[i]._type);
    }
    @Override public void map(Chunk[] cs) {
      int i=0;
      for(Chunk c: cs) {
        Chunk c2 = (Chunk)c.clone();
        c2._vec=null;
        c2._start=-1;
        c2._cidx=-1;
        c2._mem = c2._mem.clone();
        DKV.put(_vecs[i++].chunkKey(c.cidx()), c2, _fs, true);
      }
    }
    @Override public void postGlobal() { for( Vec _vec : _vecs ) DKV.put(_vec); }
  }

  /**
   *  Last column is a bit vec indicating whether or not to take the row.
   */
  private static class DeepSelect extends MRTask<DeepSelect> {
    @Override public void map( Chunk chks[], NewChunk nchks[] ) {
      Chunk pred = chks[chks.length-1];
      BufferedString tmpStr = new BufferedString();
      for(int i = 0; i < pred._len; ++i) {
        if( pred.atd(i) != 0 && !pred.isNA(i) ) {
          for( int j = 0; j < chks.length - 1; j++ ) {
            Chunk chk = chks[j];
            if( chk.isNA(i) )                   nchks[j].addNA();
            else if( chk instanceof C16Chunk )  nchks[j].addUUID(chk, i);
            else if( chk instanceof CStrChunk)  nchks[j].addStr(chk.atStr(tmpStr, i));
            else if( chk.hasFloat() )           nchks[j].addNum(chk.atd(i));
            else                                nchks[j].addNum(chk.at8(i),0);
          }
        }
      }
    }
  }

  private String[][] domains(int [] cols){
    Vec[] vecs = vecs();
    String[][] res = new String[cols.length][];
    for(int i = 0; i < cols.length; ++i)
      res[i] = vecs[cols[i]].domain();
    return res;
  }

  private String [] names(int [] cols){
    if(_names == null)return null;
    String [] res = new String[cols.length];
    for(int i = 0; i < cols.length; ++i)
      res[i] = _names[cols[i]];
    return res;
  }

  private byte[] types(int [] cols){
    Vec[] vecs = vecs();
    byte[] res = new byte[cols.length];
    for(int i = 0; i < cols.length; ++i)
      res[i] = vecs[cols[i]]._type;
    return res;
  }

  /** Return Frame 'f' if 'f' is compatible with 'this', else return a new
   *  Frame compatible with 'this' and a copy of 'f's data otherwise.  Note
   *  that this can, in the worst case, copy all of {@code this}s' data.
   *  @return This Frame's data in a Frame that is compatible with {@code f}. */
  public Frame makeCompatible( Frame f) {
    // Small data frames are always "compatible"
    if (anyVec() == null)      // Or it is small
      return f;                 // Then must be compatible
    // Same VectorGroup is also compatible
    Vec v1 = anyVec();
    Vec v2 = f.anyVec();
    if(v1.length() != v2.length())
      throw new IllegalArgumentException("Can not make vectors of different length compatible!");
    if (v2 == null || v1.checkCompatible(v2))
      return f;
    // Ok, here make some new Vecs with compatible layout
    Key k = Key.make();
    H2O.submitTask(new RebalanceDataSet(this, f, k)).join();
    Frame f2 = (Frame)k.get();
    DKV.remove(k);
    return f2;
  }

  private boolean isLastRowOfCurrentNonEmptyChunk(int chunkIdx, long row) {
    long[] espc = anyVec().espc();
    long lastRowOfCurrentChunk = espc[chunkIdx + 1] - 1;
    // Assert chunk is non-empty.
    assert espc[chunkIdx + 1] > espc[chunkIdx];
    // Assert row numbering sanity.
    assert row <= lastRowOfCurrentChunk;
    return row >= lastRowOfCurrentChunk;
  }

  /**
   * Flush a chunk if it's not homed here.
   * Do this to avoid filling up memory when streaming a large dataset.
   */
  private void hintFlushRemoteChunk(Vec v, int cidx) {
    Key k = v.chunkKey(cidx);
    if( ! k.home() ) {
      H2O.raw_remove(k);
    }
  }

  /** Convert this Frame to a CSV (in an {@link InputStream}), that optionally
   *  is compatible with R 3.1's recent change to read.csv()'s behavior.
   *  @return An InputStream containing this Frame as a CSV */
  public InputStream toCSV(boolean headers, boolean hex_string) {
    return new CSVStream(headers, hex_string);
  }

  public class CSVStream extends InputStream {
    private final boolean _hex_string;
    byte[] _line;
    int _position;
    public volatile int _curChkIdx;
    long _row;

    CSVStream(boolean headers, boolean hex_string) {
      _curChkIdx=0;
      _hex_string = hex_string;
      StringBuilder sb = new StringBuilder();
      Vec vs[] = vecs();
      if( headers ) {
        sb.append('"').append(_names[0]).append('"');
        for(int i = 1; i < vs.length; i++)
          sb.append(',').append('"').append(_names[i]).append('"');
        sb.append('\n');
      }
      _line = sb.toString().getBytes();
    }

    byte[] getBytesForRow() {
      StringBuilder sb = new StringBuilder();
      Vec vs[] = vecs();
      BufferedString tmpStr = new BufferedString();
      for( int i = 0; i < vs.length; i++ ) {
        if(i > 0) sb.append(',');
        if(!vs[i].isNA(_row)) {
          if( vs[i].isCategorical() ) sb.append('"').append(vs[i].factor(vs[i].at8(_row))).append('"');
          else if( vs[i].isUUID() ) sb.append(PrettyPrint.UUID(vs[i].at16l(_row), vs[i].at16h(_row)));
          else if( vs[i].isInt() ) sb.append(vs[i].at8(_row));
          else if (vs[i].isString()) sb.append('"').append(vs[i].atStr(tmpStr, _row)).append('"');
          else {
            double d = vs[i].at(_row);
            // R 3.1 unfortunately changed the behavior of read.csv().
            // (Really type.convert()).
            //
            // Numeric values with too much precision now trigger a type conversion in R 3.1 into a factor.
            //
            // See these discussions:
            //   https://bugs.r-project.org/bugzilla/show_bug.cgi?id=15751
            //   https://stat.ethz.ch/pipermail/r-devel/2014-April/068778.html
            //   http://stackoverflow.com/questions/23072988/preserve-old-pre-3-1-0-type-convert-behavior
            String s = _hex_string ? Double.toHexString(d) : Double.toString(d);
            sb.append(s);
          }
        }
      }
      sb.append('\n');
      return sb.toString().getBytes();
    }

    @Override public int available() throws IOException {
      // Case 1:  There is more data left to read from the current line.
      if (_position != _line.length) {
        return _line.length - _position;
      }

      // Case 2:  Out of data.
      if (_row == numRows()) {
        return 0;
      }

      // Case 3:  Return data for the current row.
      //          Note this will fast-forward past empty chunks.
      _curChkIdx = anyVec().elem2ChunkIdx(_row);
      _line = getBytesForRow();
      _position = 0;

      // Flush non-empty remote chunk if we're done with it.
      if (isLastRowOfCurrentNonEmptyChunk(_curChkIdx, _row)) {
        for (Vec v : vecs()) {
          hintFlushRemoteChunk(v, _curChkIdx);
        }
      }

      _row++;

      return _line.length;
    }

    @Override public void close() throws IOException {
      super.close();
      _line = null;
    }

    @Override public int read() throws IOException {
      return available() == 0 ? -1 : _line[_position++];
    }

    @Override public int read(byte[] b, int off, int len) throws IOException {
      int n = available();
      if(n > 0) {
        n = Math.min(n, len);
        System.arraycopy(_line, _position, b, off, n);
        _position += n;
      }
      return n;
    }
  }

}
