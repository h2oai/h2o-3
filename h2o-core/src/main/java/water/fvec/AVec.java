package water.fvec;

import water.*;
import water.nbhm.NonBlockingHashMap;
import water.util.*;

import java.util.Arrays;
import java.util.UUID;

/** A distributed vector/array/column of uniform data.
 *
 *  <p>A distributed vector has a count of elements, an element-to-chunk
 *  mapping, a Java-like type (mostly determines rounding on store and
 *  display), and functions to directly load elements without further
 *  indirections.  The data is compressed, or backed by disk or both.
 *
 *  <p>A Vec is a collection of {@link Chunk}s, each of which holds between 1,000
 *  and 1,000,000 elements.  Operations on a Chunk are intended to be
 *  single-threaded; operations on a Vec are intended to be parallel and
 *  distributed on Chunk granularities, with each Chunk being manipulated by a
 *  separate CPU.  The standard Map/Reduce ({@link MRTask}) paradigm handles
 *  parallel and distributed Chunk access well.
 *
 *  <p>Individual elements can be directly accessed like a (very large and
 *  distributed) array - however this is not the fastest way to access the
 *  data.  Direct access from Chunks is faster, avoiding several layers of
 *  indirection.  In particular accessing a random row from the Vec will force
 *  the containing Chunk data to be cached locally (and network traffic to
 *  bring it local); accessing all rows from a single machine will force all
 *  the Big Data to be pulled local typically resulting in swapping and very
 *  poor performance.  The main API is provided for ease of small-data
 *  manipulations and is fairly slow for writing; when touching ALL the data
 *  you are much better off using e.g. {@link MRTask}.
 *

 *
 *  <p>Example manipulating some individual elements:<pre>
 *    double r1 = vec.at(0x123456789L);  // Access element 0x1234567889 as a double
 *    double r2 = vec.at(-1);            // Throws AIOOBE
 *    long   r3 = vec.at8_abs(1);        // Element #1, as a long
 *    vec.set(2,r1+r3);                  // Set element #2, as a double
 *  </pre>
 *

 *
 *  <p>Reading elements as doubles, or checking for an element missing is
 *  always safe.  Reading a missing integral type throws an exception, since
 *  there is no NaN equivalent in the integer domain.  <em>Writing</em> to
 *  elements may throw if the backing data is read-only (file backed), and
 *  otherwise is fully supported.
 *
 *  <p>Note this dangerous scenario: loading a missing value as a double, and
 *  setting it as a long: <pre>
 *   set(row,(long)at(row)); // Danger!
 *</pre>
 *  The cast from a Double.NaN to a long produces a zero!  This code will
 *  silently replace a missing value with a zero.
 *

 *
 *  <p>Example usage of common stats:<pre>
 *    double mean = vec.mean();  // Vec's mean; first touch computes and caches rollups
 *    double min  = vec.min();   // Smallest element; already computed
 *    double max  = vec.max();   // Largest element; already computed
 *    double sigma= vec.sigma(); // Standard deviation; already computed
 *  </pre>
 *
 *  <p>Example: Impute (replace) missing values with the mean.  Note that the
 *  use of {@code vec.mean()} in the constructor uses (and computes) the
 *  general RollupStats before the MRTask starts.  Setting a value in the Chunk
 *  clears the RollupStats (since setting any value but the mean will change
 *  the mean); they will be recomputed at the next use after the MRTask.
 *  <pre>
 *    new MRTask{} { final double _mean = vec.mean();
 *      public void map( Chunk chk ) {
 *        for( int row=0; row &lt; chk._len; row++ )
 *          if( chk.isNA(row) ) chk.set(row,_mean);
 *      }
 *    }.doAll(vec);
 *  </pre>
 *
 *  <p>Vecs have a {@link AVec.VectorGroup}.  Vecs in the same VectorGroup have the
 *  same Chunk and row alignment - that is, Chunks with the same index are
 *  homed to the same Node and have the same count of rows-per-Chunk.  {@link
 *  Frame}s are only composed of Vecs of the same VectorGroup (or very small
 *  Vecs) guaranteeing that all elements of each row are homed to the same Node
 *  and set of Chunks - such that a simple {@code for} loop over a set of
 *  Chunks all operates locally.  See the example in the {@link Chunk} class.
 *
 *  <p>It is common and cheap to make new Vecs in the same VectorGroup as an
 *  existing Vec and initialized to e.g. zero.  Such Vecs are often used as
 *  temps, and usually immediately set to interest values in a later MRTask
 *  pass.
 *
 *  <p>Example creation of temp Vecs:<pre>
 *    Vec tmp0 = vec.makeZero();         // New Vec with same VectorGroup and layout as vec, filled with zero
 *    Vec tmp1 = vec.makeCon(mean);      // Filled with 'mean'
 *    assert tmp1.at(0x123456789)==mean; // All elements filled with 'mean'
 *    for( int i=0; i&lt;100; i++ )         // A little math on the first 100 elements
 *      tmp0.set(i,tmp1.at(i)+17);       // ...set into the tmp0 vec
 *  </pre>
 *
 *  <p>Vec {@link Key}s have a special layout (enforced by the various Vec
 *  constructors) so there is a direct Key mapping from a Vec to a numbered
 *  Chunk and vice-versa.  This mapping is crucially used in all sorts of
 *  places, basically representing a global naming scheme across a Vec and the
 *  Chunks that make it up.  The basic layout created by {@link #newKey}:
 * <pre>
 *              byte:    0      1   2 3 4 5  6 7 8 9  10+
 *  Vec   Key layout: Key.VEC  -1   vec#grp    -1     normal Key bytes; often e.g. a function of original file name
 *  Chunk Key layout: Key.CHK  -1   vec#grp  chunk#   normal Key bytes; often e.g. a function of original file name
 *  RollupStats Key : Key.CHK  -1   vec#grp    -2     normal Key bytes; often e.g. a function of original file name
 *  Group Key layout: Key.GRP  -1     -1       -1     normal Key bytes; often e.g. a function of original file name
 *  ESPC  Key layout: Key.GRP  -1     -1       -2     normal Key bytes; often e.g. a function of original file name
 * </pre>
 *
 * @author Cliff Click
 */
