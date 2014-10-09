package water.fvec;

import water.*;
import water.nbhm.NonBlockingHashMapLong;
import water.parser.Enum;
import water.parser.ParseTime;
import water.parser.ValueString;
import water.util.ArrayUtils;
import water.util.PrettyPrint;
import water.util.UnsafeUtils;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.Future;

/** A distributed vector/array/column of uniform data.
 *  
 *  <p>A distributed vector has a count of elements, an element-to-chunk
 *  mapping, a Java-like type (mostly determines rounding on store and
 *  display), and functions to directly load elements without further
 *  indirections.  The data is compressed, or backed by disk or both.
 *
 *  <p>A Vec is a collection of {@link Chunk}s, each of which holds between 1e3
 *  and 1e6 elements.  Operations on a Chunk are intended to be
 *  single-threaded; operations on a Vec are intended to be parallel and
 *  distributed on Chunk granularities, with each Chunk being manipulated by a
 *  seperate CPU.  The standard Map/Reduce ({@link MRTask}) paradigm handles
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
 *   <p>The main API is {@link #at}, {@link #set}, and {@link #isNA}:<br>
 *<pre>
 *   double  at  ( long row );  // Returns the value expressed as a double.  NaN if missing.
 *   long    at8 ( long row );  // Returns the value expressed as a long.  Throws if missing.
 *   boolean isNA( long row );  // True if the value is missing.
 *   set( long row, double d ); // Stores a double; NaN will be treated as missing.
 *   set( long row, long l );   // Stores a long; throws if l exceeds what fits in a double &amp; any floats are ever set.
 *   setNA( long row );         // Sets the value as missing.
 *</pre>
 *
 *  <p>Vecs have a loosely enforced <em>type</em>: one of numeric, {@link UUID}
 *  or {@link String}.  Numeric types are further broken down into integral
 *  ({@code long}) and real ({@code double}) types.  The {@code enum} type is
 *  an integral type, with a String mapping side-array.  Most of the math
 *  algorithms will treat enums as small dense integers, and most enum
 *  printouts will use the String mapping.  Time is another special integral
 *  type: it is represented as milliseconds since the unix epoch, and is mostly
 *  treated as an integral type when doing math but it has special time-based
 *  printout formating.  All types support the notion of a missing element; for
 *  real types this is always NaN.  It is an error to attempt to fetch a
 *  missing integral type, and {@link #isNA} must be called first.  Integral
 *  types are losslessly compressed.  Real types may lose 1 or 2 ULPS due to
 *  compression.
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
 *  <p>Vecs have lazily computed {@link RollupStats} object and Key.  The
 *  RollupStats give fast access to the common metrics: min, max, mean, stddev,
 *  the count of missing elements and non-zeros.  They are cleared if the Vec
 *  is modified, and lazily recomputed after the modified Vec is closed.
 *  Clearing the RollupStats cache is fairly expensive for individual {@link
 *  #set} calls but is easy to amortize over a large count of writes; i.e.,
 *  batch writing is efficient.  This is normally handled by the MRTask
 *  framework; the {@link Writer} framework allows <em>single-threaded</em>
 *  efficient batch writing for smaller Vecs.
 *
 *  <p>Vecs have a {@link Vec.VectorGroup}.  Vecs in the same VectorGroup have the
 *  same Chunk and row alignment - that is, Chunks with the same index are
 *  homed to the same Node and have the same count of rows-per-Chunk.  {@link
 *  Frame}s are only composed of Vecs of the same VectorGroup (or very small
 *  Vecs) guaranteeing that all elements of each row are homed to the same Node
 *  and set of Chunks - such that a simple {@code for} loop over a set of
 *  Chunks all operates locally.  See the example in the {@link Chunk} class.
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
 *  Group Key layout: Key.GRP  -1     -1       -1     normal Key bytes; often e.g. a function of original file name
 *  RollupStats Key : Key.CHK  -1   vec#grp    -2     normal Key bytes; often e.g. a function of original file name
 * </pre>
 *
 * @author Cliff Click
 */
public class Vec extends Keyed {
  /** Log-2 of Chunk size. */
  static final int LOG_CHK = 20/*1Meg*/+2/*4Meg*/;
  /** Default Chunk size in bytes, useful when breaking up large arrays into
   *  "bite-sized" chunks.  Bigger increases batch sizes, lowers overhead
   *  costs, lower increases fine-grained parallelism. */
  public static final int CHUNK_SZ = 1 << LOG_CHK;

  /** Element-start per chunk.  Always zero for chunk 0.  One more entry than
   *  chunks, so the last entry is the total number of rows.  This field is
   *  dead/ignored in subclasses that are guaranteed to have fixed-sized chunks
   *  such as file-backed Vecs. */
  final long[] _espc;

