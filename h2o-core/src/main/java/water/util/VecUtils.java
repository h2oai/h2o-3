package water.util;

import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OIllegalValueException;
import water.fvec.*;
import water.nbhm.NonBlockingHashMap;
import water.nbhm.NonBlockingHashMapLong;
import water.parser.BufferedString;
import water.parser.Categorical;

import java.util.*;

public class VecUtils {


  /**
   * Create a new {@link Vec} of categorical values from an existing {@link Vec}.
   *
   * This method accepts all {@link Vec} types as input. The original Vec is not mutated.
   *
   * If src is a categorical {@link Vec}, a copy is returned.
   *
   * If src is a numeric {@link Vec}, the values are converted to strings used as domain
   * values.
   *
   * For all other types, an exception is currently thrown. These need to be replaced
   * with appropriate conversions.
   *
   * Throws H2OIllegalArgumentException() if the resulting domain exceeds
   * Categorical.MAX_CATEGORICAL_COUNT.
   *
   *  @param src A {@link Vec} whose values will be used as the basis for a new categorical {@link Vec}
   *  @return the resulting categorical Vec
   */
  public static VecAry toCategoricalVec(VecAry src) {
    if(src.len() != 1) throw new IllegalArgumentException();
    switch (src.type(0)) {
      case Vec.T_CAT:
        return src.makeCopy(src.domain(0));
      case Vec.T_NUM:
        return numericToCategorical(src);
      case Vec.T_STR: // PUBDEV-2204
        return stringToCategorical(src);
      case Vec.T_TIME: // PUBDEV-2205
        throw new H2OIllegalArgumentException("Changing time/date columns to a categorical"
            + " column has not been implemented yet.");
      case Vec.T_UUID:
        throw new H2OIllegalArgumentException("Changing UUID columns to a categorical"
            + " column has not been implemented yet.");
      default:
        throw new H2OIllegalArgumentException("Unrecognized column type " + Vec.TYPE_STR[src.type(0)]
            + " given to toCategoricalVec()");
    }
  }

  /**
   * Create a new {@link Vec} of categorical values from string {@link Vec}.
   *
   * FIXME: implement in more efficient way with Brandon's primitives for BufferedString manipulation
   *
   * @param vec a string {@link Vec}
   * @return a categorical {@link Vec}
   */
  public static VecAry stringToCategorical(VecAry vec) {
    assert vec.len() == 1;
    final String[] vecDomain = new CollectStringVecDomain().doAll(vec).domain();
    MRTask task = new MRTask() {
      transient private java.util.HashMap<String, Integer> lookupTable;
      @Override
      protected void setupLocal() {
        lookupTable = new java.util.HashMap<>(vecDomain.length);
        for (int i = 0; i < vecDomain.length; i++) {
          // FIXME: boxing
          lookupTable.put(vecDomain[i], i);
        }
      }

      @Override
      public void map(Chunk c, NewChunk nc) {
        BufferedString bs = new BufferedString();
        for (int row = 0; row < c.len(); row++) {
          if (c.isNA(row)) {
            nc.addNA();
          } else {
            c.atStr(bs, row);
            nc.addCategorical(lookupTable.get(bs.bytesToString()));
          }
        }
      }
    };
    // Invoke tasks - one input vector, one ouput vector
    task.doAll(new byte[] {Vec.T_CAT}, vec);
    // Return result
    return task.outputFrame(null, (String[])null, new String[][] {vecDomain}).vecs();
  }

  /**
   * Create a new {@link Vec} of categorical values from a numeric {@link Vec}.
   *
   * This currently only ingests a {@link Vec} of integers.
   *
   * Handling reals is PUBDEV-2207
   *
   * @param src a numeric {@link Vec}
   * @return a categorical {@link Vec}
   */
  public static VecAry numericToCategorical(VecAry src) {
    if(src.len() != 1)
      throw new IllegalArgumentException();
    if (src.isInt(0)) {
      int min = (int) src.min(0), max = (int) src.max(0);
      // try to do the fast domain collection
      long dom[] = (min >= 0 && max < Integer.MAX_VALUE - 4) ? new CollectDomainFast().doAll(src).domain()[0] : new CollectDomain().doAll(src).domain();
      if (dom.length > Categorical.MAX_CATEGORICAL_COUNT)
        throw new H2OIllegalArgumentException("Column domain is too large to be represented as an categorical: " + dom.length + " > " + Categorical.MAX_CATEGORICAL_COUNT);
      return copyOver(src, Vec.T_CAT, dom);
    } else throw new H2OIllegalArgumentException("Categorical conversion can only currently be applied to integer columns.");
  }

