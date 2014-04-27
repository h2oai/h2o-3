package water.fvec;

import java.util.Arrays;
import java.util.UUID;
import water.*;
import water.nbhm.NonBlockingHashMapLong;
import water.parser.ParseTime;
import water.util.ArrayUtils;
import water.util.PrettyPrint;

/**
 * A single distributed vector column.
 * <p>
 * A distributed vector has a count of elements, an element-to-chunk mapping, a
 * Java type (mostly determines rounding on store and display), and functions
 * to directly load elements without further indirections.  The data is
 * compressed, or backed by disk or both.  *Writing* to elements may throw if the
 * backing data is read-only (file backed).
 * <p>
 * <pre>
 *  Vec Key format is: Key. VEC - byte, 0 - byte,   0    - int, normal Key bytes.
 * DVec Key format is: Key.DVEC - byte, 0 - byte, chunk# - int, normal Key bytes.
 * </pre>
 *
 * The main API is at, set, and isNA:<br>
 *<pre>
 *   double  at  ( long row );  // Returns the value expressed as a double.  NaN if missing.
 *   long    at8 ( long row );  // Returns the value expressed as a long.  Throws if missing.
 *   boolean isNA( long row );  // True if the value is missing.
 *   set( long row, double d ); // Stores a double; NaN will be treated as missing.
 *   set( long row, long l );   // Stores a long; throws if l exceeds what fits in a double & any floats are ever set.
 *   setNA( long row );         // Sets the value as missing.
 * </pre>
 *
 * Note this dangerous scenario: loading a missing value as a double, and
 * setting it as a long: <pre>
 *   set(row,(long)at(row)); // Danger!
 *</pre>
 * The cast from a Double.NaN to a long produces a zero!  This code will
 * replace a missing value with a zero.
 *
 * @author Cliff Click
 */
public class Vec extends Keyed {
  /** Log-2 of Chunk size. */
  static final int LOG_CHK = 20/*1Meg*/+0/*4Meg*/;
  /** Chunk size.  Bigger increases batch sizes, lowers overhead costs, lower
   * increases fine-grained parallelism. */
  public static final int CHUNK_SZ = 1 << LOG_CHK;

  /** Element-start per chunk.  Always zero for chunk 0.  One more entry than
   *  chunks, so the last entry is the total number of rows.  This field is
   *  dead/ignored in subclasses that are guaranteed to have fixed-sized chunks
   *  such as file-backed Vecs. */
  final private long _espc[];

  /** Enum/factor/categorical names. */
  protected String [] _domain;
  /** Time parse, index into Utils.TIME_PARSE, or -1 for not-a-time */
  protected byte _time;

  /** Maximal size of enum domain */
  private static final int MAX_ENUM_SIZE = 10000;

  /** Main default constructor; requires the caller understand Chunk layout
   *  already, along with count of missing elements.  */
  protected Vec( Key key, long espc[]) { this(key, espc, null); }
  protected Vec( Key key, long espc[], String[] domain) {
    super(key);
    assert key._kb[0]==Key.VEC;
    _espc = espc;
    _time = -1;                 // not-a-time
    _domain = domain;
  }

  protected Vec( Key key, Vec v ) { this(key, v._espc); assert group()==v.group(); }