public abstract class AVec<T extends AVec.AChunk<T>> extends Keyed<AVec> {

  private static NonBlockingHashMap<Key,RPC> _pendingRollups = new NonBlockingHashMap<>();

  public RollupStatsAry getRollupStats(boolean computeHisto){
    Key rsKey = rollupStatsKey();
    RollupStatsAry res = DKV.getGet(rsKey);
    while(res == null || !res.isReady() || computeHisto && !res.hasHisto()) {
      if(res != null && res.isMutating())
        throw new IllegalArgumentException("Can not access rollups while vec is being modified. (1)");
      try {
        RPC rpcNew = new RPC(rsKey.home_node(),new ComputeRollupsTask(this, computeHisto));
        RPC rpcOld = _pendingRollups.putIfAbsent(rsKey, rpcNew);
        if(rpcOld == null) {  // no prior pending task, need to send this one
          rpcNew.call().get();
          _pendingRollups.remove(rsKey);
        } else // rollups computation is already in progress, wait for it to finish
          rpcOld.get();
      } catch( Throwable t ) {
        System.err.println("Remote rollups failed with an exception, wrapping and rethrowing: "+t);
        throw new RuntimeException(t);
      }
      // 2. fetch - done in two steps to go through standard DKV.get and enable local caching
      res = DKV.getGet(rsKey);
    }
    return res;
  }


  public int vecId(){return VectorGroup.getVecId(_key._kb);}

  /** Element-start per chunk, i.e. the row layout.  Defined in the
   *  VectorGroup.  This field is dead/ignored in subclasses that are
   *  guaranteed to have fixed-sized chunks such as file-backed Vecs. */
  protected int _rowLayout;
  public int rowLayout(){return _rowLayout;}
  // Carefully set in the constructor and read_impl to be pointer-equals to a
  // common copy one-per-node.  These arrays can get both *very* common
  // (one-per-Vec at least, sometimes one-per-Chunk), and very large (one per
  // Chunk, could run into the millions).
  private transient long _espc[];
  private Key _rollupStatsKey;

  /** Build a numeric-type Vec; the caller understands Chunk layout (via the
   *  {@code espc} array).
   */
  public AVec(Key<AVec> key, int rowLayout) {
    super(key);
    _rowLayout = rowLayout;
    _espc = ESPC.espc(this);
  }

//  public abstract boolean hasCol(int id);


  /**
   * Check if we have local cached copy of basic Vec stats (including histogram if requested) and if not start task to compute and fetch them;
   * useful when launching a bunch of them in parallel to avoid single threaded execution later (e.g. for-loop accessing min/max of every vec in a frame).
   *
   * Does *not* guarantee rollup stats will be cached locally when done since there may be racy changes to vec which will flush local caches.
   *
   * @param fs Futures allow to wait for this task to finish.
   * @param doHisto Also compute histogram, requires second pass over data amd is not computed by default.
   *
   */
  public abstract Futures startRollupStats(Futures fs, boolean doHisto, int... colIds);


  public final RollupStats getRollups(int colId){return getRollups(colId,false);}



  public abstract RollupStats getRollups(int colId, boolean histo);
  public abstract void setDomain(int vec, String[] domain);

  public abstract void setType(int i, byte type);

  public abstract Futures removeVecs(int[] ints, Futures fs);

  public static abstract class AChunk<T extends AChunk<T>> extends Iced<T> {
    transient long _start = -1;
    transient AVec _vec = null;
    transient int _cidx = -1;
    public abstract Chunk getChunk(int i);
    public abstract  Chunk[] getChunks();
    public abstract  Chunk[] getChunks(Chunk [] chks, int off, int... ids);


    public abstract Futures close(Futures fs);

    public void setWrite(Chunk chunk) {
      throw H2O.unimpl();
    }

    public abstract Futures updateChunk(int chunkIdx,Chunk c, Futures fs);

  }