  /**
   * Create a new {@link Vec} of numeric values from an existing {@link Vec}.
   *
   * This method accepts all {@link Vec} types as input. The original Vec is not mutated.
   *
   * If src is a categorical {@link Vec}, a copy is returned.
   *
   * If src is a string {@link Vec}, all values that can be are parsed into reals or integers, and all
   * others become NA. See stringToNumeric for parsing details.
   *
   * If src is a numeric {@link Vec}, a copy is made.
   *
   * If src is a time {@link Vec}, the milliseconds since the epoch are used to populate the new Vec.
   *
   * If src is a UUID {@link Vec}, the existing numeric storage is used to populate the new Vec.
   *
   * Throws H2OIllegalArgumentException() if the resulting domain exceeds
   * Categorical.MAX_CATEGORICAL_COUNT.
   *
   *  @param src A {@link Vec} whose values will be used as the basis for a new numeric {@link Vec}
   *  @return the resulting numeric {@link Vec}
   */
  public static VecAry toNumericVec(VecAry src) {
    if(src.len() != 1) throw new IllegalArgumentException();
    switch (src.type(0)) {
      case Vec.T_CAT:
        return categoricalToInt(src);
      case Vec.T_STR:
        return stringToNumeric(src);
      case Vec.T_NUM:
      case Vec.T_TIME:
      case Vec.T_UUID:
        return src.makeCopy(null, Vec.T_NUM);
      default:
        throw new H2OIllegalArgumentException("Unrecognized column type " + Vec.TYPE_STR[src.type(0)]
            + " given to toNumericVec()");
    }
  }

  /**
   * Create a new {@link Vec} of numeric values from a string {@link Vec}. Any rows that cannot be
   * converted to a number are set to NA.
   *
   * Currently only does basic numeric formats. No exponents, or hex values. Doesn't
   * even like commas or spaces.  :( Needs love. Handling more numeric
   * representations is PUBDEV-2209
   *
   * @param src a string {@link Vec}
   * @return a numeric {@link Vec}
   */
  public static VecAry stringToNumeric(VecAry src) {
    if(src.len() != 1) throw new IllegalArgumentException();
    if(!src.isString(0)) throw new H2OIllegalArgumentException("stringToNumeric conversion only works on string columns");
    VecAry res = new MRTask() {
      @Override public void map(Chunk chk, NewChunk newChk){
        if (chk instanceof C0DChunk) { // all NAs
          for (int i=0; i < chk._len; i++)
            newChk.addNA();
        } else {
          BufferedString tmpStr = new BufferedString();
          for (int i=0; i < chk._len; i++) {
            if (!chk.isNA(i)) {
              tmpStr = chk.atStr(tmpStr, i);
              switch (tmpStr.getNumericType()) {
                case BufferedString.NA:
                  newChk.addNA(); break;
                case BufferedString.INT:
                  newChk.addNum(Long.parseLong(tmpStr.toString()),0); break;
                case BufferedString.REAL:
                  newChk.addNum(Double.parseDouble(tmpStr.toString())); break;
                default:
                  throw new H2OIllegalValueException("Received unexpected type when parsing a string to a number.", this);
              }
            } else newChk.addNA();
          }
        }
      }
    }.doAll(1,Vec.T_NUM, src).outputFrame().vecs();
    assert res != null;
    return res;
  }