  private String [] _domain;
  /** Returns the enum toString mapping array, or null if not an Enum column.
   *  Not a defensive clone (to expensive to clone; coding error to change the
   *  contents).
   *  @return the enum / factor / categorical mapping array, or null if not a Enum column */
  public final String[] domain() { return _domain; }
  /** Returns the {@code i}th factor for this enum column.
   *  @return The {@code i}th factor */
  public final String factor( long i ) { return _domain[(int)i]; }
  /** Set the Enum/factor/categorical names.  No range-checking on the actual
   *  underlying numeric domain; user is responsible for maintaining a mapping
   *  which is coherent with the Vec contents. */
  public final void setDomain(String[] domain) { _domain = domain; }
  /** Returns cardinality for enum domain or -1 for other types. */
  public final int cardinality() { return isEnum() ? _domain.length : -1; }

  // Vec internal type
  static final byte T_BAD  =  0; // No none-NA rows (triple negative! all NAs or zero rows)
  static final byte T_UUID =  1; // UUID
  static final byte T_STR  =  2; // String
  static final byte T_NUM  =  3; // Numeric, but not enum or time
  static final byte T_ENUM =  4; // Integer, with a enum/factor String mapping
  static final byte T_TIME =  5; // Long msec since the Unix Epoch - with a variety of display/parse options
  static final byte T_TIMELAST= (byte)(T_TIME+ParseTime.TIME_PARSE.length);
  byte _type;                   // Vec Type

  /** True if this is an Enum column.  All enum columns are also {@link #isInt}, but
   *  not vice-versa.
   *  @return true if this is an Enum column.  */
  public final boolean isEnum   (){ return _type==T_ENUM; }
  /** True if this is a UUID column.  
   *  @return true if this is a UUID column.  */
  public final boolean isUUID   (){ return _type==T_UUID; }
  /** True if this is a String column.  
   *  @return true if this is a String column.  */
  public final boolean isString (){ return _type==T_STR; }
  /** True if this is a numeric column, excluding enum and time types.
   *  @return true if this is a numeric column, excluding enum and time types  */
  public final boolean isNumeric(){ return _type==T_NUM; }
  /** True if this is a time column.  All time columns are also {@link #isInt}, but
   *  not vice-versa.
   *  @return true if this is a time column.  */
  public final boolean isTime   (){ return _type>=T_TIME && _type<T_TIMELAST; }
  final byte timeMode(){ assert isTime(); return (byte)(_type-T_TIME); }
  /** Time formatting string.
   *  @return Time formatting string */
  public final String timeParse(){ return ParseTime.TIME_PARSE[timeMode()]; }


  /** Build a numeric-type Vec; the caller understands Chunk layout (via the
   *  {@code espc} array). */
  Vec( Key key, long espc[]) { this(key, espc, null, T_NUM); }

  /** Build a numeric-type or enum-type Vec; the caller understands Chunk
   *  layout (via the {@code espc} array); enum Vecs need to pass the
   *  domain. */
  Vec( Key key, long espc[], String[] domain) { this(key,espc,domain, (domain==null?T_NUM:T_ENUM)); }

  /** Main default constructor; the caller understands Chunk layout (via the
   *  {@code espc} array), plus enum/factor the {@code domain} (or null for
   *  non-enums), and the Vec type. */
  Vec( Key key, long espc[], String[] domain, byte type ) {
    super(key);
    assert key._kb[0]==Key.VEC;
    assert domain==null || type==T_ENUM;
    assert T_BAD <= type && type < T_TIMELAST; // Note that T_BAD is allowed for all-NA Vecs
    _type = type;
    _espc = espc;
    _domain = domain;
  }

  private Vec( Key key, Vec v ) { this(key, v._espc); assert group()==v.group(); }


  // ======= Create zero/constant Vecs ======

  /** Make a new zero-filled vector with the given row count. 
   *  @return New zero-filled vector with the given row count. */
  public static Vec makeZero( long len ) { return makeCon(0L,len); }

  /** Make a new constant vector with the given row count. 
   *  @return New constant vector with the given row count. */
  public static Vec makeCon(double x, long len) {
    int nchunks = (int)Math.max(1,(len>>LOG_CHK)-1);
    long[] espc = new long[nchunks+1];
    for( int i=0; i<nchunks; i++ )
      espc[i] = ((long)i)<<LOG_CHK;
    espc[nchunks] = len;
    return makeCon(x,VectorGroup.VG_LEN1,espc);
  }

  /** Make a new vector with the same size and data layout as the current one,
   *  and initialized to zero.
   *  @return A new vector with the same size and data layout as the current one,
   *  and initialized to zero.  */
  public Vec makeZero() { return makeCon(0, null, group(), _espc); }

  /** A new vector with the same size and data layout as the current one, and
   *  initialized to zero, with the given enum domain.
   *  @return A new vector with the same size and data layout as the current
   *  one, and initialized to zero, with the given enum domain. */
  public Vec makeZero(String[] domain) { return makeCon(0, domain, group(), _espc); }

  private static Vec makeCon( final long l, String[] domain, VectorGroup group, long[] espc ) {
    final int nchunks = espc.length-1;
    final Vec v0 = new Vec(group.addVec(), espc, domain);
    new MRTask() {              // Body of all zero chunks
      @Override protected void setupLocal() {
        for( int i=0; i<nchunks; i++ ) {
          Key k = v0.chunkKey(i);
          if( k.home() ) DKV.put(k,new C0LChunk(l,v0.chunkLen(i)),_fs);
        }
      }
    }.doAllNodes();
    DKV.put(v0._key,v0);        // Header last
    return v0;
  }