  public final long[] espc() { if( _espc==null ) _espc = ESPC.espc(this); return _espc; }

  public abstract void setBad(int colId);

  public boolean isBinary(int colId){
    return getRollups(colId).isBinary();
  }

  /** Number of elements in the vector; returned as a {@code long} instead of
   *  an {@code int} because Vecs support more than 2^32 elements. Overridden
   *  by subclasses that compute length in an alternative way, such as
   *  file-backed Vecs.
   *  @return Number of elements in the vector */
  public long length() { espc(); return _espc[_espc.length-1]; }

  public int numCols(){return 1;}

  /** Number of chunks, returned as an {@code int} - Chunk count is limited by
   *  the max size of a Java {@code long[]}.  Overridden by subclasses that
   *  compute chunks in an alternative way, such as file-backed Vecs.
   *  @return Number of chunks */
  public int nChunks() { return espc().length-1; }

  /** Convert a chunk-index into a starting row #.  For constant-sized chunks
   *  this is a little shift-and-add math.  For variable-sized chunks this is a
   *  table lookup. */
  public long chunk2StartElem(int cidx) { return espc()[cidx]; }

  /** Number of rows in chunk. Does not fetch chunk content. */
  protected final int chunkLen( int cidx ) { espc(); return (int) (_espc[cidx + 1] - _espc[cidx]); }

  /** Check that row-layouts are compatible. */
  public final boolean checkCompatible( AVec v ) {
    // Vecs are compatible iff they have same group and same espc (i.e. same length and same chunk-distribution)
    return (espc() == v.espc() || Arrays.equals(_espc, v._espc)) &&
            (VectorGroup.sameGroup(this, v) || length() < 1e3);
  }

  /** Default read/write behavior for Vecs.  File-backed Vecs are read-only. */
  boolean readable() { return true ; }
  /** Default read/write behavior for Vecs.  AppendableVecs are write-only. */
  boolean writable() { return true; }



  public abstract String [] domain(int i);


  // Vec internal type
  public static final byte T_BAD  =  0; // No none-NA rows (triple negative! all NAs or zero rows)
  public static final byte T_UUID =  1; // UUID
  public static final byte T_STR  =  2; // String
  public static final byte T_NUM  =  3; // Numeric, but not categorical or time
  public static final byte T_CAT  =  4; // Integer, with a categorical/factor String mapping
  public static final byte T_TIME =  5; // Long msec since the Unix Epoch - with a variety of display/parse options
  public static final String[] TYPE_STR=new String[] { "BAD", "UUID", "String", "Numeric", "Enum", "Time", "Time", "Time"};

  public static String getTypeString(byte type) {return TYPE_STR[type];}

  public static final boolean DO_HISTOGRAMS = true;

  public abstract byte type(int colId);

  public final double sparseRatio(int colId) {return getRollups(colId)._nzCnt/(double)length();}
  /** True if this is a UUID column.
   *  @return true if this is a UUID column.  */
  public final boolean isUUID   (int colId){ return type(colId)==T_UUID; }
  /** True if this is a String column.
   *  @return true if this is a String column.  */
  public final boolean isString (int colId){ return type(colId)==T_STR; }
  /** True if this is a numeric column, excluding categorical and time types.
   *  @return true if this is a numeric column, excluding categorical and time types  */
  public final boolean isNumeric(int colId){ return type(colId)==T_NUM; }
  /** True if this is a time column.  All time columns are also {@link #isInt}, but
   *  not vice-versa.
   *  @return true if this is a time column.  */
  public final boolean isTime   (int colId){ return type(colId)==T_TIME; }

  public final boolean isInt(int colId){return getRollups(colId)._isInt; }
  /** Size of compressed vector data. */
  public long byteSize(){
    long res = 0;
    for(int i = 0; i < numCols(); ++i)
      res += getRollups(i)._size;
    return res;
  }

  /** Vecs's mode
   * @return Vec's mode */
  public int mode(int colId) {
    if (!isCategorical(colId)) throw H2O.unimpl();
    long[] bins = bins(colId);
    return ArrayUtils.maxIndex(bins);
  }


  /** Default percentiles for approximate (single-pass) quantile computation (histogram-based). */
  public static final double PERCENTILES[] = {0.001,0.01,0.1,0.2,0.25,0.3,1.0/3.0,0.4,0.5,0.6,2.0/3.0,0.7,0.75,0.8,0.9,0.99,0.999};
  /** A simple and cheap histogram of the Vec, useful for getting a broad
   *  overview of the data.  Each bin is row-counts for the bin's range.  The
   *  bin's range is computed from {@link #base} and {@link #stride}.  The
   *  histogram is computed on first use and cached thereafter.
   *  @return A set of histogram bins, or null for String columns */
  public long[] bins(int colId) { return getRollups(colId,true)._bins; }

  public long[] lazy_bins(int colId) { return getRollups(colId)._bins; }

  // ======= Rollup Stats ======