  /**
   * Create a new {@link Vec} of numeric values from a categorical {@link Vec}.
   *
   * If the first value in the domain of the src Vec is a stringified ints,
   * then it will use those ints. Otherwise, it will use the raw enumeration level mapping.
   * If the domain is stringified ints, then all of the domain must be able to be parsed as
   * an int. If it cannot be parsed as such, a NumberFormatException will be caught and
   * rethrown as an H2OIllegalArgumentException that declares the illegal domain value.
   * Otherwise, the this pointer is copied to a new Vec whose domain is null.
   *
   * The magic of this method should be eliminated. It should just use enumeration level
   * maps. If the user wants domains to be used, call categoricalDomainsToNumeric().
   * PUBDEV-2209
   *
   * @param src a categorical {@link Vec}
   * @return a numeric {@link Vec}
   */
  public static VecAry categoricalToInt(final VecAry src) {
    if(src.len() != 1)
      throw new IllegalArgumentException();
    if( src.isInt(0) && (src.domain(0)==null || src.domain(0).length == 0)) return copyOver(src, Vec.T_NUM, null);
    if( !src.isCategorical(0) ) throw new IllegalArgumentException("categoricalToInt conversion only works on categorical columns.");
    // check if the 1st lvl of the domain can be parsed as int
    boolean useDomain=false;
    VecAry newVec = copyOver(src, Vec.T_NUM, null);
    try {
      Integer.parseInt(src.domain(0)[0]);
      useDomain=true;
    } catch (NumberFormatException e) {
      // makeCopy and return...
    }
    if( useDomain ) {
      new MRTask() {
        @Override public void map(Chunk c) {
          for (int i=0;i<c._len;++i)
            if( !c.isNA(i) )
              c.set(i, Integer.parseInt(src.domain(0)[(int)c.at8(i)]));
        }
      }.doAll(newVec);
    }
    return newVec;
  }

  /**
   * Create a new {@link Vec} of string values from an existing {@link Vec}.
   *
   * This method accepts all {@link Vec} types as input. The original Vec is not mutated.
   *
   * If src is a string {@link Vec}, a copy of the {@link Vec} is made.
   *
   * If src is a categorical {@link Vec}, levels are dropped, and the {@link Vec} only records the string.
   *
   * For all numeric {@link Vec}s, the number is converted to a string.
   *
   * For all UUID {@link Vec}s, the hex representation is stored as a string.
   *
   *  @param src A {@link Vec} whose values will be used as the basis for a new string {@link Vec}
   *  @return the resulting string {@link Vec}
   */
  public static VecAry toStringVec(VecAry src) {
    if(src.len() != 1) throw new IllegalArgumentException();
    switch (src.type(0)) {
      case Vec.T_STR:
        return src.makeCopy((String[])null);
      case Vec.T_CAT:
        return categoricalToStringVec(src);
      case Vec.T_UUID:
        return UUIDToStringVec(src);
      case Vec.T_TIME:
      case Vec.T_NUM:
        return numericToStringVec(src);
      default:
        throw new H2OIllegalArgumentException("Unrecognized column type " + src.typesStr()
            + " given to toStringVec().");
    }
  }

  /**
   * Create a new {@link Vec} of string values from a categorical {@link Vec}.
   *
   * Transformation is done by a {@link Categorical2StrChkTask} which provides a mapping
   *  between values - without copying the underlying data.
   *
   * @param src a categorical {@link Vec}
   * @return a string {@link Vec}
   */
  public static VecAry categoricalToStringVec(VecAry src) {
    if(src.len() != 1) throw new IllegalArgumentException();
    if( !src.isCategorical(0) )
      throw new H2OIllegalValueException("Can not convert a non-categorical column"
          + " using categoricalToStringVec().",src);
    return new Categorical2StrChkTask(src.domain(0)).doAll(1,Vec.T_STR,src).outputFrame().vecs();
  }

  private static class Categorical2StrChkTask extends MRTask<Categorical2StrChkTask> {
    final String[] _domain;
    Categorical2StrChkTask(String[] domain) { _domain=domain; }
    @Override public void map(Chunk c, NewChunk nc) {
      for(int i=0;i<c._len;++i)
        if (!c.isNA(i))
          nc.addStr(_domain == null ? "" + c.at8(i) : _domain[(int) c.at8(i)]);
        else
          nc.addNA();
    }
  }

  /**
   * Create a new {@link Vec} of string values from a numeric {@link Vec}.
   *
   * Currently only uses a default pretty printer. Would be better if
   * it accepted a format string PUBDEV-2211
   *
   * @param src a numeric {@link Vec}
   * @return a string {@link Vec}
   */
  public static VecAry numericToStringVec(VecAry src) {
    if(src.len() != 1) throw new IllegalArgumentException();
    if (src.isCategorical(0) || src.isUUID(0))
      throw new H2OIllegalValueException("Cannot convert a non-numeric column"
          + " using numericToStringVec() ",src);
    VecAry res = new MRTask() {
      @Override
      public void map(Chunk chk, NewChunk newChk) {
        if (chk instanceof C0DChunk) { // all NAs
          for (int i=0; i < chk._len; i++)
            newChk.addNA();
        } else {
          for (int i=0; i < chk._len; i++) {
            if (!chk.isNA(i))
              newChk.addStr(PrettyPrint.number(chk, chk.atd(i), 4));
            else
              newChk.addNA();
          }
        }
      }
    }.doAll(1,Vec.T_STR, src).outputFrame().vecs();
    assert res != null;
    return res;
  }