  /** Make a new vector with the same size and data layout as the current one,
   *  and initialized to the given constant value.
   *  @return A new vector with the same size and data layout as the current one,
   *  and initialized to the given constant value.  */
  public Vec makeCon( final double d ) { return makeCon(d,group(),_espc); }

  private static Vec makeCon( final double d, VectorGroup group, long[] espc ) {
    if( (long)d==d ) return makeCon((long)d, null, group, espc);
    final int nchunks = espc.length-1;
    final Vec v0 = new Vec(group.addVec(), espc, null, T_NUM);
    new MRTask() {              // Body of all zero chunks
      @Override protected void setupLocal() {
        for( int i=0; i<nchunks; i++ ) {
          Key k = v0.chunkKey(i);
          if( k.home() ) DKV.put(k,new C0DChunk(d,v0.chunkLen(i)),_fs);
        }
      }
    }.doAllNodes();
    DKV.put(v0._key,v0);        // Header last
    return v0;
  }

  
  /** Make a collection of new vectors with the same size and data layout as
   *  the current one, and initialized to zero.
   *  @return A collection of new vectors with the same size and data layout as
   *  the current one, and initialized to zero.  */
  public Vec[] makeZeros(int n) { return makeCons(n,0L,null,null); }

  // Make a bunch of compatible zero Vectors
  Vec[] makeCons(int n, final long l, String[][] domains, byte[] types) {
    final int nchunks = nChunks();
    Key[] keys = group().addVecs(n);
    final Vec[] vs = new Vec[keys.length];
    for(int i = 0; i < vs.length; ++i)
      vs[i] = new Vec(keys[i],_espc, 
                      domains== null ? null : domains[i], 
                      types  == null ? T_NUM: types[i]);
    new MRTask() {
      @Override protected void setupLocal() {
        for (Vec v1 : vs) {
          for (int i = 0; i < nchunks; i++) {
            Key k = v1.chunkKey(i);
            if (k.home()) DKV.put(k, new C0LChunk(l, chunkLen(i)), _fs);
          }
        }
        for( Vec v : vs ) if( v._key.home() ) DKV.put(v._key,v,_fs);
      }
    }.doAllNodes();
    return vs;
  }

  /** Make a new vector with the same size and data layout as the current one,
   *  and initialized to increasing integers, starting with 1.
   *  @return A new vector with the same size and data layout as the current
   *  one, and initialized to increasing integers, starting with 1. */
  public static Vec makeSeq( long len) {
    return new MRTask() {
      @Override public void map(Chunk[] cs) {
        for( Chunk c : cs )
          for( int r = 0; r < c._len; r++ )
            c.set0(r, r+1+c._start);
      }
    }.doAll(makeZero(len))._fr.vecs()[0];
  }

  /** Make a new vector with the same size and data layout as the current one,
   *  and initialized to increasing integers mod {@code repeat}, starting with 1.
   *  @return A new vector with the same size and data layout as the current
   *  one, and initialized to increasing integers mod {@code repeat}, starting
   *  with 1. */
  public static Vec makeRepSeq( long len, final long repeat ) {
    return new MRTask() {
      @Override public void map(Chunk[] cs) {
        for( Chunk c : cs )
          for( int r = 0; r < c._len; r++ )
            c.set0(r, (r+1+c._start) % repeat);
      }
    }.doAll(makeZero(len))._fr.vecs()[0];
  }


  /** Number of elements in the vector; returned as a {@code long} instead of
   *  an {@code int} because Vecs support more than 2^32 elements. Overridden
   *  by subclasses that compute length in an alternative way, such as
   *  file-backed Vecs.
   *  @return Number of elements in the vector */
  public long length() { return _espc[_espc.length-1]; }

  /** Number of chunks.  Overridden by subclasses that compute chunks in an
   *  alternative way, such as file-backed Vecs. */
  public int nChunks() { return _espc.length-1; }

  /** Convert a chunk-index into a starting row #.  For constant-sized chunks
   *  this is a little shift-and-add math.  For variable-sized chunks this is a
   *  table lookup. */
  long chunk2StartElem( int cidx ) { return _espc[cidx]; }

  /** Number of rows in chunk. Does not fetch chunk content. */
  public int chunkLen( int cidx ) { return (int) (_espc[cidx + 1] - _espc[cidx]); }

  /** Check that row-layouts are compatible. */
  boolean checkCompatible( Vec v ) {
    // Groups are equal?  Then only check length
    if( group().equals(v.group()) ) return length()==v.length();
    // Otherwise actual layout has to be the same, and size "small enough"
    // to not worry about data-replication when things are not homed the same.
    // The layout test includes an exactly-equals-length check.
    return Arrays.equals(_espc,v._espc) && length() < 1e5;
  }

  /** Is the column constant.
   * <p>Returns true if the column contains only constant values and it is not full of NAs.</p> */
  public final boolean isConst() { return min() == max(); }
  /** Is the column bad.
   * <p>Returns true if the column is full of NAs.</p>
   */
  private boolean isBad() { return naCnt() == length(); }