  /** Vec's minimum value
   *  @return Vec's minimum value */
  public double min(int colId)  { return mins(colId)[0]; }
  /** Vec's 5 smallest values
   *  @return Vec's 5 smallest values */
  public double[] mins(int colId){ return getRollups(colId)._mins; }
  /** Vec's maximum value
   *  @return Vec's maximum value */
  public double max(int colId)  { return maxs(colId)[0]; }
  /** Vec's 5 largest values
   *  @return Vec's 5 largeest values */
  public double[] maxs(int colId){ return getRollups(colId)._maxs; }
  /** True if the column contains only a constant value and it is not full of NAs
   *  @return True if the column is constant */
  public final boolean isConst(int colId) { return min(colId) == max(colId); }
  /** True if the column contains only NAs
   *  @return True if the column contains only NAs */
  public final boolean isBad(int colId) { return naCnt(colId)==length(); }
  /** Vecs's mean
   *  @return Vec's mean */
  public double mean(int colId) {
    return getRollups(colId)._mean; }
  /** Vecs's standard deviation
   *  @return Vec's standard deviation */
  public double sigma(int colId){ return getRollups(colId)._sigma; }

  /** Count of missing elements
   *  @return Count of missing elements */
  public long  naCnt(int colId) { return getRollups(colId)._naCnt; }
  /** Count of non-zero elements
   *  @return Count of non-zero elements */
  public long  nzCnt(int colId) { return getRollups(colId)._nzCnt; }
  /** Count of positive infinities
   *  @return Count of positive infinities */
  public long  pinfs(int colId) { return getRollups(colId)._pinfs; }
  /** Count of negative infinities
   *  @return Count of negative infinities */
  public long  ninfs(int colId) { return getRollups(colId)._ninfs; }


  /** The {@code base} for a simple and cheap histogram of the Vec, useful
   *  for getting a broad overview of the data.  This returns the base of
   *  {@code bins()[0]}.
   *  @return the base of {@code bins()[0]} */
  public double base(int colId)      { return getRollups(colId).h_base(); }
  /** The {@code stride} for a a simple and cheap histogram of the Vec, useful
   *  for getting a broad overview of the data.  This returns the stride
   *  between any two bins.
   *  @return the stride between any two bins */
  public double stride(int colId)    { return  getRollups(colId).h_stride(); }

  /** A simple and cheap percentiles of the Vec, useful for getting a broad
   *  overview of the data.  The specific percentiles are take from {@link #PERCENTILES}.
   *  @return A set of percentiles */
  public double[] pctiles(int colId) { return  getRollups(colId)._pctiles;   }

  /** True if this is an categorical column.  All categorical columns are also
   *  {@link #isInt}, but not vice-versa.
   *  @return true if this is an categorical column.  */
  public final boolean isCategorical(int colId) {
    return type(colId)==T_CAT;
  }

  public final int cardinality(int colId) {
    String [] dom = domain(colId);
    return dom == null?-1:dom.length;
  }

  // Filled in lazily and racily... but all writers write the exact identical Key
  public Key rollupStatsKey() {
    if( _rollupStatsKey==null ) _rollupStatsKey=chunkKey(-2);
    return _rollupStatsKey;
  }

  /** A high-quality 64-bit checksum of the Vec's content, useful for
   *  establishing dataset identity.
   *  @return Checksum of the Vec's content  */
  protected final long checksum_impl(int colId) { return getRollups(colId)._checksum;}
  @Override public final long checksum_impl() {
    long res = checksum_impl(0);
    for(int i = 1; 1 < numCols(); ++i)
      res ^= checksum_impl(i);
    return res;
  }


  /** Begin writing into this Vec.  Immediately clears all the rollup stats
   *  ({@link #min}, {@link #max}, {@link #mean}, etc) since such values are
   *  not meaningful while the Vec is being actively modified.  Can be called
   *  repeatedly.  Per-chunk row-counts will not be changing, just row
   *  contents. */
  public abstract void preWriting(int... colIds);

  /** Stop writing into this Vec.  Rollup stats will again (lazily) be
   *  computed. */
  public abstract Futures postWrite( Futures fs );

  // ======= Key and Chunk Management ======

  /** Convert a row# to a chunk#.  For constant-sized chunks this is a little
   *  shift-and-add math.  For variable-sized chunks this is a binary search,
   *  with a sane API (JDK has an insane API).  Overridden by subclasses that
   *  compute chunks in an alternative way, such as file-backed Vecs. */
   public int elem2ChunkIdx(long i) {
    if( !(0 <= i && i < length()) ) throw new ArrayIndexOutOfBoundsException("0 <= "+i+" < "+length());
    long[] espc = espc();       // Preload
    int lo=0, hi = nChunks();
    while( lo < hi-1 ) {
      int mid = (hi+lo)>>>1;
      if( i < espc[mid] ) hi = mid;
      else                lo = mid;
    }
    while( espc[lo+1] == i ) lo++;
    return lo;
  }

  /** Get a Vec Key from Chunk Key, without loading the Chunk.
   *  @return the Vec Key for the Chunk Key */
  public static Key getVecKey( Key chk_key ) {
    assert chk_key._kb[0]==Key.CHK;
    byte [] bits = chk_key._kb.clone();
    bits[0] = Key.VEC;
    UnsafeUtils.set4(bits, 6, -1); // chunk#
    return Key.make(bits);
  }

