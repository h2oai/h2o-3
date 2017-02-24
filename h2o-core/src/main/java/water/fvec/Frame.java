package water.fvec;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import water.*;
import water.api.schemas3.KeyV3;
import water.exceptions.H2OIllegalArgumentException;
import water.parser.BufferedString;
import water.rapids.Merge;
import water.util.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/** A collection of named {@link Vec}s, essentially an R-like Distributed Data Frame.
 *
 *  <p>Frames represent a large distributed 2-D table with named columns
 *  ({@link Vec}s) and numbered rows.  A reasonable <em>column</em> limit is
 *  100K columns, but there's no hard-coded limit.  There's no real <em>row</em>
 *  limit except memory; Frames (and Vecs) with many billions of rows are used
 *  routinely.
 *
 *  <p>A Frame is a collection of named Vecs; a Vec is a collection of numbered
 *  {@link ByteArraySupportedChunk}s.  A Frame is small, cheaply and easily manipulated, it is
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
 *  then enforces {@link ByteArraySupportedChunk} row alignment across Vecs (or at least enforces
 *  a low-cost access model).  Parallel and distributed execution touching all
 *  the data in a Frame relies on this alignment to get good performance.
 *
 *  <p>Example: Make a Frame from a CSV file:<pre>
 *  File file = ...
 *  NFSFileVec nfs = NFSFileVec.make(file); // NFS-backed Vec, lazily read on demand
 *  Frame fr = water.parser.ParseDataset.parse(Key.make("myKey"),nfs._key);
 *  </pre>
 *
 *  <p>Example: Find and removeVecs the Vec called "unique_id" from the Frame,
 *  since modeling with a unique_id can lead to overfitting:
 *  <pre>
 *  Vec uid = fr.removeVecs("unique_id");
 *  </pre>
 *
 *  <p>Example: Move the response column to the last position:
 *  <pre>
 *  fr.add("response",fr.removeVecs("response"));
 *  </pre>
 *
 */
public class Frame extends Lockable<Frame> {
  /** Vec names */
  public String[] _names;
  private int [] _sorted_ids;

  protected int [] nameSortedIds(){
    if(_sorted_ids != null)
      return _sorted_ids;
    TreeMap<String,Integer> map = new TreeMap<>();
    int [] sorted_ids = new int[_names.length];
    for(int i = 0; i < _names.length; ++i)
      map.put(_names[i],i);
    int j = 0;
    for(Map.Entry<String,Integer> e:map.entrySet())
      sorted_ids[j++] = e.getValue();
    for(int i = 1; i < sorted_ids.length; ++i)
      assert _names[sorted_ids[i]].compareTo(_names[sorted_ids[i-1]]) > 0;
    return _sorted_ids = sorted_ids;
  }

  private boolean _lastNameBig; // Last name is "Cxxx" and has largest number
  protected VecAry _vecs; // The Vectors (transient to avoid network traffic)

  public boolean hasNAs(){
    RollupsAry rsa = _vecs.rollupStats();
    for(int i = 0; i < _vecs.numCols(); ++i)
      if (rsa.getRollups(i)._naCnt > 0) return true;
    return false;
  }

  public Frame(Vec... vecs){this(null, new VecAry(vecs));}
  public Frame(VecAry vecs){this(null,vecs);}
  /** Creates an internal frame composed of the given Vecs and default names.  The frame has no key. */

  /** Creates an internal frame composed of the given Vecs and names.  The frame has no key. */
  public Frame(String names[], Vec... vecs) {this(names,new VecAry(vecs));}
  public Frame(String names[], VecAry vecs) {
    this(null, names, vecs);
  }

  /** Creates an empty frame with given key. */
  public Frame(Key<Frame> key) {
    this(key, (String[])null, new VecAry());
  }

  /**
   * Special constructor for data with unnamed columns (e.g. svmlight) bypassing *all* checks.
   */
  public Frame(Key<Frame> key, VecAry vecs, boolean noChecks) {
    super(key);
    assert noChecks;
    _vecs = vecs;
    _names = new String[vecs.numCols()];
    for (int i = 0; i < vecs.numCols(); i++)
      _names[i] = defaultColName(i);
  }