  /**
   * Create a new {@link Vec} of string values from a UUID {@link Vec}.
   *
   * String {@link Vec} is the standard hexadecimal representations of a UUID.
   *
   * @param src a UUID {@link Vec}
   * @return a string {@link Vec}
   */
  public static VecAry UUIDToStringVec(VecAry src) {
    if(src.len() != 1) throw new IllegalArgumentException();
    if( !src.isUUID(0) ) throw new H2OIllegalArgumentException("UUIDToStringVec() conversion only works on UUID columns");
    VecAry res = new MRTask() {
      @Override public void map(Chunk chk, NewChunk newChk) {
        if (chk instanceof C0DChunk) { // all NAs
          for (int i=0; i < chk._len; i++)
            newChk.addNA();
        } else {
          for (int i=0; i < chk._len; i++) {
            if (!chk.isNA(i))
              newChk.addStr(PrettyPrint.UUID(chk.at16l(i), chk.at16h(i)));
            else
              newChk.addNA();
          }
        }
      }
    }.doAll(1,Vec.T_STR,src).outputFrame().vecs();
    assert res != null;
    return res;
  }

  /**
   * Create a new {@link Vec} of numeric values from a categorical {@link Vec}.
   *
   * Numeric values are generated explicitly from the domain values, and not the
   * enumeration levels. If a domain value cannot be translated as a number, that
   * domain and all values for that domain will be NA.
   *
   * @param src a categorical {@link Vec}
   * @return a numeric {@link Vec}
   */
  public static VecAry categoricalDomainsToNumeric(final VecAry src) {
    if(src.len() != 1) throw new IllegalArgumentException();
    if( !src.isCategorical(0) ) throw new H2OIllegalArgumentException("categoricalToNumeric() conversion only works on categorical columns");
    // check if the 1st lvl of the domain can be parsed as int
    return new MRTask() {
        @Override public void map(Chunk c) {
          for (int i=0;i<c._len;++i)
            if( !c.isNA(i) )
              c.set(i, Integer.parseInt(src.domain(0)[(int)c.at8(i)]));
        }
      }.doAll(1,Vec.T_NUM, src).outputFrame().vecs();
  }

  /** Collect numeric domain of given {@link Vec}
   *  A map-reduce task to collect up the unique values of an integer {@link Vec}
   *  and returned as the domain for the {@link Vec}.
   * */
  public static class CollectDomain extends MRTask<CollectDomain> {
    transient NonBlockingHashMapLong<String> _uniques;
    @Override protected void setupLocal() { _uniques = new NonBlockingHashMapLong<>(); }
    @Override public void map(Chunk ys) {
      for( int row=0; row< ys._len; row++ )
        if( !ys.isNA(row) )
          _uniques.put(ys.at8(row), "");
    }

    @Override public void reduce(CollectDomain mrt) {
      if( _uniques != mrt._uniques ) _uniques.putAll(mrt._uniques);
    }

    public final AutoBuffer write_impl( AutoBuffer ab ) {
      return ab.putA8(_uniques==null ? null : _uniques.keySetLong());
    }

    public final CollectDomain read_impl( AutoBuffer ab ) {
      long ls[] = ab.getA8();
      assert _uniques == null || _uniques.size()==0; // Only receiving into an empty (shared) NBHM
      _uniques = new NonBlockingHashMapLong<>();
      if( ls != null ) for( long l : ls ) _uniques.put(l, "");
      return this;
    }
    @Override public final void copyOver(CollectDomain that) {
      _uniques = that._uniques;
    }

    /** Returns exact numeric domain of given {@link Vec} computed by this task.
     * The domain is always sorted. Hence:
     *    domain()[0] - minimal domain value
     *    domain()[domain().length-1] - maximal domain value
     */
    public long[] domain() {
      long[] dom = _uniques.keySetLong();
      Arrays.sort(dom);
      return dom;
    }
  }