  /** Get a Chunk Key from a chunk-index.  Basically the index-to-key map.
   *  @return Chunk Key from a chunk-index */
  public final Key chunkKey(int cidx) {
    return chunkKey(_key,cidx, 0);
  }

  /** Get a Chunk Key from a chunk-index and a Vec Key, without needing the
   *  actual Vec object.  Basically the index-to-key map.
   *  @return Chunk Key from a chunk-index and Vec Key */
  public static Key chunkKey(Key veckey, int cidx ) {
    byte [] bits = veckey._kb.clone();
    bits[0] = Key.CHK;
    UnsafeUtils.set4(bits, 6, cidx); // chunk#
    return Key.make(bits);
  }

  /** Get a Chunk Key from a chunk-index and a Vec Key, without needing the
   *  actual Vec object.  Basically the index-to-key map.
   *  @return Chunk Key from a chunk-index and Vec Key */
  public static Key chunkKey(Key veckey, int cidx, int vidx) {
    byte [] bits = veckey._kb.clone();
    bits[0] = Key.CHK;
    VectorGroup.setVecId(bits,vidx + VectorGroup.getVecId(bits));
    UnsafeUtils.set4(bits, 6, cidx); // chunk#
    return Key.make(bits);
  }

  public static void setChunkId(Key<AVec> key, int cidx) {
    UnsafeUtils.set4(key._kb, 6, cidx); // chunk#
  }


  /** Get a Chunk's Value by index.  Basically the index-to-key map, plus the
   *  {@code DKV.get()}.  Warning: this pulls the data locally; using this call
   *  on every Chunk index on the same node will probably trigger an OOM!  */
  public Value chunkIdx(int cidx) {
    Value val = DKV.get(chunkKey(cidx));
    assert checkMissing(cidx,val) : "Missing chunk " + chunkKey(cidx);
    return val;
  }


  private boolean checkMissing(int cidx, Value val) {
    if( val != null ) return true;
    Log.err("Error: Missing chunk " + cidx + " for " + _key);
    return false;
  }

  /** Make a new random Key that fits the requirements for a Vec key. 
   *  @return A new random Vec Key */
  public static Key<AVec> newKey(){return newKey(Key.make());}

  /** Internally used to help build Vec and Chunk Keys; public to help
   *  PersistNFS build file mappings.  Not intended as a public field. */
  public static final int KEY_PREFIX_LEN = 4+4+1+1;
  /** Make a new Key that fits the requirements for a Vec key, based on the
   *  passed-in key.  Used to make Vecs that back over e.g. disk files. */
  static Key<AVec> newKey(Key k) {
    byte [] kb = k._kb;
    byte [] bits = MemoryManager.malloc1(kb.length + KEY_PREFIX_LEN);
    bits[0] = Key.VEC;
    bits[1] = -1;         // Not homed
    UnsafeUtils.set4(bits,2,0);   // new group, so we're the first vector
    UnsafeUtils.set4(bits,6,-1);  // 0xFFFFFFFF in the chunk# area
    System.arraycopy(kb, 0, bits, 4 + 4 + 1 + 1, kb.length);
    return Key.make(bits);
  }

  /** Make a ESPC-group key.  */
  private static Key espcKey(Key key) { 
    byte [] bits = key._kb.clone();
    bits[0] = Key.GRP;
    UnsafeUtils.set4(bits, 2, -1);
    UnsafeUtils.set4(bits, 6, -2);
    return Key.make(bits);
  }

  /** Make a Vector-group key.  */
  protected final Key groupKey(){
    byte [] bits = _key._kb.clone();
    bits[0] = Key.GRP;
    UnsafeUtils.set4(bits, 2, -1);
    UnsafeUtils.set4(bits, 6, -1);
    return Key.make(bits);
  }

  /** Get the group this vector belongs to.  In case of a group with only one
   *  vector, the object actually does not exist in KV store.  This is the ONLY
   *  place VectorGroups are fetched.
   *  @return VectorGroup this vector belongs to */
  public final VectorGroup group() {
    Key gKey = groupKey();
    Value v = DKV.get(gKey);
    // if no group exists we have to create one
    return v==null ? new VectorGroup(gKey,1) : (VectorGroup)v.get();
  }

  /** The Chunk for a chunk#.  Warning: this pulls the data locally; using this
   *  call on every Chunk index on the same node will probably trigger an OOM!
   *  @return Chunk for a chunk# */
  public T chunkForChunkIdx(int cidx) {
    T c = chunkIdx(cidx).get();        // Chunk# to chunk data
    long cstart = c._start;             // Read once, since racily filled in
    int tcidx = c._cidx;
    AVec v = c._vec;
    if( cstart != -1 && v != null && tcidx == cidx)
      return c;                       // Already filled-in
    assert cstart == -1 || v == null || tcidx == -1; // Was not filled in (everybody racily writes the same start value)
    c._vec = this;             // Fields not filled in by unpacking from Value
    c._start = chunk2StartElem(cidx);          // Fields not filled in by unpacking from Value
    c._cidx = cidx;
    return c;
  }