  /** Default read/write behavior for Vecs.  File-backed Vecs are read-only. */
  protected boolean readable() { return true ; }
  /** Default read/write behavior for Vecs.  AppendableVecs are write-only. */
  protected boolean writable() { return true; }

  /** RollupStats: min/max/mean of this Vec lazily computed.  */
  /** Return column min - lazily computed as needed. */
  public double min()  { return mins()[0]; }
  public double[]mins(){ return rollupStats()._mins; }
  /** Return column max - lazily computed as needed. */
  public double max()  { return maxs()[0]; }
  public double[]maxs(){ return rollupStats()._maxs; }
  /** Return column mean - lazily computed as needed. */
  public double mean() { return rollupStats()._mean; }
  /** Return column standard deviation - lazily computed as needed. */
  public double sigma(){ return rollupStats()._sigma; }
  /** Return column missing-element-count - lazily computed as needed. */
  public long  naCnt() { return rollupStats()._naCnt; }
  /** Return column zero-element-count - lazily computed as needed. */
  public long  nzCnt() { return rollupStats()._nzCnt; }
  /** Positive and negative infinity counts */
  public long  pinfs() { return rollupStats()._pinfs; }
  public long  ninfs() { return rollupStats()._ninfs; }
  /** {@link #isInt} is a property of numeric Vecs and not a type; this
   *  property can be changed by assigning non-integer values into the Vec (or
   *  restored by overwriting non-integer values with integers).  This is a
   *  strong type for {@link #isEnum} and {@link #isTime} Vecs.
   *  @return true if the Vec is all integers */
  public boolean isInt(){return rollupStats()._isInt; }
  /** Size of compressed vector data. */
  long byteSize(){return rollupStats()._size; }

  /** Default Histogram bins. */
  public static final double PERCENTILES[] = {0.01,0.10,0.25,1.0/3.0,0.50,2.0/3.0,0.75,0.90,0.99};
  /**  Computed on-demand to 1st call to these methods.
   *  bins[] are row-counts in each bin
   *  base - start of bin 0
   *  stride - relative start of next bin
   */
  public long[] bins()  { RollupStats.computeHisto(this); return rollupStats()._bins; }
  public double base()  { RollupStats.computeHisto(this); return rollupStats().h_base(); }
  public double stride(){ RollupStats.computeHisto(this); return rollupStats().h_stride();}
  public double[] pctiles(){RollupStats.computeHisto(this); return rollupStats()._pctiles;}
  /** Optimistically return the histogram bins, or null if not computed */
  public long[] lazy_bins() { return rollupStats()._bins; }

  /** Compute the roll-up stats as-needed */
  private RollupStats rollupStats() { return RollupStats.get(this); }

  /** Begin execution of RollupStats; useful when launching a bunch of them in
   *  parallel right after a parse. */
  public Future startRollupStats() { return RollupStats.start(this); }

  private long _last_write_timestamp = System.currentTimeMillis();
  private long _checksum_timestamp = -1;
  private long _checksum = 0;

  /** A private class to compute the rollup stats */
  private static class ChecksummerTask extends MRTask<ChecksummerTask> {
    public long checksum = 0;
    public long getChecksum() { return checksum; }

    @Override public void map( Chunk c ) {
      long _start = c._start;

      for( int i=0; i<c._len; i++ ) {
        long l = 81985529216486895L; // 0x0123456789ABCDEF
        if (! c.isNA0(i)) {
          if (c instanceof C16Chunk) {
            l = c.at16l0(i);
            l ^= (37 * c.at16h0(i));
          } else {
            l = c.at80(i);
          }
        }
        long global_row = _start + i;

        checksum ^= (17 * global_row);
        checksum ^= (23 * l);
      }
    } // map()

    @Override public void reduce( ChecksummerTask that ) {
      this.checksum ^= that.checksum;
    }
  } // class ChecksummerTask

  public long checksum() {
    final long now = _last_write_timestamp;  // TODO: someone can be writing while we're checksuming. . .
    if (-1 != now && now == _checksum_timestamp) {
      return _checksum;
    }
    final long checksum = new ChecksummerTask().doAll(this).getChecksum();

    new TAtomic<Vec>() {
      @Override public Vec atomic(Vec v) {
          if (v != null) {
              v._checksum = checksum;
              v._checksum_timestamp = now;
          } return v;
      }
    }.invoke(_key);

    this._checksum = checksum;
    this._checksum_timestamp = now;

    return checksum;
  }


  /** Writing into this Vector from *some* chunk.  Immediately clear all caches
   *  (_min, _max, _mean, etc).  Can be called repeatedly from one or all
   *  chunks.  Per-chunk row-counts will not be changing, just row contents and
   *  caches of row contents. */
  public void preWriting( ) {
    if( !writable() ) throw new IllegalArgumentException("Vector not writable");
    final Key rskey = rollupStatsKey();
    Value val = DKV.get(rskey);
    if( val != null ) {
      RollupStats rs = val.get(RollupStats.class);
      if( rs.isMutating() ) return; // Vector already locked against rollups
    }
    // Set rollups to "vector isMutating" atomically.
    new TAtomic<RollupStats>() {
      @Override protected RollupStats atomic(RollupStats rs) {
        return rs != null && rs.isMutating() ? null : RollupStats.makeMutating(rskey);
      }
    }.invoke(rskey);
  }