  public Frame(Key k, String names[], Vec... vecs) { this(k,names,new VecAry(vecs));}
    /** Creates a frame with given key, names and vectors. */
  public Frame(Key<Frame> key, String names[], VecAry vecs) { // allways only 1 vec
    super(key);
    // Require all Vecs already be installed in the K/V store
    _vecs = new VecAry(vecs);
    // Always require names
    if( names==null ) {         // Make default names, all known to be unique
      _names = new String[_vecs.numCols()];

      for( int i=0; i<_vecs.numCols(); i++ ) _names[i] = defaultColName(i);
      _lastNameBig = true;
    } else {
      // Make empty to dodge asserts, then "add()" them all which will check
      // for compatible Vecs & names.
      _names = names;
    }
    assert _names.length == _vecs.numCols():_names.length + "  != " + _vecs.numCols();
  }

  public void setNames(String[] columns){
    if(columns.length!= _vecs.numCols()){
      throw new IllegalArgumentException("Size of array containing column names does not correspond to the number of vecs!");
    }
    _names = columns;
  }
  /** Deep copy of Vecs and Keys and Names (but not data!) to a new random Key.
   *  The resulting Frame does not share with the original, so the set of Vecs
   *  can be freely hacked without disturbing the original Frame. */
  public Frame( Frame fr ) {
    super( Key.<Frame>make() );
    _names= fr._names.clone();
    _vecs = new VecAry(fr.vecs());
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
    return vecs().checkCompatible(fr.vecs());
  }

  /** Number of columns
   *  @return Number of columns */
  public int  numCols() { return _vecs.numCols(); }
  /** Number of rows
   *  @return Number of rows */
  public long numRows() { Vec v = anyVec(); return v==null ? 0 : v.length(); }

  /** Returns the first readable vector.
   *  @return the first readable Vec */
  public final Vec anyVec() {return _vecs.isEmpty()?null:_vecs.fetchVec(0);}

  /** The array of column names.
   *  @return the array of column names */
  public String[] names() { return _names; }

  /** A single column name.
   *  @return the column name */
  public String name(int i) {
    return _names[i];
  }

  public final VecAry vecs(int... idxs) {
    return idxs.length == 0?_vecs:_vecs.select(idxs);
  }

  public VecAry vecs(String[] names) {
    return vecs(find(names));
  }


  /** Convenience to accessor for last Vec
   *  @return last Vec */
  public VecAry lastVec() {return _vecs.select(_vecs.numCols()-1);}
  /** Convenience to accessor for last Vec name
   *  @return last Vec name */
  public String lastVecName() {  return _names[_names.length-1]; }

  /** Force a cache-flush and reload, assuming vec mappings were altered
   *  remotely, or that the _vecs array was shared and now needs to be a
   *  defensive copy.
   *  @return the new instance of the Frame's Vec[] */
  public final VecAry reloadVecs() { _vecs.reloadVecs(); return _vecs; }

  /** Returns the Vec by given index, implemented by code: {@code vecs()[idx]}.
   *  @param idx idx of column
   *  @return this frame idx-th vector, never returns <code>null</code> */
  public final VecAry vec(int idx) { return _vecs.select(idx); }

  /**  Return a Vec by name, or null if missing
   *  @return a Vec by name, or null if missing */
  public VecAry vec(String name) { int idx = find(name); return idx==-1 ? null : _vecs.select(idx); }

  /**   Finds the column index with a matching name, or -1 if missing
   *  @return the column index with a matching name, or -1 if missing */
  public int find( String name ) {
    if( name == null ) return -1;
    int [] sorted_ids = nameSortedIds();
    int lb = 0, ub = _names.length;
    while(ub > lb){
      int mid = lb + ((ub - lb) >> 1);
      int x = sorted_ids[mid];
      int c = name.compareTo(_names[x]);
      if(c == 0) return x;
      if(c > 0){
        lb = mid+1;
      } else {
        ub = mid;
      }
    }
    return -1;
  }

  public int find( VecAry v ) {
    if(v.numCols() != 1) throw new IllegalArgumentException("expected exactly one vec");
    int vid = v._vecIds[0];
    int x = ArrayUtils.find(_vecs._vecIds,vid);
    if(x == -1)return -1;
    int off = _vecs._blockOffset[x];
    int id =  v._colFilter == null?0:v._colFilter[0];
    int y = off + id;
    return _vecs._colFilter == null?y:ArrayUtils.find(_vecs._colFilter,y);
  }

  /** Bulk {@link #find(String)} api
   *  @return An array of column indices matching the {@code names} array */
  public int[] find(String[] names) {
    int [] sorted_ids = nameSortedIds();
    for(int i = 1; i < sorted_ids.length; ++i)
      assert _names[sorted_ids[i]].compareTo(_names[sorted_ids[i-1]]) > 0;
    if( names == null ) return null;
    int[] res = new int[names.length];
    for(int i = 0; i < names.length; ++i)
      res[i] = find(names[i]);
    return res;
  }