  /** The Chunk for a row#.  Warning: this pulls the data locally; using this
   *  call on every Chunk index on the same node will probably trigger an OOM!
   *  @return Chunk for a row# */
  public final T chunkForRow(long i) { return chunkForChunkIdx(elem2ChunkIdx(i)); }

  /** True if two Vecs are equal.  Checks for equal-Keys only (so it is fast)
   *  and not equal-contents.
   *  @return True if two Vecs are equal */
  @Override public boolean equals( Object o ) {
    return o instanceof AVec && ((AVec)o)._key.equals(_key);
  }
  /** Vec's hashcode, which is just the Vec Key hashcode.
   *  @return Vec's hashcode */
  @Override public int hashCode() { return _key.hashCode(); }

  /** Remove associated Keys when this guy removes.  For Vecs, remove all
   *  associated Chunks.
   *  @return Passed in Futures for flow-coding  */
  @Override public Futures remove_impl( Futures fs ) {
    // Bulk dumb local remove - no JMM, no ordering, no safety.
    final int ncs = nChunks();
    new MRTask() {
      @Override public void setupLocal() { bulk_remove(_key,ncs); }
    }.doAllNodes();
    return fs;
  }
  // Bulk remove: removes LOCAL keys only, without regard to total visibility.
  // Must be run in parallel on all nodes to preserve semantics, completely
  // removing the Vec without any JMM communication.
  static void bulk_remove( Key vkey, int ncs ) {
    for( int i=0; i<ncs; i++ ) {
      Key kc = chunkKey(vkey,i);
      H2O.raw_remove(kc);
    }
    Key kr = chunkKey(vkey,-2); // Rollup Stats
    H2O.raw_remove(kr);
    H2O.raw_remove(vkey);
  }

  // ======= Whole Vec Transformations ======

  /** Make a Vec adapting this cal vector to the 'to' categorical Vec.  The adapted
   *  CategoricalWrappedVec has 'this' as it's masterVec, but returns results in the 'to'
   *  domain (or just past it, if 'this' has elements not appearing in the 'to'
   *  domain). */
  public CategoricalWrappedVec adaptTo( String[] domain ) {
    return new CategoricalWrappedVec(group().addVec(),_rowLayout,domain,new VecAry(this));
  }

  /** Class representing the group of vectors.
   *
   *  Vectors from the same group have same distribution of chunks among nodes.
   *  Each vector is member of exactly one group.  Default group of one vector
   *  is created for each vector.  Group of each vector can be retrieved by
   *  calling group() method;
   *  
   *  The expected mode of operation is that user wants to add new vectors
   *  matching the source.  E.g. parse creates several vectors (one for each
   *  column) which are all colocated and are colocated with the original
   *  bytevector.
   *  
   *  To do this, user should first ask for the set of keys for the new vectors
   *  by calling addVecs method on the target group.
   *  
   *  Vectors in the group will have the same keys except for the prefix which
   *  specifies index of the vector inside the group.  The only information the
   *  group object carries is its own key and the number of vectors it
   *  contains (deleted vectors still count).
   *  
   *  Because vectors (and chunks) share the same key-pattern with the group,
   *  default group with only one vector does not have to be actually created,
   *  it is implicit.
   *  
   *  @author tomasnykodym
   */
  public static class VectorGroup extends Keyed<VectorGroup> {
    /** The common shared vector group for very short vectors */
    public static final VectorGroup VG_LEN1 = new VectorGroup();
    // The number of Vec keys handed out by the this VectorGroup already.
    // Updated by overwriting in a TAtomic.
    final int _len;

    // New empty VectorGroup (no Vecs handed out)
    public VectorGroup() { super(init_key()); _len = 0; }

    static private Key init_key() { 
      byte[] bits = new byte[26];
      bits[0] = Key.GRP;
      bits[1] = -1;
      UnsafeUtils.set4(bits, 2, -1);
      UnsafeUtils.set4(bits, 6, -1);
      UUID uu = UUID.randomUUID();
      UnsafeUtils.set8(bits,10,uu.getLeastSignificantBits());
      UnsafeUtils.set8(bits,18,uu. getMostSignificantBits());
      return Key.make(bits);
    }

    // Clone an old vector group, setting a new len
    private VectorGroup(Key key, int newlen) { super(key); _len = newlen; }

    /** Returns Vec Key from Vec id#.  Does NOT allocate a Key id#
     *  @return Vec Key from Vec id# */
    public Key<AVec> vecKey(int vecId) {
      byte [] bits = _key._kb.clone();
      bits[0] = Key.VEC;
      UnsafeUtils.set4(bits,2,vecId);
      return Key.make(bits);
    }

    public VecAry makeCons(int rowLayout, int len, long cons) {
      throw H2O.unimpl();
    }

    public static int getVecId(byte [] bits) {return UnsafeUtils.get4(bits,2);}
    public static void setVecId(byte [] bits, int id) { UnsafeUtils.set4(bits,2,id);}