  /** Stop writing into this Vec.  Rollup stats will again (lazily) be computed. */
  public Futures postWrite( Futures fs ) {
    // TODO: update _last_write_timestamp!

    // Get the latest rollups *directly* (do not compute them!).
    final Key rskey = rollupStatsKey();
    Value val = DKV.get(rollupStatsKey());
    if( val != null ) {
      RollupStats rs = val.get(RollupStats.class);
      if( rs.isMutating() )  // Vector was mutating, is now allowed for rollups
        DKV.remove(rskey,fs);// Removing will cause them to be rebuilt, on demand
    }
    return fs;                  // Flow-coding
  }


  /** Convert a row# to a chunk#.  For constant-sized chunks this is a little
   *  shift-and-add math.  For variable-sized chunks this is a binary search,
   *  with a sane API (JDK has an insane API).  Overridden by subclasses that
   *  compute chunks in an alternative way, such as file-backed Vecs. */
  int elem2ChunkIdx( long i ) {
    assert 0 <= i && i < length() : "0 <= "+i+" < "+length();
    int lo=0, hi = nChunks();
    while( lo < hi-1 ) {
      int mid = (hi+lo)>>>1;
      if( i < _espc[mid] ) hi = mid;
      else                 lo = mid;
    }
    while( _espc[lo+1] == i ) lo++;
    return lo;
  }

  /** Get a Vec Key from Chunk Key, without loading the Chunk */
  public static Key getVecKey( Key key ) {
    assert key._kb[0]==Key.CHK;
    byte [] bits = key._kb.clone();
    bits[0] = Key.VEC;
    UnsafeUtils.set4(bits, 6, -1); // chunk#
    return Key.make(bits);
  }

  /** Get a Chunk Key from a chunk-index.  Basically the index-to-key map. */
  public Key chunkKey(int cidx ) { return chunkKey(_key,cidx); }
  static public Key chunkKey(Key veckey, int cidx ) {
    byte [] bits = veckey._kb.clone();
    bits[0] = Key.CHK;
    UnsafeUtils.set4(bits,6,cidx); // chunk#
    return Key.make(bits);
  }
  Key rollupStatsKey() { return chunkKey(-2); }

  /** Get a Chunk's Value by index.  Basically the index-to-key map,
   *  plus the {@code DKV.get()}.  Warning: this pulls the data locally;
   *  using this call on every Chunk index on the same node will
   *  probably trigger an OOM!  */
  protected Value chunkIdx( int cidx ) {
    Value val = DKV.get(chunkKey(cidx));
    assert checkMissing(cidx,val);
    return val;
  }

  protected boolean checkMissing(int cidx, Value val) {
    if( val != null ) return true;
    System.out.println("Error: Missing chunk "+cidx+" for "+_key);
    return false;
  }

  /** Return the next Chunk, or null if at end.  Mostly useful for parsers or
   *  optimized stencil calculations that want to "roll off the end" of a
   *  Chunk, but in a highly optimized way. */
  Chunk nextChunk( Chunk prior ) {
    int cidx = elem2ChunkIdx(prior._start)+1;
    return cidx < nChunks() ? chunkForChunkIdx(cidx) : null;
  }

  /** Make a new random Key that fits the requirements for a Vec key. */
  public static Key newKey(){return newKey(Key.make());}

  /** Internally used to help build Vec and Chunk Keys; public to help
   * PersistNFS build file mappings.  Not intended as a public field. */
  public static final int KEY_PREFIX_LEN = 4+4+1+1;
  /** Make a new Key that fits the requirements for a Vec key, based on the
   *  passed-in key.  Used to make Vecs that back over e.g. disk files. */
  static Key newKey(Key k) {
    byte [] kb = k._kb;
    byte [] bits = MemoryManager.malloc1(kb.length+KEY_PREFIX_LEN);
    bits[0] = Key.VEC;
    bits[1] = -1;         // Not homed
    UnsafeUtils.set4(bits,2,0);   // new group, so we're the first vector
    UnsafeUtils.set4(bits,6,-1);  // 0xFFFFFFFF in the chunk# area
    System.arraycopy(kb, 0, bits, 4+4+1+1, kb.length);
    return Key.make(bits);
  }

  /** Make a Vector-group key.  */
  private Key groupKey(){
    byte [] bits = _key._kb.clone();
    bits[0] = Key.GRP;
    UnsafeUtils.set4(bits, 2, -1);
    UnsafeUtils.set4(bits, 6, -1);
    return Key.make(bits);
  }
  /**
   * Get the group this vector belongs to.
   * In case of a group with only one vector, the object actually does not exist in KV store.
   *
   * @return VectorGroup this vector belongs to.
   */
  public final VectorGroup group() {
    Key gKey = groupKey();
    Value v = DKV.get(gKey);
    // if no group exists we have to create one
    return v==null ? new VectorGroup(gKey,1) : (VectorGroup)v.get();
  }