   /** Make a new vector with the same size and data layout as the old one, and
   *  initialized to zero. */
  public Vec makeZero()                { return makeCon(0); }
  private Vec makeZero(String[] domain) { return makeCon(0, domain); }
  /** Make a new vector with the same size and data layout as the old one, and
   *  initialized to a constant. */
  private Vec makeCon( final long l ) { return makeCon(l, null); }
  private Vec makeCon( final long l, String[] domain ) {
    if( _espc == null ) throw H2O.unimpl(); // need to make espc for e.g. NFSFileVecs!
    final int nchunks = nChunks();
    final Vec v0 = new Vec(group().addVec(), _espc, domain);
    new MRTask() {
      @Override protected void setupLocal() {
        long row=0;             // Start row
        Key k;
        for( int i=0; i<nchunks; i++ ) {
          long nrow = chunk2StartElem(i+1); // Next row
          if( (k = v0.chunkKey(i)).home() )
            DKV.put(k,new C0LChunk(l,(int)(nrow-row)),_fs);
          row = nrow;
        }
      }
    }.doAll(v0._key);
    DKV.put(v0._key,v0);
    return v0;
  }
  private Vec makeCon( final double d ) {
    Futures fs = new Futures();
    if( _espc == null ) throw H2O.unimpl(); // need to make espc for e.g. NFSFileVecs!
    if( (long)d==d ) return makeCon((long)d);
    final int nchunks = nChunks();
    final Vec v0 = new Vec(group().addVecs(1)[0],_espc);
    throw H2O.unimpl();
    //new DRemoteTask(){
    //  @Override private void lcompute(){
    //    long row=0;                 // Start row
    //    Key k;
    //    for( int i=0; i<nchunks; i++ ) {
    //      long nrow = chunk2StartElem(i+1); // Next row
    //      if((k = v0.chunkKey(i)).home())
    //        DKV.put(k,new C0DChunk(d,(int)(nrow-row)),_fs);
    //      row = nrow;
    //    }
    //    tryComplete();
    //  }
    //  @Override private void reduce(DRemoteTask drt){}
    //}.invokeOnAllNodes();
    //DKV.put(v0._key,v0,fs);
    //fs.blockForPending();
    //return v0;
  }
  private static Vec makeSeq( int len ) {
    Futures fs = new Futures();
    AppendableVec av = new AppendableVec(VectorGroup.VG_LEN1.addVec());
    NewChunk nc = new NewChunk(av,0);
    for (int r = 0; r < len; r++) nc.addNum(r+1);
    nc.close(0,fs);
    Vec v = av.close(fs);
    fs.blockForPending();
    return v;
  }
  private static Vec makeConSeq(double x, int len) {
    Futures fs = new Futures();
    AppendableVec av = new AppendableVec(VectorGroup.VG_LEN1.addVec());
    NewChunk nc = new NewChunk(av,0);
    for (int r = 0; r < len; r++) nc.addNum(x);
    nc.close(0,fs);
    Vec v = av.close(fs);
    fs.blockForPending();
    return v;
  }

  /** Create a new 1-element vector in the shared vector group for 1-element vectors. */
  private static Vec make1Elem(double d) {
    return make1Elem(Vec.VectorGroup.VG_LEN1.addVec(), d);
  }
  /** Create a new 1-element vector representing a scalar value. */
  private static Vec make1Elem(Key key, double d) {
    assert key.isVec();
    Vec v = new Vec(key,new long[]{0,1});
    Futures fs = new Futures();
    throw H2O.unimpl();
    //DKV.put(v.chunkKey(0),new C0DChunk(d,1),fs);
    //DKV.put(key,v,fs);
    //fs.blockForPending();
    //return v;
  }

  /** Number of elements in the vector.  Overridden by subclasses that compute
   *  length in an alternative way, such as file-backed Vecs. */
  public long length() { return _espc[_espc.length-1]; }

  /** Number of chunks.  Overridden by subclasses that compute chunks in an
   *  alternative way, such as file-backed Vecs. */
  public int nChunks() { return _espc.length-1; }

  /** Is the column a factor/categorical/enum?  Note: all "isEnum()" columns
   *  are are also "isInt()" but not vice-versa. */
  public final boolean isEnum(){return _domain != null;}

  /** Whether or not this column parsed as a time, and if so what pattern was used. */
  private final boolean isTime(){ return _time>=0; }
  private final int timeMode(){ return _time; }
  public final String timeParse(){ return ParseTime.TIME_PARSE[_time]; }

  /** Is the column constant.
   * <p>Returns true if the column contains only constant values and it is not full of NAs.</p> */
  public final boolean isConst() { return min() == max(); }
  /** Is the column bad.
   * <p>Returns true if the column is full of NAs.</p>
   */
  private final boolean isBad() { return naCnt() == length(); }

  /** Map the integer value for a enum/factor/categorical to it's String.
   *  Error if it is not an ENUM.  */
  private String domain(long i) { return _domain[(int)i]; }

  /** Return an array of domains.  This is eagerly manifested for enum or
   *  categorical columns.  Returns null for non-Enum/factor columns. */
  public String[] domain() { return _domain; }

  /** Returns cardinality for enum domain or -1 for other types. */
  private int cardinality() { return isEnum() ? _domain.length : -1; }

