package water.fvec;

import water.*;
import water.parser.BufferedString;
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
 *   <p>The main API is {@link #at}, {@link #set}, and {@link #isNA}:<br>
 *   <table class='table table-striped table-bordered' border="1" summary="">
 *   <tr><th>     Returns       </th><th>    Call      </th><th>  Missing?  </th><th>Notes</th>
 *   <tr><td>  {@code double}   </td><td>{@link #at}   </td><td>{@code NaN} </td><td></td>
 *   <tr><td>  {@code long}     </td><td>{@link #at8}  </td><td>   throws   </td><td></td>
 *   <tr><td>  {@code long}     </td><td>{@link #at16l}</td><td>   throws   </td><td>Low  half of 128-bit UUID</td>
 *   <tr><td>  {@code long}     </td><td>{@link #at16h}</td><td>   throws   </td><td>High half of 128-bit UUID</td>
 *   <tr><td>{@link BufferedString}</td><td>{@link #atStr}</td><td>{@code null}</td><td>Updates BufferedString in-place and returns it for flow-coding</td>
 *   <tr><td>  {@code boolean}  </td><td>{@link #isNA} </td><td>{@code true}</td><td></td>
 *   <tr><td>        </td><td>{@link #set(long,double)}</td><td>{@code NaN} </td><td></td>
 *   <tr><td>        </td><td>{@link #set(long,float)} </td><td>{@code NaN} </td><td>Limited precision takes less memory</td>
 *   <tr><td>        </td><td>{@link #set(long,long)}  </td><td>Cannot set  </td><td></td>
 *   <tr><td>        </td><td>{@link #set(long,String)}</td><td>{@code null}</td><td>Convenience wrapper for String</td>
 *   <tr><td>        </td><td>{@link #setNA(long)}     </td><td>            </td><td></td>
 *   </table>
 *
 *  <p>Example manipulating some individual elements:<pre>
 *    double r1 = vec.at(0x123456789L);  // Access element 0x1234567889 as a double
 *    double r2 = vec.at(-1);            // Throws AIOOBE
 *    long   r3 = vec.at8_abs(1);        // Element #1, as a long
 *    vec.set(2,r1+r3);                  // Set element #2, as a double
 *  </pre>
 *
 *  <p>Vecs have a loosely enforced <em>type</em>: one of numeric, {@link UUID}
 *  or {@link String}.  Numeric types are further broken down into integral
 *  ({@code long}) and real ({@code double}) types.  The {@code categorical} type is
 *  an integral type, with a String mapping side-array.  Most of the math
 *  algorithms will treat categoricals as small dense integers, and most categorical
 *  printouts will use the String mapping.  Time is another special integral
 *  type: it is represented as milliseconds since the unix epoch, and is mostly
 *  treated as an integral type when doing math but it has special time-based
 *  printout formatting.  All types support the notion of a missing element; for
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
 *  <p>Vecs have a lazily computed {@link RollupStats} object and Key.  The
 *  RollupStats give fast access to the common metrics: {@link #min}, {@link
 *  #max}, {@link #mean}, {@link #sigma}, the count of missing elements ({@link
 *  #naCnt}) and non-zeros ({@link #nzCnt}), amongst other stats.  They are
 *  cleared if the Vec is modified and lazily recomputed after the modified Vec
 *  is closed.  Clearing the RollupStats cache is fairly expensive for
 *  individual {@link #set} calls but is easy to amortize over a large count of
 *  writes; i.e., batch writing is efficient.  This is normally handled by the
 *  MRTask framework; the {@link Vec.Writer} framework allows
 *  <em>single-threaded</em> efficient batch writing for smaller Vecs.
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
 *  <p>Vecs have a {@link Vec.VectorGroup}.  Vecs in the same VectorGroup have the
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
public class Vec extends AVec<SingleChunk> {
  // Vec internal type: one of T_BAD, T_UUID, T_STR, T_NUM, T_CAT, T_TIME
  byte _type;                   // Vec Type
  // String domain, only for Categorical columns
  private String[] _domain;


  /** Returns the categorical toString mapping array, or null if not an categorical column.
   *  Not a defensive clone (to expensive to clone; coding error to change the
   *  contents).
   *  @return the categorical / factor / categorical mapping array, or null if not a categorical column */
  public String[] domain(int i) {
    if(i != 0) throw new ArrayIndexOutOfBoundsException();
    return _domain;
  }   // made no longer final so that InteractionWrappedVec which are _type==T_NUM but have a categorical interaction

  @Override
  public byte type(int colId) {
    if(colId != 0) throw new ArrayIndexOutOfBoundsException(colId);
    return _type;
  }

  /** Set the categorical/factor names.  No range-checking on the actual
   *  underlying numeric domain; user is responsible for maintaining a mapping
   *  which is coherent with the Vec contents. */
  public final void setDomain(int i, String[] domain) {
    if(i != 0) throw new ArrayIndexOutOfBoundsException();
    _domain = domain;
    if( domain != null ) _type = T_CAT;
  }

  /** Set the categorical/factor names.  No range-checking on the actual
   *  underlying numeric domain; user is responsible for maintaining a mapping
   *  which is coherent with the Vec contents. */
  @Override
  public void setType(int i, byte t) {
    if(i != 0) throw new ArrayIndexOutOfBoundsException();
    _type = t;
  }


  /** Build a numeric-type Vec; the caller understands Chunk layout (via the
   *  {@code espc} array).
   */
  public Vec( Key<AVec> key, int rowLayout) { this(key, rowLayout, null, T_NUM); }

  /** Build a numeric-type or categorical-type Vec; the caller understands Chunk
   *  layout (via the {@code espc} array); categorical Vecs need to pass the
   *  domain.
   */
  Vec( Key<AVec> key, int rowLayout, String[] domain) { this(key,rowLayout,domain, (domain==null?T_NUM:T_CAT)); }

  /** Main default constructor; the caller understands Chunk layout (via the
   *  {@code espc} array), plus categorical/factor the {@code domain} (or null for
   *  non-categoricals), and the Vec type. */
  public Vec( Key<AVec> key, int rowLayout, String[] domain, byte type ) {
    super(key,rowLayout);
    assert key._kb[0]==Key.VEC;
    assert domain==null || type==T_CAT;
    assert T_BAD <= type && type <= T_TIME; // Note that T_BAD is allowed for all-NA Vecs
    _type = type;
    _domain = domain;
  }

  @Override
  public boolean hasCol(int id) {return id == 0;}


  @Override
  public RollupStats getRollups(int colId, boolean histo) {
    throw H2O.unimpl(); // TODO
  }

  @Override public void setBad(int colId) {
    if(colId != 0) throw new ArrayIndexOutOfBoundsException();
    _type = T_BAD;
  }


  // ======= Create zero/constant Vecs ======
  /** Make a new zero-filled vec **/
  public static Vec makeZero( long len, boolean redistribute ) {
    return makeCon(0L,len,redistribute);
  }
  /** Make a new zero-filled vector with the given row count.
   *  @return New zero-filled vector with the given row count. */
  public static Vec makeZero( long len ) { return makeCon(0d,len); }

  /** Make a new constant vector with the given row count, and redistribute the data
   * evenly around the cluster.
   * @param x The value with which to fill the Vec.
   * @param len Number of rows.
   * @return New cosntant vector with the given len.
   */
  public static Vec makeCon(double x, long len) {
    return makeCon(x,len,true);
  }

  /** Make a new constant vector with the given row count.
   *  @return New constant vector with the given row count. */
  public static Vec makeCon(double x, long len, boolean redistribute) {
    int log_rows_per_chunk = FileVec.DFLT_LOG2_CHUNK_SIZE;
    return makeCon(x,len,log_rows_per_chunk,redistribute);
  }

  /** Make a new constant vector with the given row count, and redistribute the data evenly
   *  around the cluster.
   *  @return New constant vector with the given row count. */
  public static Vec makeCon(double x, long len, int log_rows_per_chunk) {
    return makeCon(x,len,log_rows_per_chunk,true);
  }

  /**
   * Make a new constant vector with minimal number of chunks. Used for importing SQL tables.
   *  @return New constant vector with the given row count. */
  public static Vec makeCon(long totSize, long len) {
    int safetyInflationFactor = 8;
    int nchunks = (int) Math.max(safetyInflationFactor * totSize / Value.MAX , 1);
    long[] espc = new long[nchunks+1];
    espc[0] = 0;
    for( int i=1; i<nchunks; i++ )
      espc[i] = espc[i-1]+len/nchunks;
    espc[nchunks] = len;
    VectorGroup vg = VectorGroup.VG_LEN1;
    return makeCon(0,vg, AVec.ESPC.rowLayout(vg._key,espc));
  }

  /** Make a new constant vector with the given row count.
   *  @return New constant vector with the given row count. */
  public static Vec makeCon(double x, long len, int log_rows_per_chunk, boolean redistribute) {
    int chunks0 = (int)Math.max(1,len>>log_rows_per_chunk); // redistribute = false
    int chunks1 = (int)Math.min( 4 * H2O.NUMCPUS * H2O.CLOUD.size(), len); // redistribute = true
    int nchunks = (redistribute && chunks0 < chunks1 && len > 10*chunks1) ? chunks1 : chunks0;
    long[] espc = new long[nchunks+1];
    espc[0] = 0;
    for( int i=1; i<nchunks; i++ )
      espc[i] = redistribute ? espc[i-1]+len/nchunks : ((long)i)<<log_rows_per_chunk;
    espc[nchunks] = len;
    VectorGroup vg = VectorGroup.VG_LEN1;
    return makeCon(x,vg, ESPC.rowLayout(vg._key,espc));
  }

  public VecAry makeDoubles(int n, double [] values) {
    Key [] keys = group().addVecs(n);
    Vec [] res = new Vec[n];
    for(int i = 0; i < n; ++i)
      res[i] = new Vec(keys[i],_rowLayout);
    fillDoubleChunks(this,res, values);
    Futures fs = new Futures();
    for(Vec v:res)
      DKV.put(v,fs);
    fs.blockForPending();
    System.out.println("made vecs " + Arrays.toString(res));
    return  new VecAry(res);
  }


  private static void fillDoubleChunks(Vec v, final Vec[] ds, final double [] values){
    new MRTask(){
      public void map(Chunk c){
        for(int i = 0; i < ds.length; ++i)
          DKV.put(ds[i].chunkKey(c.cidx()),new C0DChunk(values[i],c._len << 3));
      }
    }.doAll(new VecAry(v));
  }

  /** A new vector which is a copy of {@code this} one.
   *  @return a copy of the vector.  */
  public Vec makeCopy() { return makeCopy(domain(0)); }

  public Vec makeCopy(String[] domain) {
    Vec v = doCopy();
    v.setDomain(0,domain);
    DKV.put(v);
    return v;
  }

  @Override
  public Vec doCopy(){
    final Vec v = new Vec(group().addVec(),_rowLayout);
    new MRTask(){
      @Override public void map(Chunk c){
        Chunk c2 = c.deepCopy();
        DKV.put(v.chunkKey(c.cidx()), c2, _fs);
      }
    }.doAll(new VecAry(this));
    return v;
  }

  public static Vec makeCon( final long l, String[] domain, VectorGroup group, int rowLayout ) {
    final Vec v0 = new Vec(group.addVec(), rowLayout, domain);
    final int nchunks = v0.nChunks();
    new MRTask() {              // Body of all zero chunks
      @Override protected void setupLocal() {
        for( int i=0; i<nchunks; i++ ) {
          Key k = v0.chunkKey(i);
          if( k.home() ) DKV.put(k,new C0LChunk(l,v0.chunkLen(i)),_fs);
        }
      }
    }.doAllNodes();
    DKV.put(v0._key, v0);        // Header last
    return v0;
  }

  public static Vec makeCon( final double d, String[] domain, VectorGroup group, int rowLayout ) {
    final Vec v0 = new Vec(group.addVec(), rowLayout, domain);
    final int nchunks = v0.nChunks();
    new MRTask() {              // Body of all zero chunks
      @Override protected void setupLocal() {
        for( int i=0; i<nchunks; i++ ) {
          Key k = v0.chunkKey(i);
          if( k.home() ) DKV.put(k,new C0DChunk(d,v0.chunkLen(i)),_fs);
        }
      }
    }.doAllNodes();
    DKV.put(v0._key, v0);        // Header last
    return v0;
  }

  public static Vec makeVec(double [] vals, Key<AVec> vecKey){
    Vec v = new Vec(vecKey, AVec.ESPC.rowLayout(vecKey,new long[]{0,vals.length}));
    SingleChunk sc = new SingleChunk(v,0);
    NewChunk nc = new NewChunk(sc,0);
    Futures fs = new Futures();
    for(double d:vals)
      nc.addNum(d);
    nc.close(fs);
    DKV.put(v._key, v, fs);
    fs.blockForPending();
    return v;
  }

  // allow missing (NaN) categorical values
  public static Vec makeVec(double [] vals, String [] domain, Key<AVec> vecKey){
    Vec v = new Vec(vecKey, AVec.ESPC.rowLayout(vecKey, new long[]{0, vals.length}), domain);
    NewChunk nc = new NewChunk(new SingleChunk(v,0),0);
    Futures fs = new Futures();
    for(double d:vals) {
      assert(Double.isNaN(d) || (long)d == d);
      nc.addNum(d);
    }
    nc.close(fs);
    DKV.put(v._key, v, fs);
    fs.blockForPending();
    return v;
  }
  // Warning: longs are lossily converted to doubles in nc.addNum(d)
  public static Vec makeVec(long [] vals, String [] domain, Key<AVec> vecKey){
    Vec v = new Vec(vecKey, AVec.ESPC.rowLayout(vecKey, new long[]{0, vals.length}), domain);
    NewChunk nc = new NewChunk(new SingleChunk(v,0),0);
    Futures fs = new Futures();
    for(long d:vals)
      nc.addNum(d);
    nc.close(fs);
    DKV.put(v._key, v, fs);
    fs.blockForPending();
    return v;
  }

  public static Vec[] makeCons(double x, long len, int n) {
    Vec[] vecs = new Vec[n];
    for( int i=0; i<n; i++ )
      vecs[i] = makeCon(x,len,true);
    return vecs;
  }

  /** Make a new vector with the same size and data layout as the current one,
   *  and initialized to the given constant value.
   *  @return A new vector with the same size and data layout as the current one,
   *  and initialized to the given constant value.  */
  public Vec makeCon( final double d ) { return makeCon(d, group(), _rowLayout); }

  private static Vec makeCon( final double d, VectorGroup group, int rowLayout ) {
    if( (long)d==d ) return makeCon((long)d, null, group, rowLayout);
    final Vec v0 = new Vec(group.addVec(), rowLayout, null, T_NUM);
    final int nchunks = v0.nChunks();
    new MRTask() {              // Body of all zero chunks
      @Override protected void setupLocal() {
        for( int i=0; i<nchunks; i++ ) {
          Key k = v0.chunkKey(i);
          if( k.home() ) DKV.put(k,new C0DChunk(d,v0.chunkLen(i)),_fs);
        }
      }
    }.doAllNodes();
    DKV.put(v0._key, v0);        // Header last
    return v0;
  }




  /** A Vec from an array of doubles
   *  @param rows Data
   *  @return The Vec  */
  public static VecAry makeCon(Key<AVec> k, double ...rows) {
    k = k==null?Vec.VectorGroup.VG_LEN1.addVec():k;
    Futures fs = new Futures();
    AppendableVec avec = new AppendableVec(k, T_NUM);
    NewChunk chunk = new NewChunk(new SingleChunk(avec,0), 0);
    for( double r : rows ) chunk.addNum(r);
    chunk.close(fs);
    VecAry vec = avec.layout_and_close(fs);
    fs.blockForPending();
    return vec;
  }

  /** Make a new vector initialized to increasing integers, starting with 1.
   *  @return A new vector initialized to increasing integers, starting with 1. */
  public static Vec makeSeq( long len, boolean redistribute) {
    return (Vec) new MRTask() {
      @Override public void map(Chunk[] cs) {
        for( Chunk c : cs )
          for( int r = 0; r < c._len; r++ )
            c.set(r, r + 1 + c.start());
      }
    }.doAll(makeZero(len, redistribute)).vecs().getAVecRaw(0);
  }

  /** Make a new vector initialized to increasing integers, starting with `min`.
   *  @return A new vector initialized to increasing integers, starting with `min`.
   */
  public static Vec makeSeq(final long min, long len) {
    return (Vec) new MRTask() {
      @Override public void map(Chunk[] cs) {
        for (Chunk c : cs)
          for (int r = 0; r < c._len; r++)
            c.set(r, r + min + c.start());
      }
    }.doAll(makeZero(len)).vecs().getAVecRaw(0);
  }

  /** Make a new vector initialized to increasing integers, starting with `min`.
   *  @return A new vector initialized to increasing integers, starting with `min`.
   */
  public static Vec makeSeq(final long min, long len, boolean redistribute) {
    return (Vec) new MRTask() {
      @Override public void map(Chunk c) {
        for (int r = 0; r < c._len; r++)
          c.set(r, r + min + c.start());
      }
    }.doAll(makeZero(len, redistribute)).vecs().getAVecRaw(0);
  }

  /** Make a new vector initialized to increasing integers mod {@code repeat}.
   *  @return A new vector initialized to increasing integers mod {@code repeat}.
   */
  public static Vec makeRepSeq( long len, final long repeat ) {
    return (Vec) new MRTask() {
      @Override public void map(Chunk c) {
        for( int r = 0; r < c._len; r++ )
          c.set(r, (r + c.start()) % repeat);
      }
    }.doAll(makeZero(len)).vecs().getAVecRaw(0);
  }

  public Vec makeZero() {return makeCon(0);}


  private static class SetMutating extends TAtomic<RollupStats> {
    @Override protected RollupStats atomic(RollupStats rs) {
      return rs != null && rs.isMutating() ? null : RollupStats.makeMutating();
    }
  }


  /** Begin writing into this Vec.  Immediately clears all the rollup stats
   *  ({@link #min}, {@link #max}, {@link #mean}, etc) since such values are
   *  not meaningful while the Vec is being actively modified.  Can be called
   *  repeatedly.  Per-chunk row-counts will not be changing, just row
   *  contents. */
  @Override public void preWriting(int... colIds) {
    if( !writable() ) throw new IllegalArgumentException("Vector not writable");
    if(colIds != null && (colIds.length != 1 || colIds[0] != 0))
      throw new ArrayIndexOutOfBoundsException();
    final Key rskey = rollupStatsKey();
    Value val = DKV.get(rskey);
    if( val != null ) {
      RollupStats rs = val.get(RollupStats.class);
      if( rs.isMutating() ) return; // Vector already locked against rollups
    }
    // Set rollups to "vector isMutating" atomically.
    new Vec.SetMutating().invoke(rskey);
  }

  /** Stop writing into this Vec.  Rollup stats will again (lazily) be
   *  computed. */
  @Override public Futures postWrite( Futures fs ) {
    // Get the latest rollups *directly* (do not compute them!).
    if (writable()) { // skip this for immutable vecs (like FileVec)
      final Key rskey = rollupStatsKey();
      Value val = DKV.get(rollupStatsKey());
      if (val != null) {
        RollupStats rs = val.get(RollupStats.class);
        if (rs.isMutating())  // Vector was mutating, is now allowed for rollups
          DKV.remove(rskey, fs);// Removing will cause them to be rebuilt, on demand
      }
    }
    return fs;                  // Flow-coding
  }


  // ======= Direct Data Accessors ======

  /** Fetch element the slow way, as a long.  Floating point values are
   *  silently rounded to an integer.  Throws if the value is missing. 
   *  @return {@code i}th element as a long, or throw if missing */
  public final long  at8( long i ) {return chunkForRow(i).getChunk(0).at8_abs(i);}

  /** Fetch element the slow way, as a double, or Double.NaN is missing.
   *  @return {@code i}th element as a double, or Double.NaN if missing */
  public final double at( long i ) { return chunkForRow(i).getChunk(0).at_abs(i); }
  /** Fetch the missing-status the slow way. 
   *  @return the missing-status the slow way */
  public final boolean isNA(long row){ return chunkForRow(row).getChunk(0).isNA_abs(row); }

  /** Fetch element the slow way, as the low half of a UUID.  Throws if the
   *  value is missing or not a UUID.
   *  @return {@code i}th element as a UUID low half, or throw if missing */
  public final long  at16l( long i ) { return chunkForRow(i).getChunk(0).at16l_abs(i); }
  /** Fetch element the slow way, as the high half of a UUID.  Throws if the
   *  value is missing or not a UUID.
   *  @return {@code i}th element as a UUID high half, or throw if missing */
  public final long  at16h( long i ) { return chunkForRow(i).getChunk(0).at16h_abs(i); }

  /** Fetch element the slow way, as a {@link BufferedString} or null if missing.
   *  Throws if the value is not a String.  BufferedStrings are String-like
   *  objects than can be reused in-place, which is much more efficient than
   *  constructing Strings.
   *  @return {@code i}th element as {@link BufferedString} or null if missing, or
   *  throw if not a String */
  public final BufferedString atStr( BufferedString bStr, long i ) { return chunkForRow(i).getChunk(0).atStr_abs(bStr, i); }

  /** A more efficient way to read randomly to a Vec - still single-threaded,
   *  but much faster than Vec.at(i).  Limited to single-threaded
   *  single-machine reads.
   *
   * Usage:
   * Vec.Reader vr = vec.new Reader();
   * x = vr.at(0);
   * y = vr.at(1);
   * z = vr.at(2);
   */
  public final class Reader {
    private SingleChunk _cache;
    private SingleChunk chk(long i) {
      SingleChunk c = _cache;
      return (c != null && c._start <= i && i < c._start+ c._c._len) ? c : (_cache = chunkForRow(i));
    }
    public final long    at8( long i ) { return chk(i)._c. at8_abs(i); }
    public final double   at( long i ) { return chk(i)._c. at_abs(i); }
    public final boolean isNA(long i ) { return chk(i)._c. isNA_abs(i); }
    public final long length() { return Vec.this.length(); }
  }

//  /** Write element the slow way, as a long.  There is no way to write a
//   *  missing value with this call.  Under rare circumstances this can throw:
//   *  if the long does not fit in a double (value is larger magnitude than
//   *  2^52), AND float values are stored in Vec.  In this case, there is no
//   *  common compatible data representation.  */
//  public final void set( long i, long l) {
//    chunkForRow(i).set_abs(i,l);
//
//    postWrite(closeChunk(ck.cidx(), ck,  new Futures())).blockForPending();
//  }
//
//  /** Write element the slow way, as a double.  Double.NaN will be treated as a
//   *  set of a missing element. */
//  public final void set( long i, double d) {
//    Chunk ck = chunkForRow(i);
//    ck.set(ck.rowInChunk(i), d);
//    postWrite(closeChunk(ck.cidx(),ck, new Futures())).blockForPending();
//  }
//
//  /** Write element the slow way, as a float.  Float.NaN will be treated as a
//   *  set of a missing element. */
//  public final void set( long i, float  f) {
//    Chunk ck = chunkForRow(i);
//    ck.set(ck.rowInChunk(i), f);
//    postWrite(closeChunk(ck.cidx(), ck, new Futures())).blockForPending();
//  }
//
//  /** Set the element as missing the slow way.  */
//  final void setNA( long i ) {
//    Chunk ck = chunkForRow(i);
//    ck.setNA(ck.rowInChunk(i));
//    postWrite(closeChunk(ck.cidx(),ck, new Futures())).blockForPending();
//  }
//
//  /** Write element the slow way, as a String.  {@code null} will be treated as a
//   *  set of a missing element. */
//  public final void set( long i, String str) {
//    Chunk ck = chunkForRow(i);
//    ck.set((int)(i - ck.start()), str);
//    postWrite(closeChunk(ck.cidx(),ck, new Futures())).blockForPending();
//  }

  /** A more efficient way to write randomly to a Vec - still single-threaded,
   *  still slow, but much faster than Vec.set().  Limited to single-threaded
   *  single-machine writes.
   *
   * Usage:
   * try( Vec.Writer vw = vec.open() ) {
   *   vw.set(0, 3.32);
   *   vw.set(1, 4.32);
   *   vw.set(2, 5.32);
   * }
   */
//  public final class Writer implements java.io.Closeable {
//    private Chunk _cache;
//    long _start;
//    private Chunk chk(long i) {
//      Chunk c = _cache;
//      return (c != null && c.chk2()==null && c._start <= i && i < c._start+ c._len) ? c : (_cache = chunkForRow(i));
//    }
//    private Writer() { preWriting(); }
//    public final void set( long i, long   l) {
//      Chunk c = chk(i);
//      c.set(c.rowInChunk(i), l);
//    }
//    public final void set( long i, double d) {
//      Chunk c = chk(i);
//      c.set(c.rowInChunk(i), d);
//    }
//    public final void set( long i, float  f) {
//      Chunk c = chk(i);
//      c.set(c.rowInChunk(i), f);
//    }
//    public final void setNA( long i        ) {
//      Chunk c = chk(i);
//      c.setNA(c.rowInChunk(i));
//    }
//    public final void set( long i,String str){
//      Chunk c = chk(i);
//      c.set(c.rowInChunk(i), str);
//    }
//    public Futures close(Futures fs) { return postWrite(closeLocal(fs)); }
//    public void close() { close(new Futures()).blockForPending(); }
//  }
//
//  /** Create a writer for bulk serial writes into this Vec.
//   *  @return A Writer for bulk serial writes */
//  public final Writer open() { return new Writer(); }
//
//  /** Close all chunks that are local (not just the ones that are homed)
//   *  This should only be called from a Writer object */
//  private Futures closeLocal(Futures fs) {
//    int nc = nChunks();
//    for( int i=0; i<nc; i++ )
//      if( H2O.containsKey(chunkKey(i)) )
//        closeChunk(i,chunkForChunkIdx(i),fs);
//    return fs;                  // Flow-coding
//  }
//
//  /** Pretty print the Vec: {@code [#elems, min/mean/max]{chunks,...}}
//   *  @return Brief string representation of a Vec */
//  @Override public String toString() {
//    RollupStats rs = RollupStats.getOrNull(this,rollupStatsKey());
//    String s = "["+length()+(rs == null ? ", {" : ","+rs._mins[0]+"/"+rs._mean+"/"+rs._maxs[0]+", "+PrettyPrint.bytes(rs._size)+", {");
//    int nc = nChunks();
//    for( int i=0; i<nc; i++ ) {
//      s += chunkKey(i).home_node()+":"+chunk2StartElem(i)+":";
//      // CNC: Bad plan to load remote data during a toString... messes up debug printing
//      // Stupidly chunkForChunkIdx loads all data locally
//      // s += chunkForChunkIdx(i).getClass().getSimpleName().replaceAll("Chunk","")+", ";
//    }
//    return s+"}]";
//  }
//

  /** True if two Vecs are equal.  Checks for equal-Keys only (so it is fast)
   *  and not equal-contents.
   *  @return True if two Vecs are equal */
  @Override public boolean equals( Object o ) {
    return o instanceof Vec && ((Vec)o)._key.equals(_key);
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
}