  /** The Chunk for a chunk#.  Warning: this loads the data locally!  */
  public Chunk chunkForChunkIdx(int cidx) {
    long start = chunk2StartElem(cidx); // Chunk# to chunk starting element#
    Value dvec = chunkIdx(cidx);        // Chunk# to chunk data
    Chunk c = dvec.get();               // Chunk data to compression wrapper
    long cstart = c._start;             // Read once, since racily filled in
    Vec v = c._vec;
    if( cstart == start && v != null) return c;     // Already filled-in
    assert cstart == -1 || v == null;       // Was not filled in (everybody racily writes the same start value)
    c._vec = this;             // Fields not filled in by unpacking from Value
    c._start = start;          // Fields not filled in by unpacking from Value
    return c;
  }
  /** The Chunk for a row#.  Warning: this loads the data locally!  */
  private Chunk chunkForRow_impl(long i) { return chunkForChunkIdx(elem2ChunkIdx(i)); }

  // Cache of last Chunk accessed via at/set api
  transient Chunk _cache;
  /** The Chunk for a row#.  Warning: this loads the data locally!  */
  public final Chunk chunkForRow(long i) {
    Chunk c = _cache;
    return (c != null && c.chk2()==null && c._start <= i && i < c._start+ c._len) ? c : (_cache = chunkForRow_impl(i));
  }
  /** Fetch element the slow way, as a long.  Floating point values are
   *  silently rounded to an integer.  Throws if the value is missing. */
  public final long  at8( long i ) { return chunkForRow(i).at8(i); }
  /** Fetch element the slow way, as a double.  Missing values are
   *  returned as Double.NaN instead of throwing. */
  public final double at( long i ) { return chunkForRow(i).at(i); }
  /** Fetch the missing-status the slow way. */
  public final boolean isNA(long row){ return chunkForRow(row).isNA(row); }

  /** Fetch element the slow way, as a long.  Throws if the value is missing or not a UUID. */
  public final long  at16l( long i ) { return chunkForRow(i).at16l(i); }
  public final long  at16h( long i ) { return chunkForRow(i).at16h(i); }

  public final ValueString atStr( ValueString vstr, long i ) { return chunkForRow(i).atStr(vstr,i); }

  /** Write element the slow way, as a long.  There is no way to write a
   *  missing value with this call.  Under rare circumstances this can throw:
   *  if the long does not fit in a double (value is larger magnitude than
   *  2^52), AND float values are stored in Vector.  In this case, there is no
   *  common compatible data representation.
   *
   *  */
  public final void set( long i, long l) {
    Chunk ck = chunkForRow(i);
    ck.set(i,l);
    postWrite(ck.close(ck.cidx(), new Futures())).blockForPending();
  }

  /** Write element the slow way, as a double.  Double.NaN will be treated as
   *  a set of a missing element.
   *  Slow to do this for every set - use Writer if writing many values
   *  */
  public final void set( long i, double d) {
    Chunk ck = chunkForRow(i);
    ck.set(i,d);
    postWrite(ck.close(ck.cidx(), new Futures())).blockForPending();
  }

  /** Write element the slow way, as a float.  Float.NaN will be treated as
   *  a set of a missing element.
   *  */
  public final void set( long i, float  f) {
    Chunk ck = chunkForRow(i);
    ck.set(i,f);
    postWrite(ck.close(ck.cidx(), new Futures())).blockForPending();
  }

  /** Set the element as missing the slow way.  */
  final void setNA( long i ) {
    Chunk ck = chunkForRow(i);
    ck.setNA(i);
    postWrite(ck.close(ck.cidx(), new Futures())).blockForPending();
  }

  public final void set( long i, String str) {
    Chunk ck = chunkForRow(i);
    ck.set(i,str);
    postWrite(ck.close(ck.cidx(), new Futures())).blockForPending();
  }

  /**
   * More efficient way to write randomly to a Vec - still slow, but much
   * faster than Vec.set().  Limited to single-threaded single-machine writes.
   *
   * Usage:
   * Vec.Writer vw = vec.open();
   * vw.set(0, 3.32);
   * vw.set(1, 4.32);
   * vw.set(2, 5.32);
   * vw.close();
   */
  public final static class Writer implements java.io.Closeable {
    final Vec _vec;
    private Writer(Vec v) { (_vec=v).preWriting(); }
    public final void set( long i, long   l) { _vec.chunkForRow(i).set(i,l); }
    public final void set( long i, double d) { _vec.chunkForRow(i).set(i,d); }
    public final void set( long i, float  f) { _vec.chunkForRow(i).set(i,f); }
    public final void setNA( long i        ) { _vec.chunkForRow(i).setNA(i); }
    public final void set( long i, String str) { _vec.chunkForRow(i).set(i,str); }
    public Futures close(Futures fs) { return _vec.postWrite(_vec.closeLocal(fs)); }
    public void close() { close(new Futures()).blockForPending(); }
  }

  /** Create a writer for bulk serial writes into this Vec */
  public final Writer open() { return new Writer(this); }

