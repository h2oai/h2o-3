package water.fvec;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import water.*;
import water.api.FramesHandler;
import water.api.schemas3.KeyV3;
import water.exceptions.H2OIllegalArgumentException;
import water.parser.BufferedString;
import water.rapids.Merge;
import water.util.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

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

  /**
   * Given a temp Frame and a base Frame from which it was created, delete the
   * Vecs that aren't found in the base Frame and then delete the temp Frame.
   *
   * TODO: Really should use Scope but Scope does not.
   */
  public static void deleteTempFrameAndItsNonSharedVecs(Frame tempFrame, Frame baseFrame) {
    Key[] keys = tempFrame.keys();
    for( int i=0; i<keys.length; i++ )
      if( baseFrame.find(keys[i]) == -1 ) //only delete vecs that aren't shared
        keys[i].remove();
    DKV.remove(tempFrame._key); //delete the frame header
  }

  /**
   * Fetch all Frames from the KV store.
   */
  public static Frame[] fetchAll() {
    // Get all the frames.
    final Key[] frameKeys = KeySnapshot.globalKeysOfClass(Frame.class);
    List<Frame> frames = new ArrayList<>(frameKeys.length);
    for( Key key : frameKeys ) {
      Frame frame = FramesHandler.getFromDKV("(none)", key);
      // Weed out frames with vecs that are no longer in DKV
      boolean skip = false;
      for( Vec vec : frame.vecs() ) {
        if (vec == null || DKV.get(vec._key) == null) {
          Log.warn("Leaked frame: Frame "+frame._key+" points to one or more deleted vecs.");
          skip = true;
          break;
        }
      }
      if (!skip) frames.add(frame);
    }
    return frames.toArray(new Frame[frames.size()]);
  }

  public boolean hasNAs(){
    for(Vec v:bulkRollups())
      if(v.naCnt() > 0) return true;
    return false;
  }

  public boolean hasInfs() {
    // return if frame contains positive infinity
    for (Vec v : bulkRollups())
      if (v.pinfs() > 0 || v.ninfs() > 0) return true;
    return false;
  }
  private long _naCnt = -1;
  synchronized public long naCount() {
    if (_naCnt !=- 1) return _naCnt;
    _naCnt = 0;
    for(Vec v: vecs()) _naCnt += v.naCnt();
    return _naCnt;
  }

  public double naFraction() {
    return naCount() / (numCols() * numRows());
  }

  /** Creates an internal frame composed of the given Vecs and default names.  The frame has no key. */
  public Frame(Vec... vecs){
    this(null, vecs);
  }

  /** Creates an internal frame composed of the given Vecs and names.  The frame has no key. */
  public Frame(String names[], Vec vecs[]) {
    this(null, names, vecs);
  }

  /** Creates an empty frame with given key. */
  public Frame(Key<Frame> key) {
    this(key, null, new Vec[0]);
  }

  /**
   * Special constructor for data with unnamed columns (e.g. svmlight) bypassing *all* checks.
   */
  public Frame(Key<Frame> key, Vec vecs[], boolean noChecks) {
    super(key);
    assert noChecks;
    _vecs = vecs;
    String[] names = new String[vecs.length];
    _keys = makeVecKeys(vecs.length);
    for (int i = 0; i < vecs.length; i++) {
      names[i] = defaultColName(i);
      _keys[i] = vecs[i]._key;
    }
    
    setNames(names);
  }

  /** Creates a frame with given key, names and vectors. */
  public Frame(Key<Frame> key, String names[], Vec vecs[] ) {
    super(key);

    // Require all Vecs already be installed in the K/V store
    for( Vec vec : vecs ) DKV.prefetch(vec._key);
    for( Vec vec : vecs ) {
      assert DKV.get(vec._key) != null : " null vec: "+vec._key;
    }

    // Always require names
    if( names==null ) {         // Make default names, all known to be unique
      setNames(new String[vecs.length]);
      _keys = makeVecKeys(vecs.length);
      _vecs  = vecs;
      for( int i=0; i<vecs.length; i++ ) _names[i] = defaultColName(i);
      for( int i=0; i<vecs.length; i++ ) _keys [i] = vecs[i]._key;
      for( int i=0; i<vecs.length; i++ ) checkCompatibility(_names[i],vecs[i]);
      _lastNameBig = true;
    } else {
      // Make empty to dodge asserts, then "add()" them all which will check
      // for compatible Vecs & names.
      _names = new String[0];
      _keys = makeVecKeys(0);
      _vecs  = new Vec   [0];
      add(names,vecs);
    }
    assert _names.length == vecs.length;
  }

  void setNamesNoCheck(String[] columns){
    _names = columns;
  }

  public final void setNames(String[] columns){
    if (_vecs != null && columns.length != _vecs.length) {
      throw new IllegalArgumentException("Number of column names=" + columns.length + " must be the number of vecs=" + _vecs.length);
    }
    _names = columns;
  }
  /** Deep copy of Vecs and Keys and Names (but not data!) to a new random Key.
   *  The resulting Frame does not share with the original, so the set of Vecs
   *  can be freely hacked without disturbing the original Frame. */
  public Frame( Frame fr ) {
    super( Key.<Frame>make() );
    setNames(fr._names.clone());
    _keys = fr._keys .clone();
    _vecs = fr.vecs().clone();
    _lastNameBig = fr._lastNameBig;
  }

  /** Default column name maker */
  public static String defaultColName( int col ) { return "C"+(1+col); }

  /**
   * Helper method to initialize `_keys` array (which requires an unchecked cast).
   * @param size number of elements in the array that will be created.
   */
  @SuppressWarnings("unchecked")
  private Key<Vec>[] makeVecKeys(int size) {
    return new Key[size];
  }

  // Make unique names.  Efficient for the special case of appending endless
  // versions of "C123" style names where the next name is +1 over the prior
  // name.  All other names take the O(n^2) lookup.
  private int pint( String name ) {
    try { return Integer.valueOf(name.substring(1)); }
    catch(NumberFormatException ignored) { }
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
  private void checkCompatibility(String name, Vec vec ) {
    if( vec instanceof AppendableVec ) return; // New Vectors are endlessly compatible
    Vec v0 = anyVec();
    if( v0 == null ) return; // No fixed-size Vecs in the Frame
    // Vector group has to be the same, or else the layout has to be the same,
    // or else the total length has to be small.
    if( !v0.isCompatibleWith(vec) ) {
      if(!Vec.VectorGroup.sameGroup(v0,vec))
        Log.err("Unexpected incompatible vector group, " + v0.group() + " != " + vec.group());
      if(!Arrays.equals(v0.espc(), vec.espc()))
        Log.err("Unexpected incompatible espc, " + Arrays.toString(v0.espc()) + " != " + Arrays.toString(vec.espc()));
      throw new IllegalArgumentException("Vec " + name + " is not compatible with the rest of the frame");
    }
  }

  /** Frames are compatible if they have the same layout (number of rows and chunking) and the same vector group (chunk placement).. */
  public boolean isCompatible( Frame fr ) {
    if( numRows() != fr.numRows() ) return false;
    for( int i=0; i<vecs().length; i++ )
      if( !vecs()[i].isCompatibleWith(fr.vecs()[i]) )
        return false;
    return true;
  }

  /** Number of columns
   *  @return Number of columns */
  public int  numCols() { return _keys == null? 0 : _keys.length; }
  /** Number of rows
   *  @return Number of rows */
  public long numRows() { Vec v = anyVec(); return v==null ? 0 : v.length(); }

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
  public String name(int i) {
    return _names[i];
  }

  /** The array of keys.
   * @return the array of keys for each vec in the frame.
   */
  public Key<Vec>[] keys() { return _keys; }
  public Iterable<Key<Vec>> keysList() { return Arrays.asList(_keys); }

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
    // TODO: add a hashtable: O(n) is just stupid.
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
    Key<Vec>[] keys = makeVecKeys(_keys.length + 1);
    System.arraycopy(_names,0,names,0,i);
    System.arraycopy(_vecs,0,vecs,0,i);
    System.arraycopy(_keys,0,keys,0,i);
    names[i] = name;
    vecs[i] = vec;
    keys[i] = vec._key;
    System.arraycopy(_names,i,names,i+1,_names.length-i);
    System.arraycopy(_vecs,i,vecs,i+1,_vecs.length-i);
    System.arraycopy(_keys,i,keys,i+1,_keys.length-i);
    _vecs = vecs;
    setNames(names);
    _keys = keys;
  }

  /** Pair of (column name, Frame key). */
  public static class VecSpecifier extends Iced implements Vec.Holder {
    public Key<Frame> _frame;
    public String _column_name;


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
    for( int i=0; i<vecs.length; i++ ) {
        ds[i] = vecs[i].domain();
    }
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

  public Vec[] bulkRollups() {
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
    try {
      Vec[] vecs = bulkRollups();
      long sum = 0;
      for (Vec vec : vecs) sum += vec.byteSize();
      return sum;
    } catch(RuntimeException ex) {
      Log.debug("Failure to obtain byteSize() - missing chunks?");
      return -1;
    }
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
      vecs[i] = vecs[i] != null ? makeCompatible(new Frame(vecs[i]))[0] : null;
      checkCompatibility(tmpnames[i]=uniquify(tmpnames[i]),vecs[i]);  // Throw IAE is mismatch
    }

    int ncols = _keys.length;

    // make temp arrays and don't assign them back until they are fully filled - otherwise vecs() can cache null's and NPE.
    String[] tmpnam = Arrays.copyOf(_names, ncols+N);
    Key<Vec>[] tmpkeys = Arrays.copyOf(_keys, ncols+N);
    Vec[] tmpvecs = Arrays.copyOf(_vecs, ncols+N);
    for (int i=0; i<N; ++i) {
      tmpnam[ncols+i] = tmpnames[i];
      tmpkeys[ncols+i] = vecs[i]._key;
      tmpvecs[ncols+i] = vecs[i];
    }
    _keys = tmpkeys;
    _vecs = tmpvecs;
    setNames(tmpnam);
  }

  /** Append a named Vec to the Frame.  Names are forced unique, by appending a
   *  unique number if needed.
   *  @return the added Vec, for flow-coding */
  public Vec add( String name, Vec vec ) {
    vec = makeCompatible(new Frame(vec))[0];
    checkCompatibility(name=uniquify(name),vec);  // Throw IAE is mismatch
    int ncols = _keys.length;
    String[] names = Arrays.copyOf(_names,ncols+1);  names[ncols] = name;
    Key<Vec>[] keys = Arrays.copyOf(_keys ,ncols+1);  keys [ncols] = vec._key;
    Vec[] vecs  = Arrays.copyOf(_vecs ,ncols+1);  vecs [ncols] = vec;
    _keys = keys;
    _vecs = vecs;
    setNames(names);
    return vec;
  }

  /** Append a Frame onto this Frame.  Names are forced unique, by appending
   *  unique numbers if needed.
   *  @return the expanded Frame, for flow-coding */
  public Frame add( Frame fr ) { add(fr._names,fr.vecs().clone(),fr.numCols()); return this; }

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
    String[] _names2 = new String[len + 1];
    Vec[] _vecs2 = new Vec[len + 1];
    Key<Vec>[] _keys2 = makeVecKeys(len + 1);
    _names2[0] = name;
    _vecs2 [0] = vec;
    _keys2 [0] = vec._key;
    if (_names != null) {
      System.arraycopy(_names, 0, _names2, 1, len);
      System.arraycopy(_vecs,  0, _vecs2,  1, len);
      System.arraycopy(_keys,  0, _keys2,  1, len);
    }
    _vecs  = _vecs2;
    _keys  = _keys2;
    setNames(_names2);
    return this;
  }

  /** Swap two Vecs in-place; useful for sorting columns by some criteria */
  public void swap( int lo, int hi ) {
    assert 0 <= lo && lo < _keys.length;
    assert 0 <= hi && hi < _keys.length;
    if( lo==hi ) return;
    Vec vecs[] = vecs();
    Vec v   = vecs [lo]; vecs  [lo] = vecs  [hi]; vecs  [hi] = v;
    Key<Vec> k = _keys[lo]; _keys[lo] = _keys[hi]; _keys[hi] = k;
    String n=_names[lo]; _names[lo] = _names[hi]; _names[hi] = n;
  }

  /**
   * Re-order the columns according to the new order specified in newOrder.
   *
   * @param newOrder
   */
  public void reOrder(int[] newOrder) {
    assert newOrder.length==_keys.length; // make sure column length match
    int numCols = _keys.length;
    Vec tmpvecs[] = vecs().clone();
    Key<Vec> tmpkeys[] = _keys.clone();
    String tmpnames[] = _names.clone();

    for (int colIndex = 0; colIndex < numCols; colIndex++) {
        tmpvecs[colIndex] = _vecs[newOrder[colIndex]];
        tmpkeys[colIndex] = _keys[newOrder[colIndex]];
        tmpnames[colIndex] = _names[newOrder[colIndex]];
    }
    // copy it back
    for (int colIndex = 0; colIndex < numCols; colIndex++) {
      _vecs[colIndex] = tmpvecs[colIndex];
      _keys[colIndex] = tmpkeys[colIndex];
      _names[colIndex] = tmpnames[colIndex];
    }
  }

  /** move the provided columns to be first, in-place. For Merge currently since method='hash' was coded like that */
  public void moveFirst( int cols[] ) {
    boolean colsMoved[] = new boolean[_keys.length];
    Vec tmpvecs[] = vecs().clone();
    Key<Vec> tmpkeys[] = _keys.clone();
    String tmpnames[] = _names.clone();

    // Move the desired ones first
    for (int i=0; i<cols.length; i++) {
      int w = cols[i];
      if (colsMoved[w]) throw new IllegalArgumentException("Duplicates in column numbers passed in");
      if (w<0 || w>=_keys.length) throw new IllegalArgumentException("column number out of 0-based range");
      colsMoved[w] = true;
      tmpvecs[i] = _vecs[w];
      tmpkeys[i] = _keys[w];
      tmpnames[i] = _names[w];
    }

    // Put the other ones afterwards
    int w = cols.length;
    for (int i=0; i<_keys.length; i++) {
      if (!colsMoved[i]) {
        tmpvecs[w] = _vecs[i];
        tmpkeys[w] = _keys[i];
        tmpnames[w] = _names[i];
        w++;
      }
    }

    // Copy back over the original in-place
    for (int i=0; i<_keys.length; i++) {
      _vecs[i] = tmpvecs[i];
      _keys[i] = tmpkeys[i];
      _names[i] = tmpnames[i];
    }
  }

  /** Returns a subframe of this frame containing only vectors with desired names.
   *
   *  @param names list of vector names
   *  @return a new frame which collects vectors from this frame with desired names.
   *  @throws IllegalArgumentException if there is no vector with desired name in this frame.
   */
  public Frame subframe(String[] names) { return subframe(names, false, 0)[0]; }

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
        cnames[ccv] = names[i];
        vecs[i] = cvecs[ccv++] = anyVec().makeCon(c);
      }
    return new Frame[] {
      new Frame(Key.<Frame>make("subframe" + Key.make().toString()), names, vecs),
      ccv > 0? new Frame(Key.<Frame>make("subframe" + Key.make().toString()), Arrays.copyOf(cnames, ccv), Arrays.copyOf(cvecs,ccv)) : null
    };
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
  @Override protected Futures remove_impl(Futures fs) {
    final Key[] keys = _keys;
    if( keys.length==0 ) return fs;

    // Get the nChunks without calling anyVec - which loads all Vecs eagerly,
    // only to delete them.  Supports Frames with some Vecs already deleted, as
    // a Scope cleanup action might delete Vecs out of order.
    Vec v = _col0;
    if (v == null) {
      Vec[] vecs = _vecs;       // Read once, in case racily being cleared
      if (vecs != null)
        for (Vec vec : vecs)
          if ((v = vec) != null) // Stop on finding the 1st Vec
            break;
    }
    if (v == null)             // Ok, now do DKV gets
      for (Key<Vec> _key1 : _keys)
        if ((v = _key1.get()) != null)
          break;                // Stop on finding the 1st Vec
    if (v == null)
      return fs;

    _vecs = new Vec[0];
    setNames(new String[0]);
    _keys = makeVecKeys(0);

    // Bulk dumb local remove - no JMM, no ordering, no safety.
    Vec.bulk_remove(keys, v.nChunks());

    return fs;
  }

  /** Write out K/V pairs, in this case Vecs. */
  @Override protected AutoBuffer writeAll_impl(AutoBuffer ab) {
    for( Key k : _keys )
      ab.putKey(k);
    return super.writeAll_impl(ab);
  }
  @Override protected Keyed readAll_impl(AutoBuffer ab, Futures fs) {
    for( Key k : _keys )
      ab.getKey(k,fs);
    return super.readAll_impl(ab,fs);
  }

  /** Replace one column with another. Caller must perform global update (DKV.put) on
   *  this updated frame.
   *  @return The old column, for flow-coding */
  public Vec replace(int col, Vec nv) {
    Vec rv = vecs()[col];
    nv = ((new Frame(rv)).makeCompatible(new Frame(nv)))[0];
    DKV.put(nv);
    assert DKV.get(nv._key)!=null; // Already in DKV
    assert rv.isCompatibleWith(nv);
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
    Key<Vec>[] keys = makeVecKeys(rem.length);
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
    setNames(names);
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
    if( v == _col0 ) _col0 = null;
    _vecs = ArrayUtils.remove(_vecs, idx);
    setNames(ArrayUtils.remove(_names, idx));
    _keys = ArrayUtils.remove(_keys, idx);
    return v;
  }

  /**
   * Remove all the vecs from frame.
   */
  public Vec[] removeAll() {
    return remove(0, _names.length);
  }

  /** Remove given interval of columns from frame.  Motivated by R intervals.
   *  @param startIdx - start index of column (inclusive)
   *  @param endIdx - end index of column (exclusive)
   *  @return array of removed columns  */
  Vec[] remove(int startIdx, int endIdx) {
    int len = _names.length;
    int nlen = len - (endIdx-startIdx);
    String[] names = new String[nlen];
    Key<Vec>[] keys = makeVecKeys(nlen);
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
    _vecs = vecs;
    _keys = keys;
    setNames(names);
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
    _keys  = makeVecKeys(0);
    _vecs  = new Vec   [0];
    setNames(new String[0]);
    add(names,vecs,cols);
  }

  // --------------------------------------------
  // Utilities to help external Frame constructors, e.g. Spark.

  // Make an initial Frame & lock it for writing.  Build Vec Keys.
  void preparePartialFrame( String[] names ) {
    // Nuke any prior frame (including freeing storage) & lock this one
    if( _keys != null ) delete_and_lock();
    else write_lock();
    _keys = new Vec.VectorGroup().addVecs(names.length);
    setNamesNoCheck(names);
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
    while( nchunk > 1 && espc[nchunk-1] == 0 )
      nchunk--;
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
      // Nuke the extra chunks
      for (int j = nchunk; j < espc.length; j++)
        DKV.remove(Vec.chunkKey(_keys[i], j), fs);
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
    unlock();
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
    Vec [] vecs = new Vec[c2.length];
    String [] names = new String[c2.length];
    for(int i = 0; i < c2.length; ++i){
      vecs[i] = _vecs[c2[i]];
      names[i] = _names[c2[i]];
    }
    Frame ff = new Frame(names, vecs);
    ff.add("predicate", frows.anyVec());
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
          for (int c = 0; c < vecs.length; c++) ncs[c].addNA();
        } else {
          if (r < last_c0 || r >= last_c1) {
            last_ci = anyv.elem2ChunkIdx(r);
            last_c0 = anyv.espc()[last_ci];
            last_c1 = anyv.espc()[last_ci + 1];
            for (int c = 0; c < vecs.length; c++)
              last_cs[c] = vecs[c].chunkForChunkIdx(last_ci);
          }
          int ir = (int)(r - last_cs[0].start());
          for (int c = 0; c < vecs.length; c++)
            last_cs[c].extractRows(ncs[c],ir);
        }
      }
    }
  }

  // Convert len rows starting at off to a 2-d ascii table
  @Override public String toString( ) {
    return ("Frame key: " + _key + "\n") +
            "   cols: " + numCols() + "\n" +
            "   rows: " + numRows() + "\n" +
            " chunks: " + (anyVec() == null ? "N/A" : anyVec().nChunks()) + "\n" +
            "   size: " + byteSize() + "\n";
  }

  public String toString(long off, int len) { return toTwoDimTable(off, len).toString(); }
  public String toString(long off, int len, boolean rollups) { return toTwoDimTable(off, len, rollups).toString(); }
  public TwoDimTable toTwoDimTable() { return toTwoDimTable(0,10); }
  public TwoDimTable toTwoDimTable(long off, int len ) { return toTwoDimTable(off,len,true); }
  public TwoDimTable toTwoDimTable(long off, int len, boolean rollups ) {
    if( off > numRows() ) off = numRows();
    if( off+len > numRows() ) len = (int)(numRows()-off);

    String[] rowHeaders = new String[len];
    int H=0;
    if( rollups ) {
      H = 5;
      rowHeaders = new String[len+H];
      rowHeaders[0] = "min";
      rowHeaders[1] = "mean";
      rowHeaders[2] = "stddev";
      rowHeaders[3] = "max";
      rowHeaders[4] = "missing";
      for( int i=0; i<len; i++ ) rowHeaders[i+H]=""+(off+i);
    }

    final int ncols = numCols();
    final Vec[] vecs = vecs();
    String[] coltypes = new String[ncols];
    String[][] strCells = new String[len+H][ncols];
    double[][] dblCells = new double[len+H][ncols];
    final BufferedString tmpStr = new BufferedString();
    for( int i=0; i<ncols; i++ ) {
      if( DKV.get(_keys[i]) == null ) { // deleted Vec in Frame
        coltypes[i] = "string";
        for( int j=0; j<len+H; j++ ) dblCells[j][i] = TwoDimTable.emptyDouble;
        for( int j=0; j<len; j++ ) strCells[j+H][i] = "NO_VEC";
        continue;
      }
      Vec vec = vecs[i];
      if( rollups ) {
        dblCells[0][i] = vec.min();
        dblCells[1][i] = vec.mean();
        dblCells[2][i] = vec.sigma();
        dblCells[3][i] = vec.max();
        dblCells[4][i] = vec.naCnt();
      }
      switch( vec.get_type() ) {
      case Vec.T_BAD:
        coltypes[i] = "string";
        for( int j=0; j<len; j++ ) { strCells[j+H][i] = null; dblCells[j+H][i] = TwoDimTable.emptyDouble; }
        break;
      case Vec.T_STR :
        coltypes[i] = "string";
        for( int j=0; j<len; j++ ) { strCells[j+H][i] = vec.isNA(off+j) ? "" : vec.atStr(tmpStr,off+j).toString(); dblCells[j+H][i] = TwoDimTable.emptyDouble; }
        break;
      case Vec.T_CAT:
        coltypes[i] = "string";
        for( int j=0; j<len; j++ ) { strCells[j+H][i] = vec.isNA(off+j) ? "" : vec.factor(vec.at8(off+j));  dblCells[j+H][i] = TwoDimTable.emptyDouble; }
        break;
      case Vec.T_TIME:
        coltypes[i] = "string";
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        for( int j=0; j<len; j++ ) { strCells[j+H][i] = vec.isNA(off+j) ? "" : fmt.print(vec.at8(off+j)); dblCells[j+H][i] = TwoDimTable.emptyDouble; }
        break;
      case Vec.T_NUM:
        coltypes[i] = vec.isInt() ? "long" : "double";
        for( int j=0; j<len; j++ ) { dblCells[j+H][i] = vec.isNA(off+j) ? TwoDimTable.emptyDouble : vec.at(off + j); strCells[j+H][i] = null; }
        break;
      case Vec.T_UUID:
        throw H2O.unimpl();
      default:
        System.err.println("bad vector type during debug print: "+vec.get_type());
        throw H2O.fail();
      }
    }
    return new TwoDimTable("Frame "+_key,numRows()+" rows and "+numCols()+" cols",rowHeaders,/* clone the names, the TwoDimTable will replace nulls with ""*/_names.clone(),coltypes,null, "", strCells, dblCells);
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
        for (int i = 0; i < _cols.length; i++)
          chks[_cols[i]].extractRows(nchks[i], rlo,rhi);
        rlo = rhi;
        if (_rows == null) break;
      }
    }
  }

  /**
   * Create a copy of the input Frame and return that copied Frame. All Vecs in this are copied in parallel.
   * Caller must do the DKV.put
   * @param keyName Key for resulting frame. If null, no key will be given.
   * @return The fresh copy of fr.
   */
  public Frame deepCopy(String keyName) {
    final Vec [] vecs = vecs().clone();
    Key [] ks = anyVec().group().addVecs(vecs.length);
    Futures fs = new Futures();
    for(int i = 0; i < vecs.length; ++i)
      DKV.put(vecs[i] = new Vec(ks[i], anyVec()._rowLayout, vecs[i].domain(),vecs()[i]._type),fs);
    new MRTask() {
      @Override public void map(Chunk[] cs) {
        int cidx = cs[0].cidx();
        for(int i = 0; i < cs.length; ++i)
          DKV.put(vecs[i].chunkKey(cidx),cs[i].deepCopy(),_fs);
      }
    }.doAll(this);//.outputFrame(keyName==null?null:Key.make(keyName),this.names(),this.domains());
    fs.blockForPending();
    return new Frame((keyName==null?null:Key.<Frame>make(keyName)),this.names(),vecs);
  }



  /**
   *  Last column is a bit vec indicating whether or not to take the row.
   */
  public static class DeepSelect extends MRTask<DeepSelect> {
    @Override public void map( Chunk[] chks, NewChunk [] nchks ) {
      Chunk pred =  chks[chks.length - 1];
      int[] ids = pred.getIntegers(new int[pred._len],0,pred._len,0);
      int zeros = 0;
      for(int i = 0; i < ids.length; ++i)
        if(ids[i] == 1){
          ids[i-zeros] = i;
        } else zeros++;
      ids = Arrays.copyOf(ids,ids.length-zeros);
      for (int c = 0; c < chks.length-1; ++c)
        chks[c].extractRows(nchks[c], ids);
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

  public Vec[] makeCompatible( Frame f) {return makeCompatible(f,false);}
  /** Return array of Vectors if 'f' is compatible with 'this', else return a new
   *  array of Vectors compatible with 'this' and a copy of 'f's data otherwise.  Note
   *  that this can, in the worst case, copy all of {@code this}s' data.
   *  @return This Frame's data in an array of Vectors that is compatible with {@code f}. */
  public Vec[] makeCompatible( Frame f, boolean force) {
    // Small data frames are always "compatible"
    if (anyVec() == null)      // Or it is small
      return f.vecs();                 // Then must be compatible
    Vec v1 = anyVec();
    Vec v2 = f.anyVec();
    if (v1 != null && v2 != null && v1.length() != v2.length())
      throw new IllegalArgumentException("Can not make vectors of different length compatible!");
    if (v1 == null || v2 == null || (!force && v1.isCompatibleWith(v2)))
      return f.vecs();
    // Ok, here make some new Vecs with compatible layout
    Key k = Key.make();
    H2O.submitTask(new RebalanceDataSet(this, f, k)).join();
    Frame f2 = (Frame)k.get();
    DKV.remove(k);
    for (Vec v : f2.vecs()) Scope.track(v);
    return f2.vecs();
  }

  public static Job export(Frame fr, String path, String frameName, boolean overwrite, int nParts) {
    boolean forceSingle = nParts == 1;
    // Validate input
    if (forceSingle) {
      boolean fileExists = H2O.getPM().exists(path);
      if (overwrite && fileExists) {
        Log.warn("File " + path + " exists, but will be overwritten!");
      } else if (!overwrite && fileExists) {
        throw new H2OIllegalArgumentException(path, "exportFrame", "File " + path + " already exists!");
      }
    } else {
      if (! H2O.getPM().isEmptyDirectoryAllNodes(path)) {
        throw new H2OIllegalArgumentException(path, "exportFrame", "Cannot use path " + path +
                " to store part files! The target needs to be either an existing empty directory or not exist yet.");
      }
    }
    Job job =  new Job<>(fr._key, "water.fvec.Frame", "Export dataset");
    FrameUtils.ExportTaskDriver t = new FrameUtils.ExportTaskDriver(fr, path, frameName, overwrite, job, nParts);
    return job.start(t, fr.anyVec().nChunks());
  }

  /** Convert this Frame to a CSV (in an {@link InputStream}), that optionally
   *  is compatible with R 3.1's recent change to read.csv()'s behavior.
   *
   *  WARNING: Note that the end of a file is denoted by the read function
   *  returning 0 instead of -1.
   *
   *  @return An InputStream containing this Frame as a CSV */
  public InputStream toCSV(boolean headers, boolean hex_string) {
    return new CSVStream(this, headers, hex_string);
  }

  public static class CSVStream extends InputStream {
    private final boolean _hex_string;
    byte[] _line;
    int _position;
    int _chkRow;
    Chunk[] _curChks;
    int _lastChkIdx;
    public volatile int _curChkIdx; // used only for progress reporting

    public CSVStream(Frame fr, boolean headers, boolean hex_string) {
      this(firstChunks(fr), headers ? fr.names() : null, fr.anyVec().nChunks(), hex_string);
    }

    private static Chunk[] firstChunks(Frame fr) {
      Vec anyvec = fr.anyVec();
      if (anyvec == null || anyvec.nChunks() == 0 || anyvec.length() == 0) {
        return null;
      }
      Chunk[] chks = new Chunk[fr.vecs().length];
      for (int i = 0; i < fr.vecs().length; i++) {
        chks[i] = fr.vec(i).chunkForRow(0);
      }
      return chks;
    }

    public CSVStream(Chunk[] chks, String[] names, int nChunks, boolean hex_string) {
      if (chks == null) nChunks = 0;
      _lastChkIdx = (chks != null) ? chks[0].cidx() + nChunks - 1 : -1;
      _hex_string = hex_string;
      StringBuilder sb = new StringBuilder();
      if (names != null) {
        sb.append('"').append(names[0]).append('"');
        for(int i = 1; i < names.length; i++)
          sb.append(',').append('"').append(names[i]).append('"');
        sb.append('\n');
      }
      _line = StringUtils.bytesOf(sb);
      _chkRow = -1; // first process the header line
      _curChks = chks;
    }

    public int getCurrentRowSize() throws IOException {
      int av = available();
      assert av > 0;
      return _line.length;
    }

    byte[] getBytesForRow() {
      StringBuilder sb = new StringBuilder();
      BufferedString tmpStr = new BufferedString();
      for (int i = 0; i < _curChks.length; i++ ) {
        Vec v = _curChks[i]._vec;
        if(i > 0) sb.append(',');
        if(!_curChks[i].isNA(_chkRow)) {
          if( v.isCategorical() ) sb.append('"').append(v.factor(_curChks[i].at8(_chkRow))).append('"');
          else if( v.isUUID() ) sb.append(PrettyPrint.UUID(_curChks[i].at16l(_chkRow), _curChks[i].at16h(_chkRow)));
          else if( v.isInt() ) sb.append(_curChks[i].at8(_chkRow));
          else if (v.isString()) sb.append('"').append(_curChks[i].atStr(tmpStr, _chkRow)).append('"');
          else {
            double d = _curChks[i].atd(_chkRow);
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
      return StringUtils.bytesOf(sb);
    }

    @Override public int available() throws IOException {
      // Case 1:  There is more data left to read from the current line.
      if (_position != _line.length) {
        return _line.length - _position;
      }

      // Case 2:  There are no chunks to work with (eg. the whole Frame was empty).
      if (_curChks == null) {
        return 0;
      }

      _chkRow++;
      Chunk anyChunk = _curChks[0];

      // Case 3:  Out of data.
      if (anyChunk._start + _chkRow == anyChunk._vec.length()) {
        return 0;
      }

      // Case 4:  Out of data in the current chunks => fast-forward to the next set of non-empty chunks.
      if (_chkRow == anyChunk.len()) {
        _curChkIdx = anyChunk._vec.elem2ChunkIdx(anyChunk._start + _chkRow); // skips empty chunks
        // Case 4:  Processed all requested chunks.
        if (_curChkIdx > _lastChkIdx) {
          return 0;
        }
        // fetch the next non-empty chunks
        Chunk[] newChks = new Chunk[_curChks.length];
        for (int i = 0; i < _curChks.length; i++) {
          newChks[i] = _curChks[i]._vec.chunkForChunkIdx(_curChkIdx);
          // flush the remote chunk
          Key oldKey = _curChks[i]._vec.chunkKey(_curChks[i]._cidx);
          if (! oldKey.home()) {
            H2O.raw_remove(oldKey);
          }
        }
        _curChks = newChks;
        _chkRow = 0;
      }

      // Case 5:  Return data for the current row.
      _line = getBytesForRow();
      _position = 0;

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

  @Override public Class<KeyV3.FrameKeyV3> makeSchema() { return KeyV3.FrameKeyV3.class; }

  /** Sort rows of a frame, using the set of columns as keys.  User can specify sorting direction for each sorting
   * column in a boolean array.  For example, if we want to sort columns 0, 1, 2 and want to sort 0 in ascending
   * direction, 1 and 2 in descending direction for frame fr, the call to make is fr.sort(new int[2]{0,1,2},
   * new boolean[2]{true, false, false}.
   *
   *  @return Copy of frame, sorted */
  public Frame sort( int[] cols ) {
    return Merge.sort(this,cols);
  }

  public Frame sort(int[] cols, int[] ascending) {
    return Merge.sort(this, cols, ascending);
  }

  /**
   * A structure for fast lookup in the set of frame's vectors.
   * Purpose of this class is to avoid multiple O(n) searches in {@link Frame}'s vectors.
   *
   * @return An instance of {@link FrameVecRegistry}
   */
  public FrameVecRegistry frameVecRegistry() {
    return new FrameVecRegistry();
  }

  public class FrameVecRegistry {
    private LinkedHashMap<String, Vec> vecMap;

    private FrameVecRegistry() {
      vecMap = new LinkedHashMap<>(_vecs.length);
      for (int i = 0; i < _vecs.length; i++) {
        vecMap.put(_names[i], _vecs[i]);
      }
    }

    /**
     * Finds a Vec by column name
     *
     * @param colName Column name to search for, case-sensitive
     * @return An instance of {@link Vec}, if found. Otherwise null.
     */
    public Vec findByColName(final String colName) {
      return vecMap.get(colName);
    }
  }
}