  /** Default read/write behavior for Vecs.  File-backed Vecs are read-only. */
  protected boolean readable() { return true ; }
  /** Default read/write behavior for Vecs.  AppendableVecs are write-only. */
  protected boolean writable() { return true; }

  /** RollupStats: min/max/mean of this Vec lazily computed.  */
  /** Return column min - lazily computed as needed. */
  public double min()  { return rollupStats()._min; }
  /** Return column max - lazily computed as needed. */
  public double max()  { return rollupStats()._max; }
  /** Return column mean - lazily computed as needed. */
  double mean() { return rollupStats()._mean; }
  /** Return column standard deviation - lazily computed as needed. */
  double sigma(){ return rollupStats()._sigma; }
  /** Return column missing-element-count - lazily computed as needed. */
  public long  naCnt() { return rollupStats()._naCnt; }
  /** Is all integers? */
  boolean isInt(){return rollupStats()._isInt; }
  /** Size of compressed vector data. */
  long byteSize(){return rollupStats()._size; }
  /** Compute the roll-up stats as-needed */
  private RollupStats rollupStats() { return RollupStats.get(this, null); }

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
  public void postWrite( Futures fs ) {
    // Get the latest rollups *directly* (do not compute them!).
    final Key rskey = rollupStatsKey();
    Value val = DKV.get(rollupStatsKey());
    if( val != null ) {
      RollupStats rs = val.get(RollupStats.class);
      if( rs.isMutating() )  // Vector was mutating, is now allowed for rollups
        DKV.remove(rskey,fs);// Removing will cause them to be rebuilt, on demand
    }
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

  /** Convert a chunk-index into a starting row #.  For constant-sized chunks
   *  this is a little shift-and-add math.  For variable-sized chunks this is a
   *  table lookup. */
  long chunk2StartElem( int cidx ) { return _espc[cidx]; }

  /** Number of rows in chunk. Does not fetch chunk content. */
  private int chunkLen( int cidx ) { return (int) (_espc[cidx + 1] - _espc[cidx]); }

  /** Get a Vec Key from Chunk Key, without loading the Chunk */
  public static Key getVecKey( Key key ) {
    assert key._kb[0]==Key.DVEC;
    byte [] bits = key._kb.clone();
    bits[0] = Key.VEC;
    UDP.set4(bits,6,-1); // chunk#
    return Key.make(bits);
  }

  /** Get a Chunk Key from a chunk-index.  Basically the index-to-key map. */
  public Key chunkKey(int cidx ) {
    byte [] bits = _key._kb.clone();
    bits[0] = Key.DVEC;
    UDP.set4(bits,6,cidx); // chunk#
    return Key.make(bits);
  }
  public Key rollupStatsKey() { return chunkKey(-2); }

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


  /** Make a new random Key that fits the requirements for a Vec key. */
  public static Key newKey(){return newKey(Key.make());}

  public static final int KEY_PREFIX_LEN = 4+4+1+1;
  /** Make a new Key that fits the requirements for a Vec key, based on the
   *  passed-in key.  Used to make Vecs that back over e.g. disk files. */
  static Key newKey(Key k) {
    byte [] kb = k._kb;
    byte [] bits = MemoryManager.malloc1(kb.length+KEY_PREFIX_LEN);
    bits[0] = Key.VEC;
    bits[1] = -1;         // Not homed
    UDP.set4(bits,2,0);   // new group, so we're the first vector
    UDP.set4(bits,6,-1);  // 0xFFFFFFFF in the chunk# area
    System.arraycopy(kb, 0, bits, 4+4+1+1, kb.length);
    return Key.make(bits);
  }

  /** Make a Vector-group key.  */
  private Key groupKey(){
    byte [] bits = _key._kb.clone();
    bits[0] = Key.VGROUP;
    UDP.set4(bits, 2, -1);
    UDP.set4(bits, 6, -1);
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
  private final Chunk chunkForRow(long i) {
    Chunk c = _cache;
    return (c != null && c._chk2==null && c._start <= i && i < c._start+c._len) ? c : (_cache = chunkForRow_impl(i));
  }
  /** Fetch element the slow way, as a long.  Floating point values are
   *  silently rounded to an integer.  Throws if the value is missing. */
  public final long  at8( long i ) { return chunkForRow(i).at8(i); }
  /** Fetch element the slow way, as a double.  Missing values are
   *  returned as Double.NaN instead of throwing. */
  public final double at( long i ) { return chunkForRow(i).at(i); }
  /** Fetch the missing-status the slow way. */
  public final boolean isNA(long row){ return chunkForRow(row).isNA(row); }


  /** Write element the slow way, as a long.  There is no way to write a
   *  missing value with this call.  Under rare circumstances this can throw:
   *  if the long does not fit in a double (value is larger magnitude than
   *  2^52), AND float values are stored in Vector.  In this case, there is no
   *  common compatible data representation.
   *
   *  */
  final long   set( long i, long   l) {return chunkForRow(i).set(i,l);}

  /** Write element the slow way, as a double.  Double.NaN will be treated as
   *  a set of a missing element.
   *  */
  final double set( long i, double d) {return chunkForRow(i).set(i,d);}
  /** Write element the slow way, as a float.  Float.NaN will be treated as
   *  a set of a missing element.
   *  */
  final float  set( long i, float  f) {return chunkForRow(i).set(i,f);}
  /** Set the element as missing the slow way.  */
  final boolean setNA( long i ) { return chunkForRow(i).setNA(i);}

  /** Pretty print the Vec: [#elems, min/mean/max]{chunks,...} */
  @Override public String toString() {
    RollupStats rs = RollupStats.getOrNull(this);
    String s = "["+length()+(rs == null ? ", {" : ","+rs._min+"/"+rs._mean+"/"+rs._max+", "+PrettyPrint.bytes(rs._size)+", {");
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
  @Override public Futures remove( Futures fs ) {
    for( int i=0; i<nChunks(); i++ )
      DKV.remove(chunkKey(i),fs);
    DKV.remove(rollupStatsKey(),fs);
    super.remove(fs);
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
  private Vec align(final Vec vec) {
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
    if( domain.length > MAX_ENUM_SIZE ) 
      throw new IllegalArgumentException("Column domain is too large to be represented as an enum: " + domain.length + " > " + MAX_ENUM_SIZE);
    return this.makeSimpleTransf(domain, ArrayUtils.toString(domain));
  }

  /** Creates a new transformation from given values to given indexes of given domain.
   *  @param values values being mapped from
   *  @param indexes values being mapped to
   *  @param domain domain of new vector
   *  @return always return a new vector which maps given values into a new domain
   */
  Vec makeTransf(final int[] values, final int[] indexes, final String[] domain) {
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
    throw H2O.unimpl();
    //return makeTransf(seq(0, _domain.length), null, _domain);
  }

  /** Makes a new transformation vector from given values to values 0..domain size
   *  @param values values which are mapped from
   *  @param domain target domain which is mapped to
   *  @return a new transformation vector providing mapping between given values and target domain.
   *  @see Vec#makeTransf(int[], int[], String[])
   */
  Vec makeSimpleTransf(long[] values, String[] domain) {
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
    public static VectorGroup VG_LEN1 = new VectorGroup();
    final int _len;
    final Key _key;
    private VectorGroup(Key key, int len){_key = key;_len = len;}
    private VectorGroup() {
      byte[] bits = new byte[26];
      bits[0] = Key.VGROUP;
      bits[1] = -1;
      UDP.set4(bits, 2, -1);
      UDP.set4(bits, 6, -1);
      UUID uu = UUID.randomUUID();
      UDP.set8(bits,10,uu.getLeastSignificantBits());
      UDP.set8(bits,18,uu. getMostSignificantBits());
      _key = Key.make(bits);
      _len = 0;
    }
    public Key vecKey(int vecId) {
      byte [] bits = _key._kb.clone();
      bits[0] = Key.VEC;
      UDP.set4(bits,2,vecId);//
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
    private Key [] addVecs(final int n){
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
    transient NonBlockingHashMapLong<Object> _uniques;
    @Override protected void setupLocal() { _uniques = new NonBlockingHashMapLong(); }
    @Override public void map(Chunk ys) {
      for( int row=0; row<ys._len; row++ )
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
      _uniques = new NonBlockingHashMapLong();
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