  /** Close all chunks that are local (not just the ones that are homed)
   *  This should only be called from a Writer object */
  private Futures closeLocal(Futures fs) {
    int nc = nChunks();
    for( int i=0; i<nc; i++ )
      if( H2O.containsKey(chunkKey(i)) )
        chunkForChunkIdx(i).close(i, fs);
    return fs;                  // Flow-coding
  }

  /** Pretty print the Vec: [#elems, min/mean/max]{chunks,...} */
  @Override public String toString() {
    RollupStats rs = RollupStats.getOrNull(this);
    String s = "["+length()+(rs == null ? ", {" : ","+rs._mins[0]+"/"+rs._mean+"/"+rs._maxs[0]+", "+PrettyPrint.bytes(rs._size)+", {");
    int nc = nChunks();
    for( int i=0; i<nc; i++ ) {
      s += chunkKey(i).home_node()+":"+chunk2StartElem(i)+":";
      // CNC: Bad plan to load remote data during a toString... messes up debug printing
      // Stupidly chunkForChunkIdx loads all data locally
      // s += chunkForChunkIdx(i).getClass().getSimpleName().replaceAll("Chunk","")+", ";
    }
    return s+"}]";
  }

  // Remove associated Keys when this guy removes
  @Override public Futures remove_impl( Futures fs ) {
    for( int i=0; i<nChunks(); i++ )
      DKV.remove(chunkKey(i),fs);
    DKV.remove(rollupStatsKey(),fs);
    return fs;
  }
  @Override public boolean equals( Object o ) {
    return o instanceof Vec && ((Vec)o)._key.equals(_key);
  }
  @Override public int hashCode() { return _key.hashCode(); }

  /** Always makes a copy of the given vector which shares the same
   * group.
   *
   * The user is responsible for deleting the returned vector.
   *
   * This can be expensive operation since it can force copy of data
   * among nodes.
   *
   * @param vec vector which is intended to be copied
   * @return a copy of vec which shared the same {@link VectorGroup} with this vector
   */
  public Vec align(final Vec vec) {
    assert ! this.group().equals(vec.group()) : "Vector align expects a vector from different vector group";
    assert this.length() == vec.length() : "Trying to align vectors with different length!";
    Vec avec = makeZero(); // aligned vector
    new MRTask() {
      @Override public void map(Chunk c0) {
        long srow = c0._start;
        for (int r = 0; r < c0._len; r++) c0.set0(r, vec.at(srow + r));
      }
    }.doAll(avec);
    avec._domain = _domain;
    return avec;
  }

  /** Transform this vector to enum.
   *  If the vector is integer vector then its domain is collected and transformed to
   *  corresponding strings.
   *  If the vector is enum an identity transformation vector is returned.
   *  Transformation is done by a TransfVec which provides a mapping between values.
   *
   *  @return always returns a new vector and the caller is responsible for vector deletion!
   */
  public Vec toEnum() {
    if( isEnum() ) return makeIdentityTransf(); // Make an identity transformation of this vector
    if( !isInt() ) throw new IllegalArgumentException("Enum conversion only works on integer columns");
    long[] domain= new CollectDomain().doAll(this).domain();
    if( domain.length > Enum.MAX_ENUM_SIZE )
      throw new IllegalArgumentException("Column domain is too large to be represented as an enum: " + domain.length + " > " + Enum.MAX_ENUM_SIZE);
    return this.makeSimpleTransf(domain, ArrayUtils.toString(domain));
  }

  /** Create a vector transforming values according given domain map.
   * @see Vec#makeTransf(int[], int[], String[])
   */
  public Vec makeTransf(final int[][] map, String[] finalDomain) { return makeTransf(map[0], map[1], finalDomain); }

  /** Creates a new transformation from given values to given indexes of given domain.
   *  @param values values being mapped from
   *  @param indexes values being mapped to
   *  @param domain domain of new vector
   *  @return always return a new vector which maps given values into a new domain
   */
  public Vec makeTransf(final int[] values, final int[] indexes, final String[] domain) {
    if( _espc == null ) throw H2O.unimpl();
    Vec v0 = new TransfVec(values, indexes, domain, this._key, group().addVec(),_espc);
    DKV.put(v0._key,v0);
    return v0;
  }

  /** Makes a new transformation vector with identity mapping.
   *  @return a new transformation vector
   *  @see Vec#makeTransf(int[], int[], String[])
   */
  private Vec makeIdentityTransf() {
    assert _domain != null : "Cannot make an identity transformation of non-enum vector!";
    return makeTransf(ArrayUtils.seq(0, _domain.length), null, _domain);
  }

  /** Makes a new transformation vector from given values to values 0..domain size
   *  @param values values which are mapped from
   *  @param domain target domain which is mapped to
   *  @return a new transformation vector providing mapping between given values and target domain.
   *  @see Vec#makeTransf(int[], int[], String[])
   */
  public Vec makeSimpleTransf(long[] values, String[] domain) {
    int is[] = new int[values.length];
    for( int i=0; i<values.length; i++ ) is[i] = (int)values[i];
    return makeTransf(is, null, domain);
  }
  /** This Vec does not have dependent hidden Vec it uses.
   *
   * @return dependent hidden vector or <code>null</code>
   */
  protected Vec masterVec() { return null; }