    /** Task to atomically add vectors into existing group.
     *  @author tomasnykodym   */
    private final static class AddVecs2GroupTsk extends TAtomic<VectorGroup> {
      final Key _key;
      int _n;          // INPUT: Keys to allocate; OUTPUT: start of run of keys
      private AddVecs2GroupTsk(Key key, int n){_key = key; _n = n;}
      @Override protected VectorGroup atomic(VectorGroup old) {
        int n = _n;             // how many
        // If the old group is missing, assume it is the default group-of-self
        // (having 1 ID already allocated for self), not a new group with
        // zero prior vectors.
        _n = old==null ? 1 : old._len; // start of allocated key run
        return new VectorGroup(_key, n+_n);
      }
    }

    /** Reserve a range of keys and return index of first new available key
     *  @return Vec id# of a range of Vec keys in this group */
    public int reserveKeys(final int n) {
      AddVecs2GroupTsk tsk = new AddVecs2GroupTsk(_key, n);
      tsk.invoke(_key);
      return tsk._n;
    }

    /** Gets the next n keys of this group.
     *  @param n number of keys to make
     *  @return arrays of unique keys belonging to this group.  */
    public Key<AVec>[] addVecs(final int n) {
      int nn = reserveKeys(n);
      Key<AVec>[] res = (Key<AVec>[])new Key[n];
      for( int i = 0; i < n; ++i )
        res[i] = vecKey(i + nn);
      return res;
    }
    /** Shortcut for {@code addVecs(1)}.
     *  @see #addVecs(int)
     *  @return a new Vec Key in this group   */
    public Key<AVec> addVec() { return addVecs(1)[0]; }

    // -------------------------------------------------
    static boolean sameGroup(AVec v1, AVec v2) {
      byte[] bits1 = v1._key._kb;
      byte[] bits2 = v2._key._kb;
      if( bits1.length != bits2.length )
        return false;
      int res  = 0;
      for( int i = KEY_PREFIX_LEN; i < bits1.length; i++ )
        res |= bits1[i] ^ bits2[i];
      return res == 0;
    }

    /** Pretty print the VectorGroup
     *  @return String representation of a VectorGroup */
    @Override public String toString() {
      return "VecGrp "+_key.toString()+", next free="+_len;
    }
    // Return current VectorGroup index; used for tests
    public int len() { return _len; }

    /** True if two VectorGroups are equal 
     *  @return True if two VectorGroups are equal */
    @Override public boolean equals( Object o ) {
      return o instanceof VectorGroup && ((VectorGroup)o)._key.equals(_key);
    }
    /** VectorGroups's hashcode
     *  @return VectorGroups's hashcode */
    @Override public int hashCode() { return _key.hashCode(); }
    @Override protected long checksum_impl() { throw H2O.fail(); }
    // Fail to remove a VectorGroup unless you also remove all related Vecs,
    // Chunks, Rollups (and any Frame that uses them), etc.
    @Override protected Futures remove_impl( Futures fs ) { throw H2O.fail(); }
    /** Write out K/V pairs */
  }

  // ---------------------------------------
  // Unify ESPC arrays on the local node as much as possible.  This is a
  // similar problem to what TypeMap solves: sometimes I have a rowLayout index
  // and want the matching ESPC array, and sometimes I have the ESPC array and
  // want an index.  The operation is frequent and must be cached locally, but
  // must be globally consistent.  Hence a "miss" in the local cache means we
  // need to fetch from global state, and sometimes update the global state
  // before fetching.

  public static class ESPC extends Keyed<ESPC> {
    static private NonBlockingHashMap<Key,ESPC> ESPCS = new NonBlockingHashMap<>();

    // Array of Row Layouts (Element Start Per Chunk) ever seen by this
    // VectorGroup.  Shared here, amongst all Vecs using the same row layout
    // (instead of each of 1000's of Vecs having a copy, each of which is
    // nChunks long - could be millions).
    //
    // Element-start per chunk.  Always zero for chunk 0.  One more entry than
    // chunks, so the last entry is the total number of rows.
    public final long[][] _espcs;

    private ESPC(Key key, long[][] espcs) { super(key); _espcs = espcs;}
    // Fetch from the local cache
    private static ESPC getLocal( Key kespc ) {
      ESPC local = ESPCS.get(kespc);
      if( local != null ) return local;
      ESPCS.putIfAbsent(kespc,new ESPC(kespc,new long[0][])); // Racey, not sure if new or old is returned
      return ESPCS.get(kespc);
    }