  /**
   * Create a new categorical {@link Vec} with deduplicated domains from a categorical {@link Vec}.
   * 
   * Categoricals may have the same values after munging, and should have the same domain index in the numerical chunk 
   * representation. Unify categoricals that are the same by remapping their domain indices. 
   * 
   * Could be more efficient with a vec copy and replace domain indices as needed. PUBDEV-2587
   */

  public static class DomainDedupe extends MRTask<DomainDedupe> {
    private final HashMap<Integer, Integer> _oldToNewDomainIndex;
    public DomainDedupe(HashMap<Integer, Integer> oldToNewDomainIndex) {_oldToNewDomainIndex = oldToNewDomainIndex; }
    @Override public void map(Chunk c, NewChunk nc) {
      for( int row=0; row < c._len; row++) {
        if ( !c.isNA(row) ) {
          int oldDomain = (int) c.at8(row);
          nc.addNum(_oldToNewDomainIndex.get(oldDomain));
        } else {
          nc.addNA();
        }
      }
    }
    public static VecAry domainDeduper(VecAry vec, HashMap<String, ArrayList<Integer>> substringToOldDomainIndices) {
      HashMap<Integer, Integer> oldToNewDomainIndex = new HashMap<>();
      int newDomainIndex = 0;
      SortedSet<String> alphabetizedSubstrings = new TreeSet<>(substringToOldDomainIndices.keySet());
      for (String sub : alphabetizedSubstrings) {
        for (int oldDomainIndex : substringToOldDomainIndices.get(sub)) {
          oldToNewDomainIndex.put(oldDomainIndex, newDomainIndex);
        }
        newDomainIndex++;
      }
      VecUtils.DomainDedupe domainDedupe = new VecUtils.DomainDedupe(oldToNewDomainIndex);
      String[][] dom2D = {Arrays.copyOf(alphabetizedSubstrings.toArray(), alphabetizedSubstrings.size(), String[].class)};
      return domainDedupe.doAll(new byte[]{Vec.T_CAT}, vec).outputFrame(null, (String[])null, dom2D).vecs();
    }
  }

  // >11x faster than CollectDomain
  /** (Optimized for positive ints) Collect numeric domain of given {@link Vec}
   *  A map-reduce task to collect up the unique values of an integer {@link Vec}
   *  and returned as the domain for the {@link Vec}.
   * */
  public static class CollectDomainFast extends MRTask<CollectDomainFast> {
    private boolean[][] _u;
    private long[][] _d;

    @Override protected void setupLocal() {
      for(int i = 0; i < _vecs.len(); ++i)
        _u[i] = new boolean[(int)_vecs.max(i)];
    }
    @Override public void map(Chunk [] chks) {
      for(int i = 0 ; i < chks.length; ++i) {
        Chunk ys = chks[i];
        boolean [] u = _u[i];
        for (int row = 0; row < ys._len; row++)
          if (!ys.isNA(row))
            u[(int) ys.at8(row)] = true;
      }
    }
    @Override public void reduce(CollectDomainFast mrt) {
      if( _u != mrt._u ) {
        for(int i = 0; i < _u.length; ++i)
          ArrayUtils.or(_u[i], mrt._u[i]);
      }
    }
    @Override protected void postGlobal() {
      for(int i = 0; i < _vecs.len(); ++i) {
        int c=0;
        for (boolean b : _u[i]) if (b) c++;
        _d[i] = MemoryManager.malloc8(c);
        int id = 0;
        for (int j = 0; j < _u.length; ++j)
          if (_u[i][j])
            _d[i][id++] = j;
        Arrays.sort(_d[i]); //is this necessary?
      }
    }

    /** Returns exact numeric domain of given {@link Vec} computed by this task.
     * The domain is always sorted. Hence:
     *    domain()[0] - minimal domain value
     *    domain()[domain().length-1] - maximal domain value
     */
    public long[][] domain() { return _d; }
  }

  public static void deleteVecs(Vec[] vs, int cnt) {
    Futures f = new Futures();
    for (int i =0; i < cnt; i++) vs[cnt].remove(f);
    f.blockForPending();
  }

  private static VecAry copyOver(VecAry src, byte type, long[] domain) {
    String[][] dom = new String[1][];
    dom[0]=domain==null?null:ArrayUtils.toString(domain);
    return new CPTask(domain).doAll(1,type, src).outputFrame(null,dom).vecs();
  }