  /**
   * Class representing the group of vectors.
   *
   * Vectors from the same group have same distribution of chunks among nodes.
   * Each vector is member of exactly one group.  Default group of one vector
   * is created for each vector.  Group of each vector can be retrieved by
   * calling group() method;
   *
   * The expected mode of operation is that user wants to add new vectors
   * matching the source.  E.g. parse creates several vectors (one for each
   * column) which are all colocated and are colocated with the original
   * bytevector.
   *
   * To do this, user should first ask for the set of keys for the new vectors
   * by calling addVecs method on the target group.
   *
   * Vectors in the group will have the same keys except for the prefix which
   * specifies index of the vector inside the group.  The only information the
   * group object carries is it's own key and the number of vectors it
   * contains(deleted vectors still count).
   *
   * Because vectors(and chunks) share the same key-pattern with the group,
   * default group with only one vector does not have to be actually created,
   * it is implicit.
   *
   * @author tomasnykodym
   *
   */
  public static class VectorGroup extends Iced {
    // The common shared vector group for length==1 vectors
    public static VectorGroup newVectorGroup(){
      return new Vec(Vec.newKey(),(long[])null).group();
    }
    public static final VectorGroup VG_LEN1 = new VectorGroup();
    final int _len;
    final Key _key;
    private VectorGroup(Key key, int len){_key = key;_len = len;}
    public VectorGroup() {
      byte[] bits = new byte[26];
      bits[0] = Key.GRP;
      bits[1] = -1;
      UnsafeUtils.set4(bits, 2, -1);
      UnsafeUtils.set4(bits, 6, -1);
      UUID uu = UUID.randomUUID();
      UnsafeUtils.set8(bits,10,uu.getLeastSignificantBits());
      UnsafeUtils.set8(bits,18,uu. getMostSignificantBits());
      _key = Key.make(bits);
      _len = 0;
    }
    public Key vecKey(int vecId) {
      byte [] bits = _key._kb.clone();
      bits[0] = Key.VEC;
      UnsafeUtils.set4(bits,2,vecId);//
      return Key.make(bits);
    }
    /**
     * Task to atomically add vectors into existing group.
     * @author tomasnykodym
     */
    private static class AddVecs2GroupTsk extends TAtomic<VectorGroup>{
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
    // reserve range of keys and return index of first new available key
    public int reserveKeys(final int n){
      AddVecs2GroupTsk tsk = new AddVecs2GroupTsk(_key, n);
      tsk.invoke(_key);
      return tsk._n;
    }
    /**
     * Gets the next n keys of this group.
     * Performs atomic update of the group object to assure we get unique keys.
     * The group size will be updated by adding n.
     *
     * @param n number of keys to make
     * @return arrays of unique keys belonging to this group.
     */
    public Key [] addVecs(final int n){
      AddVecs2GroupTsk tsk = new AddVecs2GroupTsk(_key, n);
      tsk.invoke(_key);
      Key [] res = new Key[n];
      for(int i = 0; i < n; ++i)
        res[i] = vecKey(i + tsk._n);
      return res;
    }
    /**
     * Shortcut for addVecs(1).
     * @see #addVecs(int)
     */
    public Key addVec() { return addVecs(1)[0]; }

    @Override public String toString() {
      return "VecGrp "+_key.toString()+", next free="+_len;
    }

    @Override public boolean equals( Object o ) {
      return o instanceof VectorGroup && ((VectorGroup)o)._key.equals(_key);
    }
    @Override public int hashCode() {
      return _key.hashCode();
    }
  }

  /** Collect numeric domain of given vector */
  private static class CollectDomain extends MRTask<CollectDomain> {
    transient NonBlockingHashMapLong<String> _uniques;
    @Override protected void setupLocal() { _uniques = new NonBlockingHashMapLong<>(); }
    @Override public void map(Chunk ys) {
      for( int row=0; row< ys._len; row++ )
        if( !ys.isNA0(row) )
          _uniques.put(ys.at80(row),"");
    }

    @Override public void reduce(CollectDomain mrt) {
      if( _uniques != mrt._uniques ) _uniques.putAll(mrt._uniques);
    }

    @Override public AutoBuffer write_impl( AutoBuffer ab ) {
      return ab.putA8(_uniques==null ? null : _uniques.keySetLong());
    }

    @Override public CollectDomain read_impl( AutoBuffer ab ) {
      assert _uniques == null || _uniques.size()==0;
      long ls[] = ab.getA8();
      _uniques = new NonBlockingHashMapLong<>();
      if( ls != null ) for( long l : ls ) _uniques.put(l,"");
      return this;
    }
    @Override public void copyOver(CollectDomain that) {
      _uniques = that._uniques;
    }

    /** Returns exact numeric domain of given vector computed by this task.
     * The domain is always sorted. Hence:
     *    domain()[0] - minimal domain value
     *    domain()[domain().length-1] - maximal domain value
     */
    private long[] domain() {
      long[] dom = _uniques.keySetLong();
      Arrays.sort(dom);
      return dom;
    }
  }
}