  public void insertVec(int i, String row, VecAry x) {
    _names = ArrayUtils.insert(_names,i,row);
    _vecs.insertVec(i,x);
  }

  public String[] domain(int c) {return _vecs.domain(c);}


  public Frame subframe(String[] names) {
    return subframe(find(names));
  }
  public Frame subframe(int[] ids) {
    return new Frame(ArrayUtils.select(_names,ids),_vecs.select(ids));
  }

  /** Pair of (column name, Frame key). */
  public static class VecSpecifier extends Iced {
    public Key<Frame> _frame;
    public String _column_name;


    public VecAry vec() {
      Value v = DKV.get(_frame);
      if (null == v) return null;
      Frame f = v.get();
      if (null == f) return null;
      return f.vec(_column_name);
    }
  }

  /** Type for every Vec */
  public byte[] types() {return _vecs.types();}

  /** String name for each Vec type */
  public String[] typesStr() {  // typesStr not strTypes since shows up in intelliJ next to types
    String [] res = new String[_vecs._numCols];
    for(int i = 0; i < res.length; ++i)
      res[i] = _vecs.get_type_str(i);
    return res;
  }

  /** All the domains for categorical columns; null for non-categorical columns.
   *  @return the domains for categorical columns */
  public String[][] domains() {return _vecs.domains();}

  /** Number of categorical levels for categorical columns; -1 for non-categorical columns.
   * @return the number of levels for categorical columns */
  public int[] cardinality() {

    int[] card = new int[_vecs._numCols];
    for( int i=0; i<_vecs._numCols; i++ )
      card[i] = _vecs.cardinality(i);
    return card;
  }

  public RollupsAry bulkRollups() {return _vecs.rollupStats();}

  /** Majority class for categorical columns; -1 for non-categorical columns.
   * @return the majority class for categorical columns */
  public int[] modes() {
    int[] modes = new int[_vecs._numCols];
    for( int i = 0; i < modes.length; i++ )
      modes[i] = _vecs.isCategorical(i) ? _vecs.mode(i) : -1;
    return modes;
  }

  /** All the column means.
   *  @return the mean of each column */
  public double[] means() {
    RollupsAry rs = _vecs.rollupStats();
    double[] means = new double[_vecs._numCols];
    for( int i = 0; i < _vecs._numCols; i++ )
      means[i] = rs.getRollups(i)._mean;
    return means;
  }