  private static class CPTask extends MRTask<CPTask> {
    private final long[] _domain;
    CPTask(long[] domain) { _domain = domain;}
    @Override public void map(Chunk c, NewChunk nc) {
      for(int i=0;i<c._len;++i) {
        if( c.isNA(i) ) { nc.addNA(); continue; }
        if( _domain == null )
          nc.addNum(c.at8(i));
        else {
          long num = Arrays.binarySearch(_domain,c.at8(i));  // ~24 hits in worst case for 10M levels
          if( num < 0 )
            throw new IllegalArgumentException("Could not find the categorical value!");
          nc.addNum(num);
        }
      }
    }
  }

  private static class CollectStringVecDomain extends MRTask<CollectStringVecDomain> {

    private static String PLACEHOLDER = "nothing";

    private IcedHashMap<String, IcedInt> _uniques = null;

    private final IcedInt _placeHolder = new IcedInt(1);
    @Override
    protected void setupLocal() {
      _uniques = new IcedHashMap<>();
    }

    @Override
    public void map(Chunk c) {
      BufferedString bs = new BufferedString();
      for (int i = 0; i < c.len(); i++) {
        if (!c.isNA(i)) {
          c.atStr(bs, i);
          _uniques.put(bs.bytesToString(), _placeHolder);
        }
      }
    }

    @Override
    public void reduce(CollectStringVecDomain mrt) {
      if (_uniques != mrt._uniques) { // this is not local reduce
        _uniques.putAll(mrt._uniques);
      }
    }

    public String[] domain() {
      String[] dom = _uniques.keySet().toArray(new String[_uniques.size()]);
      Arrays.sort(dom);
      return dom;
    }
  }

  public static AVec makeCon(final long l, String[] domain, AVec.VectorGroup group, int rowLayout ) {
    final AVec v0 = new AVec(group.addVec(), rowLayout, domain);
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

  public static AVec makeCon(final double d, String[] domain, AVec.VectorGroup group, int rowLayout ) {
    final AVec v0 = new AVec(group.addVec(), rowLayout, domain);
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

  public static AVec makeVec(double [] vals, Key<AVec> vecKey){
    return makeVec(vals,vecKey,null);
  }

  public static AVec makeVec(double [] vals, Key<AVec> vecKey, String [] domain){
    AVec v = new AVec(vecKey, AVec.ESPC.rowLayout(vecKey,new long[]{0,vals.length}), domain);
    NewChunk nc;
    AVec.ChunkAry sc = new AVec.ChunkAry(v, new Chunk[]{ nc = new NewChunk()});
    Futures fs = new Futures();
    for(double d:vals)
      nc.addNum(d);
    sc.close(fs);
    DKV.put(v._key, v, fs);
    fs.blockForPending();
    return v;
  }

  public static AVec makeVec(long [] vals, Key<AVec> vecKey){
    return makeVec(vals,vecKey,null);
  }
  public static AVec makeVec(long [] vals, Key<AVec> vecKey, String [] domain){
    AVec v = new AVec(vecKey, AVec.ESPC.rowLayout(vecKey, new long[]{0, vals.length}), domain);
    NewChunk nc;
    AVec.ChunkAry sc = new AVec.ChunkAry(v, new Chunk[]{ nc = new NewChunk()});
    Futures fs = new Futures();
    for(long l:vals)
      nc.addNum(l,0);
    sc.close(fs);
    DKV.put(v._key, v, fs);
    fs.blockForPending();
    return v;
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
    AVec.VectorGroup vg = AVec.VectorGroup.VG_LEN1;
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
    AVec.VectorGroup vg = AVec.VectorGroup.VG_LEN1;
    return makeCon(x,vg, AVec.ESPC.rowLayout(vg._key,espc));
  }



  public static AVec makeCons(double x, long len, int n) {
    Key vecKey = AVec.VectorGroup.VG_LEN1.addVec();
    byte [] types = new byte[n];
    Arrays.fill(types,AVec.T_NUM);
    AVec v = new AVec(vecKey, AVec.ESPC.rowLayout(vecKey, new long[]{0, (int)len}), null,types);
    Chunk [] cs = new Chunk[n];
    for(int i = 0; i < cs.length; ++i)
      cs[i] = new C0DChunk(x,(int)len);
    DKV.put(v.chunkKey(0),new AVec.ChunkAry(cs));
    DKV.put(v);
    return v;
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


}