    // Fetch from remote, and unify as needed
    private static ESPC getRemote( ESPC local, Key kespc ) {
      final ESPC remote = DKV.getGet(kespc);
      if( remote == null || remote == local ) return local; // No change

      // Something New?  If so, we need to unify the sharable arrays with a
      // "smashing merge".  Every time a remote instance of a ESPC is updated
      // (to add new ESPC layouts), and it is pulled locally, the new copy
      // brings with it a complete copy of all ESPC arrays - most of which
      // already exist locally in the old ESPC instance.  Since these arrays
      // are immutable and monotonically growing, it's safe (and much more
      // efficient!) to make the new copy use the old copies arrays where
      // possible.
      long[][] local_espcs = local ._espcs;
      long[][] remote_espcs= remote._espcs;
      // Spin attempting to move the larger remote value into the local cache
      while( true ) {
        // Is the remote stale, and the local value already larger?  Can happen
        // if the local is racily updated by another thread, after this thread
        // reads the remote value (which then gets invalidated, and updated to
        // a new larger value).
        if( local_espcs.length >= remote_espcs.length ) return local;
        // Use my (local, older, more heavily shared) ESPCs where possible.
        // I.e., the standard remote read will create new copies of all ESPC
        // arrays, but the *local* copies are heavily shared.  All copies are
        // equal, but using the same shared copies cuts down on copies.
        System.arraycopy(local._espcs, 0, remote._espcs, 0, local._espcs.length);
        // Here 'remote' is larger than 'local' (but with a shared common prefix).
        // Attempt to update local cache with the larger value
        ESPC res = ESPCS.putIfMatch(kespc,remote,local);  // Update local copy with larger
        // if res==local, then update succeeded, table has 'remote' (the larger object).
        if( res == local ) return remote;
        // if res!=local, then update failed, and returned 'res' is probably
        // larger than either remote or local
        local = res;
        local_espcs = res._espcs;
        assert remote_espcs== remote._espcs; // unchanging final field
      }
    }

    /** Get the ESPC for a Vec.  Called once per new construction or read_impl.  */
    public static long[] espc( AVec v ) {
      final int r = v._rowLayout;
      if( r == -1 ) return null; // Never was any row layout
      // Check the local cache
      final Key kespc = espcKey(v._key);
      ESPC local = getLocal(kespc);
      if( r < local._espcs.length ) return local._espcs[r];
      // Now try to refresh the local cache from the remote cache
      final ESPC remote = getRemote( local, kespc);
      if( r < remote._espcs.length ) return remote._espcs[r];
      throw H2O.fail("Vec "+v._key+" asked for layout "+r+", but only "+remote._espcs.length+" layouts defined");
    }

    // Check for a prior matching ESPC
    private static int find_espc( long[] espc, long[][] espcs ) {
      // Check for a local pointer-hit first:
      for( int i=0; i<espcs.length; i++ ) if( espc==espcs[i] ) return i;
      // Check for a local deep equals next:
      for( int i=0; i<espcs.length; i++ )
        if( espc.length==espcs[i].length && Arrays.equals(espc,espcs[i]) ) 
          return i;
      return -1;                // No match
    }

    /** Get the shared ESPC index for this layout.  Will return an old layout
     *  if one matches, otherwise will atomically update the ESPC to set
     *  a new layout.  The expectation is that new layouts are rare: once per
     *  parse, and perhaps from filtering MRTasks, or data-shuffling.  */
    public static int rowLayout( Key key, final long[] espc ) {
      Key kespc = espcKey(key);
      ESPC local = getLocal(kespc);
      int idx = find_espc(espc,local._espcs);
      if( idx != -1 ) return idx;

      // See if the ESPC is in the LOCAL DKV - if not it might have been
      // invalidated, and a refetch might get a new larger ESPC with the
      // desired layout.
      if( !H2O.containsKey(kespc) ) {
        local = getRemote(local,kespc);      // Fetch remote, merge as needed
        idx = find_espc(espc, local._espcs); // Retry
        if( idx != -1 ) return idx;
      }
      
      // Send the ESPC over to the ESPC master, and request it get
      // inserted.
      new TAtomic<ESPC>() {
        @Override public ESPC atomic( ESPC old ) {
          if( old == null ) return new ESPC(_key,new long[][]{espc});
          long[][] espcs = old._espcs;
          int idx = find_espc(espc,espcs);
          if( idx != -1 ) return null; // Abort transaction, idx exists; client needs to refresh
          int len = espcs.length;
          espcs = Arrays.copyOf(espcs,len+1);
          espcs[len] = espc;    // Insert into array
          return new ESPC(_key,espcs);
        }
      }.invoke(kespc);
      // Refetch from master, try again
      ESPC reloaded = getRemote(local,kespc); // Fetch remote, merge as needed
      idx = find_espc(espc,reloaded._espcs);  // Retry
      assert idx != -1;                       // Must work now (or else the install failed!)
      return idx;
    }
    public static void clear() { ESPCS.clear(); }
    @Override protected long checksum_impl() { throw H2O.fail(); }
  }

  /** Always makes a copy of the given vector which shares the same group as
   *  this Vec.  This can be expensive operation since it can force copy of
   *  data among nodes.
   *
   * @param vec vector which is intended to be copied
   * @return a copy of vec which shared the same {@link VectorGroup} with this vector
   */
  public final AVec align(final AVec vec) {
    return new VecAry(this).makeCompatible(new VecAry(vec),true).getAVecRaw(0);
  }

  public abstract AVec doCopy();

}