  /** One over the standard deviation of each column.
   *  @return Reciprocal the standard deviation of each column */
  public double[] mults() {
    RollupsAry rs = _vecs.rollupStats();
    double[] mults = new double[_vecs._numCols];
    for( int i = 0; i < _vecs._numCols; i++ ) {
      double sigma = rs.getRollups(i)._sigma;
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
      RollupsAry rs = _vecs.rollupStats();
      long sum = 0;
      for (int i = 0; i < _vecs._numCols; ++i)
        sum += rs.getRollups(i)._size;
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
    RollupsAry rs = _vecs.rollupStats();
    long _checksum = 0; //_vecs.rollupStats()._checksum;
    for( int i = 0; i < _names.length; ++i ) {
      long vec_checksum = rs.getRollups(i)._checksum;
      _checksum ^= vec_checksum;
      long tmp = (2147483647L * i);
      _checksum ^= tmp;
    }
    _checksum *= (0xBABE + Arrays.hashCode(_names));
    // TODO: include column types?  Vec.checksum() should include type?
    return _checksum;
  }

  public void add( String[] names, Vec vecs) {
    bulkAdd(names, new VecAry(vecs));
  }
  // Add a bunch of vecs
  public void add( String[] names, VecAry vecs) {
    bulkAdd(names, vecs);
  }
  public void add( String[] names, VecAry vecs, int cols ) {
    if (null == vecs || null == names) return;
    bulkAdd(names, vecs);
  }

  /** Append multiple named Vecs to the Frame.  Names are forced unique, by appending a
   *  unique number if needed.
   */
  private void bulkAdd(String[] names, VecAry vecs) {
    String[] tmpnames = names.clone();
    int N = names.length;
    assert(names.length == vecs._numCols):"names = " + Arrays.toString(names) + ", vecs len = " + vecs._numCols;
    int ncols = _names.length;
    // make temp arrays and don't assign them back until they are fully filled - otherwise vecs() can cache null's and NPE.
    String[] tmpnam = Arrays.copyOf(_names, ncols+N);
    for (int i=0; i<N; ++i)
      tmpnam[ncols+i] = tmpnames[i];
    _vecs.append(vecs);
    _names = tmpnam;
    _sorted_ids = null;
  }

  /** Append a named Vec to the Frame.  Names are forced unique, by appending a
   *  unique number if needed.
   *  @return the added Vec, for flow-coding */
  public VecAry add( String name, VecAry vec ) {
    vec = makeCompatible(new Frame(vec));
    int ncols = _names.length;
    String[] names = Arrays.copyOf(_names,ncols+1);  names[ncols] = name;
    _names = names;
    _vecs.append(vec);
    _sorted_ids = null;
    return vec;
  }

  /** Append a Frame onto this Frame.  Names are forced unique, by appending
   *  unique numbers if needed.
   *  @return the expanded Frame, for flow-coding */
  public Frame add( Frame fr ) { add(fr._names,fr.vecs(),fr.numCols()); return this; }

  /** Insert a named column as the first column */
  public Frame prepend( String name, VecAry vec ) {
    if( find(name) != -1 ) throw new IllegalArgumentException("Duplicate name '"+name+"' in Frame");
    _vecs = new VecAry(vec).append(_vecs);
    final int len = _names != null ? _names.length : 0;
    String[] _names2 = new String[len + 1];
    _names2[0] = name;
    if (_names != null)
      System.arraycopy(_names, 0, _names2, 1, len);
    _names = _names2;
    _sorted_ids = null;
    return this;
  }

  /** Swap two Vecs in-place; useful for sorting columns by some criteria */
  public void swap( int lo, int hi ) {
    assert 0 <= lo && lo < numCols();
    assert 0 <= hi && hi < numCols();
    if( lo==hi ) return;
    _vecs.swap(lo,hi);
    String n=_names[lo]; _names[lo] = _names[hi]; _names[hi] = n;
    _sorted_ids = null;
  }

  /** move the provided columns to be first, in-place. For Merge currently since method='hash' was coded like that */
  public void moveFirst( int cols[] ) {
    String [] names = new String[_names.length];
    int j = 0, k = cols.length;
    for(int i = 0; i < _names.length; ++i){
      if(i == cols[j]){
        names[j++] = _names[i];
      } else
        names[k++] = _names[i];
    }
    _names = names;
    _vecs.moveFirst(cols);
    _sorted_ids = null;
  }


  /** Allow rollups for all written-into vecs; used by {@link MRTask} once
   *  writing is complete.
   *  @return the original Futures, for flow-coding */
  public Futures postWrite(Futures fs) {
    return _vecs.postWrite(fs);
  }

  /** Actually removeVecs/delete all Vecs from memory, not just from the Frame.
   *  @return the original Futures, for flow-coding */
  @Override protected Futures remove_impl(Futures fs) {
    return _vecs.remove(fs);
  }

  /** Replace one column with another. Caller must perform global update (DKV.put) on
   *  this updated frame.
   *  @return The old column, for flow-coding */
  public VecAry replace(int col, VecAry nv) {
    return _vecs.replace(col,nv);
  }

  /** Create a subframe from given interval of columns.
   *  @param startIdx  index of first column (inclusive)
   *  @param endIdx index of the last column (exclusive)
   *  @return a new Frame containing specified interval of columns  */
  public Frame subframe(int startIdx, int endIdx) {
    return new Frame(Arrays.copyOfRange(_names,startIdx,endIdx),_vecs.select(ArrayUtils.seq(startIdx,endIdx)));
  }

  /** Split this Frame; return a subframe created from the given column interval, and
   *  removeVecs those columns from this Frame.
   *  @param startIdx index of first column (inclusive)
   *  @param endIdx index of the last column (exclusive)
   *  @return a new Frame containing specified interval of columns */
  public Frame extractFrame(int startIdx, int endIdx) {
    Frame f = subframe(startIdx, endIdx);
    removeVecs(startIdx, endIdx);
    return f;
  }

  /** Removes the column with a matching name.
   *  @return The removed column */
  public VecAry removeVecs(String name ) { return removeVecs(find(name)); }

  public VecAry removeVecs(String[] names ) {
    int [] idxs = new int[names.length];
    int j = 0;
    for(int i = 0; i < names.length; ++i) {
      if(names[i].length() == 0) continue;
      int x = find(names[i]);
      if(x != -1)idxs[j++] = x;
    }
    return (j > 0)?removeVecs(Arrays.copyOf(idxs,j)):new VecAry();
  }

  /** Removes a list of columns by index; the index list must be sorted
   *  @return an array of the removed columns */
  public VecAry removeVecs(int... idxs ) {
    VecAry res = _vecs.removeVecs(idxs);
    if(!ArrayUtils.isSorted(idxs)){
      idxs = idxs.clone();
      Arrays.sort(idxs);
    }
    String [] names = new String[_names.length - idxs.length];int j = 0, k = 0;
    for(int i = 0; i < _names.length; ++i)
      if(j < idxs.length && i == idxs[j])++j;
      else names[k++] = _names[i];
    _names = names;
    assert _names.length == _vecs._numCols;
    if(_sorted_ids != null){ // update sorted ids
      int rem = 0;
      for(int i = 0; i < _sorted_ids.length; ++i){
        if(Arrays.binarySearch(idxs,_sorted_ids[i]) >= 0){
          rem++;
        } else if(rem > 0){
          _sorted_ids[i-rem] = _sorted_ids[i];
        }
      }
      _sorted_ids = Arrays.copyOf(_sorted_ids,_sorted_ids.length-rem);
    }
    return res;
  }

  /** Remove given interval of columns from frame.  Motivated by R intervals.
   *  @param startIdx - start index of column (inclusive)
   *  @param endIdx - end index of column (exclusive)
   *  @return array of removed columns  */
  VecAry removeVecs(int startIdx, int endIdx) {return removeVecs(ArrayUtils.seq(startIdx,endIdx));}


  /** Restructure a Frame completely, but only for a specified number of columns (counting up)  */
  public void restructure( String[] names, VecAry vecs) {
    // Make empty to dodge asserts, then "add()" them all which will check for
    // compatible Vecs & names.
    if(_sorted_ids != null && !Arrays.deepEquals(_names,names))
      _sorted_ids = null;
    _names = names;
    _vecs = vecs;
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
      VecAry.Reader v = fr.vecs().new Reader();
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
        @Override public void map(ChunkAry chks, NewChunkAry nchks) { for (int i = 0; i < nchks._numCols; ++i) nchks.addNA(i); }
      }.doAll(types(c2), this).outputFrame(names(c2), domains(c2));
    }
    if (orows == null)
      return new DeepSlice(null,c2,_vecs).doAll(types(c2),this).outputFrame(names(c2),domains(c2));
    else if (orows instanceof long[]) {
      final long CHK_ROWS=1000000;
      final long[] rows = (long[])orows;
      if (this.numRows() == 0) {
        return this;
      }
      if( rows.length==0 || rows[0] < 0 ) {
        if (rows.length != 0 && rows[0] < 0) {
          Vec v0 = this.anyVec().makeZero();
          VecAry v = new MRTask() {
            @Override public void map(ChunkAry cs) {
              for (long er : rows) {
                if (er >= 0) continue;
                er = Math.abs(er);
                if (er < cs._start || er > (cs._len + cs._start - 1)) continue;
                cs.set((int) (er - cs._start), 1);
              }
            }
          }.doAll(v0).getResult()._fr._vecs;
          Keyed.remove(v0._key);
          Frame slicedFrame = new DeepSlice(rows, c2, vecs()).doAll(types(c2), this.add("select_vec", v)).outputFrame(names(c2), domains(c2));
          v.remove();
          this.removeVecs(this.numCols() - 1).remove();
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
        NewChunkAry nc =av.chunkForChunkIdx(c++);
        long end = Math.min(r+CHK_ROWS, rows.length);
        for (; r < end; r++) {
          nc.addNum(rows[r]);
        }
        nc.close(fs);
      }
      Vec c0 = av.close(fs);   // c0 is the row index vec
      fs.blockForPending();
      Frame ff = new Frame(new String[]{"rownames"}, c0);
      Frame fr2 = new Slice(c2, this).doAll(types(c2),ff).outputFrame(names(c2), domains(c2));
      Keyed.remove(c0._key);
      Keyed.remove(av._key);
      ff.delete();
      return fr2;
    }
    Frame frows = (Frame)orows;
    // It's a compatible Vec; use it as boolean selector.
    // Build column names for the result.
    VecAry vecs = _vecs.select(c2);
    String [] names = ArrayUtils.select(_names,c2);
    Frame ff = new Frame(names, vecs);
    ff.add("predicate", frows._vecs);
    return new DeepSelect().doAll(types(c2),ff).outputFrame(names(c2),domains(c2));
  }

  // Slice and return in the form of new chunks.
  private static class Slice extends MRTask<Slice> {
    final Frame  _base;   // the base frame to slice from
    final VecAry _vecs;
    final int[]  _cols;
    Slice(int[] cols, Frame base) { _cols = cols; _base = base; _vecs = _base._vecs.select(_cols);}
    @Override public void map(ChunkAry ix, NewChunkAry ncs) {
      BufferedString bs = new BufferedString();
      final VecAry vecs = _base._vecs.select(_cols);
      final long  nrow = _vecs.length();
      long  r    = ix.at8(0,0);
      int   last_ci = vecs.elem2ChunkIdx(r<nrow?r:0); // memoize the last chunk index
      long  last_c0 = vecs.espc()[last_ci];            // ...         last chunk start
      long  last_c1 = vecs.espc()[last_ci + 1];        // ...         last chunk end
      ChunkAry last_cs = _vecs.chunkForChunkIdx(last_ci);

      for (int i = 0; i < ix._len; i++) {
        // select one row
        r = ix.at8(i,0);   // next row to select
        if (r < 0) continue;
        if (r >= nrow) {
          for (int c = 0; c < _vecs._numCols; c++) ncs.addNum(c,Double.NaN);
        } else {
          if (r < last_c0 || r >= last_c1) {
            last_ci = vecs.elem2ChunkIdx(r);
            last_c0 = vecs.espc()[last_ci];
            last_c1 = vecs.espc()[last_ci + 1];
            last_cs = vecs.chunkForChunkIdx(last_ci);
          }
          int rowInChunk = last_cs.chunkRelativeOffset(r);
          for (int c = 0; c < vecs._numCols; c++)
            if( vecs.isUUID(c) ) ncs.addUUID(c,last_cs.at16l(rowInChunk,c),last_cs.at16h(rowInChunk,c));
            else if( vecs.isString(c) ) ncs.addStr(c,last_cs.atStr(bs,rowInChunk,c));
            else                        ncs.addNum (c,last_cs.atd(rowInChunk,c));
        }
      }
    }
  }


  // Convert len rows starting at off to a 2-d ascii table
  @Override public String toString( ) {
    return ("Frame key: " + _key + "\n") +
            "   cols: " + numCols() + "\n" +
            "   rows: " + numRows() + "\n" +
            " chunks: " + (anyVec() == null ? "N/A" : anyVec().nChunks()) + "\n";
  }

  public String toString(long off, int len) { return toTwoDimTable(off, len).toString(); }
  public String toString(long off, int len, boolean rollups) { return toTwoDimTable(off, len, rollups).toString(); }
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

    String[] coltypes = new String[ncols];
    String[][] strCells = new String[len+H][ncols];
    double[][] dblCells = new double[len+H][ncols];
    final BufferedString tmpStr = new BufferedString();
    RollupsAry rs = _vecs.rollupStats();
    for( int i=0; i<ncols; i++ ) {
      if(rs.isRemoved(i)){
        coltypes[i] = "string";
        for( int j=0; j<len+H; j++ ) dblCells[j][i] = TwoDimTable.emptyDouble;
        for( int j=0; j<len; j++ ) strCells[j+H][i] = "NO_VEC";
        continue;
      }
      if( rollups && rs.isReady(_vecs,false)) {
        RollupStats x = rs.getRollups(i);
        dblCells[0][i] = x._mins[0];
        dblCells[1][i] = x._mean;
        dblCells[2][i] = x._sigma;
        dblCells[3][i] = x._maxs[0];
        dblCells[4][i] = x._naCnt;
      }

      switch( _vecs.getType(i) ) {
      case Vec.T_BAD:
        coltypes[i] = "string";
        for( int j=0; j<len; j++ ) { strCells[j+H][i] = null; dblCells[j+H][i] = TwoDimTable.emptyDouble; }
        break;
      case Vec.T_STR :
        coltypes[i] = "string";
        for( int j=0; j<len; j++ ) { strCells[j+H][i] = _vecs.isNA(off+j,i) ? "" : _vecs.atStr(tmpStr,off+j).toString(); dblCells[j+H][i] = TwoDimTable.emptyDouble; }
        break;
      case Vec.T_CAT:
        coltypes[i] = "string";
        for( int j=0; j<len; j++ ) { strCells[j+H][i] = _vecs.isNA(off+j,i) ? "" : _vecs.factor(i,_vecs.at4(off+j,i));  dblCells[j+H][i] = TwoDimTable.emptyDouble; }
        break;
      case Vec.T_TIME:
        coltypes[i] = "string";
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        for( int j=0; j<len; j++ ) { strCells[j+H][i] = _vecs.isNA(off+j,i) ? "" : fmt.print(_vecs.at8(off+j)); dblCells[j+H][i] = TwoDimTable.emptyDouble; }
        break;
      case Vec.T_NUM:
        coltypes[i] = _vecs.isInt(i) ? "long" : "double";
        for( int j=0; j<len; j++ ) { dblCells[j+H][i] = _vecs.isNA(off+j,i) ? TwoDimTable.emptyDouble : _vecs.at(off + j); strCells[j+H][i] = null; }
        break;
      case Vec.T_UUID:
        throw H2O.unimpl();
      default:
        System.err.println("bad vector type during debug print: "+_vecs.getType(i));
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
    DeepSlice( long rows[], int cols[], VecAry vecs ) {
      _cols=cols;
      _rows=rows;
      _isInt = new byte[cols.length];
      for( int i=0; i<cols.length; i++ )
        _isInt[i] = (byte)(vecs.isInt(cols[i]) ? 1 : 0);
    }

    @Override public boolean logVerbose() { return false; }

    @Override public void map( ChunkAry chks, NewChunkAry nchks ) {
      long rstart = chks._start;
      int rlen = chks._len;  // Total row count
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
          int oc = _cols[i];

          if (_isInt[i] == 1) { // Slice on integer columns
            for (int j = rlo; j < rhi; j++)
              if (chks._vec.isUUID(oc)) nchks.addUUID(i,oc, j);
              else if (chks.isNA(j,oc)) nchks.addNA(i);
              else nchks.addNum(i,chks.at8(j,oc), 0);
          } else if (chks._vec.isString(oc)) {
            for (int j = rlo; j < rhi; j++)
              nchks.addStr(i,chks.atStr(tmpStr, j, oc));
          } else {// Slice on double columns
            for (int j = rlo; j < rhi; j++)
              nchks.addNum(i,chks.atd(j,oc));
          }
        }
        rlo = rhi;
        if (_rows == null) break;
      }
    }
  }

  public static Frame deepCopy(Frame src, String keyName){
    final Vec v = new Vec(src.anyVec().group().addVec(),src.anyVec()._rowLayout,src.domains(),src.types());
    new MRTask() {
      @Override public void map(ChunkAry cs) {
        Chunk[] chks = cs.getChunks().clone();
        for(int i = 0; i < chks.length; ++i)
          chks[i] = chks[i].deepCopy();
        DKV.put(v.chunkKey(cs._cidx),new DBlock.MultiChunkBlock(chks),_fs);
      }
    }.doAll(src);
    return new Frame(keyName == null?null:Key.<Frame>make(keyName),src.names().clone(),v);
  }
  /**
   * Create a copy of the input Frame and return that copied Frame. All Vecs in this are copied in parallel.
   * Caller mut do the DKV.put
   * @param keyName Key for resulting frame. If null, no key will be given.
   * @return The fresh copy of fr.
   */
  public Frame deepCopy(String keyName) {
   return deepCopy(this,keyName);
  }

  /**
   *  Last column is a bit vec indicating whether or not to take the row.
   */
  public static class DeepSelect extends MRTask<DeepSelect> {
    @Override public void map( ChunkAry chks, NewChunkAry nchks ) {
      int pred = chks._numCols - 1;
      int[] ids = new int[chks._len];
      int selected = 0;
      for(int i = 0; i < chks._len; ++i)
        if(!chks.isNA(i,pred) && chks.at4(i,pred) != 0)ids[selected++] = i;
      ids = Arrays.copyOf(ids,selected);
      for (int c = 0; c < pred; ++c)
        chks.add2Chunk(c,nchks,c,ids);
    }
  }
  private String[][] domains(int [] cols){
    String[][] res = new String[cols.length][];
    for(int i = 0; i < cols.length; ++i)
      res[i] = _vecs.domain(cols[i]);
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
    byte[] res = new byte[cols.length];
    for(int i = 0; i < cols.length; ++i)
      res[i] = _vecs.getType(cols[i]);
    return res;
  }

  public VecAry makeCompatible( Frame f) {return makeCompatible(f,false);}
  /** Return array of Vectors if 'f' is compatible with 'this', else return a new
   *  array of Vectors compatible with 'this' and a copy of 'f's data otherwise.  Note
   *  that this can, in the worst case, copy all of {@code this}s' data.
   *  @return This Frame's data in an array of Vectors that is compatible with {@code f}. */
  public VecAry makeCompatible( Frame f, boolean force) {
    // Small data frames are always "compatible"
    if (anyVec() == null)      // Or it is small
      return f.vecs();                 // Then must be compatible
    Vec v1 = anyVec();
    Vec v2 = f.anyVec();
    if (v1 != null && v2 != null && v1.length() != v2.length())
      throw new IllegalArgumentException("Can not make vectors of different length compatible!");
    if (v1 == null || v2 == null || (!force && v1.checkCompatible(v2)))
      return f.vecs();
    // Ok, here make some new Vecs with compatible layout
    Key k = Key.make();
    H2O.submitTask(new RebalanceDataSet(this, f, k)).join();
    Frame f2 = (Frame)k.get();
    DKV.remove(k);
    for (Vec v : f2._vecs.vecs()) Scope.track(v);
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
   *  @return An InputStream containing this Frame as a CSV */
  public InputStream toCSV(boolean headers, boolean hex_string) {
    return new CSVStream(this, headers, hex_string);
  }

  public static class CSVStream extends InputStream {
    private final boolean _hex_string;
    byte[] _line;
    int _position;
    int _chkRow;
    ChunkAry _curChks;
    int _lastChkIdx;
    public volatile int _curChkIdx; // used only for progress reporting

    public CSVStream(Frame fr, boolean headers, boolean hex_string) {
      this(firstChunks(fr), headers ? fr.names() : null, fr.anyVec().nChunks(), hex_string);
    }

    private static ChunkAry firstChunks(Frame fr) {
      Vec anyvec = fr.anyVec();
      if (anyvec == null || anyvec.nChunks() == 0 || anyvec.length() == 0) {
        return null;
      }
      return fr._vecs.chunkForChunkIdx(0);
    }

    public CSVStream(ChunkAry chks, String[] names, int nChunks, boolean hex_string) {
      if (chks == null) nChunks = 0;
      _lastChkIdx = (chks != null) ? chks._cidx + nChunks - 1 : -1;
      _hex_string = hex_string;
      StringBuilder sb = new StringBuilder();
      if (names != null) {
        sb.append('"').append(names[0]).append('"');
        for(int i = 1; i < names.length; i++)
          sb.append(',').append('"').append(names[i]).append('"');
        sb.append('\n');
      }
      _line = sb.toString().getBytes();
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
      for (int i = 0; i < _curChks._numCols; i++ ) {
        if(i > 0) sb.append(',');
        if(!_curChks.isNA(_chkRow,i)) {
          if( _curChks._vec.isCategorical(i) ) sb.append('"').append(_curChks._vec.factor(i,_curChks.at4(_chkRow,i))).append('"');
          else if( _curChks._vec.isUUID(i) ) sb.append(PrettyPrint.UUID(_curChks.at16l(_chkRow,i), _curChks.at16h(_chkRow,i)));
          else if( _curChks._vec.isInt(i) ) sb.append(_curChks.at8(_chkRow,i));
          else if (_curChks._vec.isString(i)) sb.append('"').append(_curChks.atStr(tmpStr, _chkRow,i)).append('"');
          else {
            double d = _curChks.atd(_chkRow,i);
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

      // Case 2:  There are no chunks to work with (eg. the whole Frame was empty).
      if (_curChks == null) {
        return 0;
      }

      _chkRow++;


      // Case 3:  Out of data.
      if (_curChks._start + _chkRow == _curChks._vec.length()) {
        return 0;
      }

      // Case 4:  Out of data in the current chunks => fast-forward to the next set of non-empty chunks.
      if (_chkRow == _curChks._len) {
        _curChkIdx = _curChks._vec.elem2ChunkIdx(_curChks._start + _chkRow); // skips empty chunks
        // Case 4:  Processed all requested chunks.
        if (_curChkIdx > _lastChkIdx) {
          return 0;
        }
        // fetch the next non-empty chunks
        ChunkAry newChunks = _curChks._vec.chunkForChunkIdx(_curChkIdx);
        for (Vec v :newChunks._vec.vecs()) {
          Key oldKey = v.chunkKey(_curChks._cidx);
          if (! oldKey.home()) {
            H2O.raw_remove(oldKey);
          }
        }
        _curChks = newChunks;
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

  /** Sort rows of a frame, using the set of columns as keys.
   *  @return Copy of frame, sorted */
  public Frame sort( int[] cols ) { return Merge.sort(this,cols); }
}
