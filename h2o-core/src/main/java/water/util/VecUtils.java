package water.util;

import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OIllegalValueException;
import water.fvec.C0DChunk;
import water.fvec.Chunk;
import water.fvec.NewChunk;
import water.fvec.Vec;
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
  public static Vec toCategoricalVec(Vec src) {
    switch (src.get_type()) {
      case Vec.T_CAT:
        return src.makeCopy(src.domain());
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
        throw new H2OIllegalArgumentException("Unrecognized column type " + src.get_type_str()
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
  public static Vec stringToCategorical(Vec vec) {
    final String[] vecDomain = new CollectStringVecDomain().domain(vec);

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
            nc.addNum(lookupTable.get(bs.toSanitizedString()), 0);
          }
        }
      }
    };
    // Invoke tasks - one input vector, one ouput vector
    task.doAll(new byte[] {Vec.T_CAT}, vec);
    // Return result
    return task.outputFrame(null, null, new String[][] {vecDomain}).vec(0);
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
  public static Vec numericToCategorical(Vec src) {
    if (src.isInt()) {
      int min = (int) src.min(), max = (int) src.max();
      // try to do the fast domain collection
      long dom[] = (min >= 0 && max < Integer.MAX_VALUE - 4) ? new CollectDomainFast(max).doAll(src).domain() : new CollectIntegerDomain().doAll(src).domain();
      if (dom.length > Categorical.MAX_CATEGORICAL_COUNT)
        throw new H2OIllegalArgumentException("Column domain is too large to be represented as an categorical: " + dom.length + " > " + Categorical.MAX_CATEGORICAL_COUNT);
      return copyOver(src, Vec.T_CAT, dom);
    } else if(src.isNumeric()){
      final double [] dom = new CollectDoubleDomain(null,10000).doAll(src).domain();
      String [] strDom = new String[dom.length];
      for(int i = 0; i < dom.length; ++i)
        strDom[i] = String.valueOf(dom[i]);
      Vec dst = src.makeZero(strDom);
      new MRTask(){
        @Override public void map(Chunk c0, Chunk c1){
          for(int r = 0; r < c0._len; ++r){
            double d = c0.atd(r);
            if(Double.isNaN(d))
              c1.setNA(r);
            else
              c1.set(r,Arrays.binarySearch(dom,d));
          }
        }
      }.doAll(new Vec[]{src,dst});
      assert dst.min() == 0;
      assert dst.max() == dom.length-1;
      return dst;
    } else throw new IllegalArgumentException("calling numericToCategorical conversion on a non numeric column");
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
  public static Vec toNumericVec(Vec src) {
    switch (src.get_type()) {
      case Vec.T_CAT:
        return categoricalToInt(src);
      case Vec.T_STR:
        return stringToNumeric(src);
      case Vec.T_NUM:
      case Vec.T_TIME:
      case Vec.T_UUID:
        return src.makeCopy(null, Vec.T_NUM);
      default:
        throw new H2OIllegalArgumentException("Unrecognized column type " + src.get_type_str()
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
  public static Vec stringToNumeric(Vec src) {
    if(!src.isString()) throw new H2OIllegalArgumentException("stringToNumeric conversion only works on string columns");
    Vec res = new MRTask() {
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
    }.doAll(Vec.T_NUM, src).outputFrame().anyVec();
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
  public static Vec categoricalToInt(final Vec src) {
    if( src.isInt() && (src.domain()==null || src.domain().length == 0)) return copyOver(src, Vec.T_NUM, null);
    if( !src.isCategorical() ) throw new IllegalArgumentException("categoricalToInt conversion only works on categorical columns.");
    // check if the 1st lvl of the domain can be parsed as int
    boolean useDomain=false;
    Vec newVec = copyOver(src, Vec.T_NUM, null);
    try {
      Integer.parseInt(src.domain()[0]);
      useDomain=true;
    } catch (NumberFormatException e) {
      // makeCopy and return...
    }
    if( useDomain ) {
      new MRTask() {
        @Override public void map(Chunk c) {
          for (int i=0;i<c._len;++i)
            if( !c.isNA(i) )
              c.set(i, Integer.parseInt(src.domain()[(int)c.at8(i)]));
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
  public static Vec toStringVec(Vec src) {
    switch (src.get_type()) {
      case Vec.T_STR:
        return src.makeCopy();
      case Vec.T_CAT:
        return categoricalToStringVec(src);
      case Vec.T_UUID:
        return UUIDToStringVec(src);
      case Vec.T_TIME:
      case Vec.T_NUM:
        return numericToStringVec(src);
      default:
        throw new H2OIllegalArgumentException("Unrecognized column type " + src.get_type_str()
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
  public static Vec categoricalToStringVec(Vec src) {
    if( !src.isCategorical() )
      throw new H2OIllegalValueException("Can not convert a non-categorical column"
          + " using categoricalToStringVec().",src);
    return new Categorical2StrChkTask(src.domain()).doAll(Vec.T_STR,src).outputFrame().anyVec();
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
  public static Vec numericToStringVec(Vec src) {
    if (src.isCategorical() || src.isUUID())
      throw new H2OIllegalValueException("Cannot convert a non-numeric column"
          + " using numericToStringVec() ",src);
    Vec res = new MRTask() {
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
    }.doAll(Vec.T_STR, src).outputFrame().anyVec();
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
  public static Vec UUIDToStringVec(Vec src) {
    if( !src.isUUID() ) throw new H2OIllegalArgumentException("UUIDToStringVec() conversion only works on UUID columns");
    Vec res = new MRTask() {
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
    }.doAll(Vec.T_STR,src).outputFrame().anyVec();
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
  public static Vec categoricalDomainsToNumeric(final Vec src) {
    if( !src.isCategorical() ) throw new H2OIllegalArgumentException("categoricalToNumeric() conversion only works on categorical columns");
    // check if the 1st lvl of the domain can be parsed as int
    return new MRTask() {
        @Override public void map(Chunk c) {
          for (int i=0;i<c._len;++i)
            if( !c.isNA(i) )
              c.set(i, Integer.parseInt(src.domain()[(int)c.at8(i)]));
        }
      }.doAll(Vec.T_NUM, src).outputFrame().anyVec();
  }

  public static class CollectDoubleDomain extends MRTask<CollectDoubleDomain> {

    final double [] _sortedKnownDomain;
    private IcedHashMap<IcedDouble,IcedInt> _uniques; // new uniques
    final int _maxDomain;
    final IcedInt _placeHolder = new IcedInt(1);

    public CollectDoubleDomain(double [] knownDomain, int maxDomainSize) {
      _maxDomain = maxDomainSize;
      _sortedKnownDomain = knownDomain == null?null:knownDomain.clone();
      if(_sortedKnownDomain != null && !ArrayUtils.isSorted(knownDomain))
        Arrays.sort(_sortedKnownDomain);
    }

    @Override public void setupLocal(){
      _uniques = new IcedHashMap<>();
    }

    public double [] domain(){
      double [] res = MemoryManager.malloc8d(_uniques.size());
      int i = 0;
      for(IcedDouble v:_uniques.keySet())
        res[i++] = v._val;
      Arrays.sort(res);
      return res;
    }
    private IcedDouble addValue(IcedDouble val){
      if(Double.isNaN(val._val)) return val;
      if(_sortedKnownDomain != null && Arrays.binarySearch(_sortedKnownDomain,val._val) >= 0)
        return val; // already known value
      if (!_uniques.containsKey(val)) {
        _uniques.put(val,_placeHolder);
        val = new IcedDouble(0);
        if(_uniques.size() > _maxDomain)
          throw new RuntimeException("Too many unique values. Expected |uniques| < " + _maxDomain + ", already got " + _uniques.size());
      }
      return val;
    }
    @Override public void map(Chunk ys) {
      IcedDouble val = new IcedDouble(0);
      for( int row=ys.nextNZ(-1); row< ys._len; row = ys.nextNZ(row) )
        val = addValue(val.setVal(ys.atd(row)));
      if(ys.isSparseZero())
        addValue(val.setVal(0));
    }
    @Override public void reduce(CollectDoubleDomain mrt) {
      if( _uniques != mrt._uniques ) _uniques.putAll(mrt._uniques);
      if(_uniques.size() > _maxDomain)
        throw new RuntimeException("Too many unique values. Expected |uniques| < " + _maxDomain + ", already got " + _uniques.size());
    }
  }

  /** Collect numeric domain of given {@link Vec}
   *  A map-reduce task to collect up the unique values of an integer {@link Vec}
   *  and returned as the domain for the {@link Vec}.
   * */
  public static class CollectIntegerDomain extends MRTask<CollectIntegerDomain> {
    transient NonBlockingHashMapLong<String> _uniques;
    @Override protected void setupLocal() { _uniques = new NonBlockingHashMapLong<>(); }
    @Override public void map(Chunk ys) {
      for( int row=0; row< ys._len; row++ )
        if( !ys.isNA(row) )
          _uniques.put(ys.at8(row), "");
    }

    @Override public void reduce(CollectIntegerDomain mrt) {
      if( _uniques != mrt._uniques ) _uniques.putAll(mrt._uniques);
    }

    public final AutoBuffer write_impl( AutoBuffer ab ) {
      return ab.putA8(_uniques==null ? null : _uniques.keySetLong());
    }

    public final CollectIntegerDomain read_impl(AutoBuffer ab ) {
      long ls[] = ab.getA8();
      assert _uniques == null || _uniques.size()==0; // Only receiving into an empty (shared) NBHM
      _uniques = new NonBlockingHashMapLong<>();
      if( ls != null ) for( long l : ls ) _uniques.put(l, "");
      return this;
    }
    @Override public final void copyOver(CollectIntegerDomain that) {
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
    public static Vec domainDeduper(Vec vec, HashMap<String, ArrayList<Integer>> substringToOldDomainIndices) {
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
      return domainDedupe.doAll(new byte[]{Vec.T_CAT}, vec).outputFrame(null, null, dom2D).anyVec();
    }
  }

  // >11x faster than CollectIntegerDomain
  /** (Optimized for positive ints) Collect numeric domain of given {@link Vec}
   *  A map-reduce task to collect up the unique values of an integer {@link Vec}
   *  and returned as the domain for the {@link Vec}.
   * */
  public static class CollectDomainFast extends MRTask<CollectDomainFast> {
    private final int _s;
    private boolean[] _u;
    private long[] _d;
    public CollectDomainFast(int s) { _s=s; }
    @Override protected void setupLocal() { _u= MemoryManager.mallocZ(_s + 1); }
    @Override public void map(Chunk ys) {
      for( int row=0; row< ys._len; row++ )
        if( !ys.isNA(row) )
          _u[(int)ys.at8(row)]=true;
    }
    @Override public void reduce(CollectDomainFast mrt) { if( _u != mrt._u ) ArrayUtils.or(_u, mrt._u);}
    @Override protected void postGlobal() {
      int c=0;
      for (boolean b : _u) if(b) c++;
      _d=MemoryManager.malloc8(c);
      int id=0;
      for (int i = 0; i < _u.length;++i)
        if (_u[i])
          _d[id++]=i;
      Arrays.sort(_d); //is this necessary? 
    }

    /** Returns exact numeric domain of given {@link Vec} computed by this task.
     * The domain is always sorted. Hence:
     *    domain()[0] - minimal domain value
     *    domain()[domain().length-1] - maximal domain value
     */
    public long[] domain() { return _d; }
  }

  public static void deleteVecs(Vec[] vs, int cnt) {
    Futures f = new Futures();
    for (int i =0; i < cnt; i++) vs[cnt].remove(f);
    f.blockForPending();
  }

  private static Vec copyOver(Vec src, byte type, long[] domain) {
    String[][] dom = new String[1][];
    dom[0]=domain==null?null:ArrayUtils.toString(domain);
    return new CPTask(domain).doAll(type, src).outputFrame(null,dom).anyVec();
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
          _uniques.put(bs.toSanitizedString(), _placeHolder);
        }
      }
    }

    @Override
    public void reduce(CollectStringVecDomain mrt) {
      if (_uniques != mrt._uniques) { // this is not local reduce
        _uniques.putAll(mrt._uniques);
      }
    }

    public String[] domain(Vec vec) {
      assert vec.isString() : "String vector expected. Unsupported vector type: " + vec.get_type_str();
      this.doAll(vec);
      return domain();
    }

    public String[] domain() {
      String[] dom = _uniques.keySet().toArray(new String[_uniques.size()]);
      Arrays.sort(dom);
      return dom;
    }
  }
  public static int [] getLocalChunkIds(Vec v){
    if(v._cids != null) return v._cids;
    int [] res = new int[Math.max(v.nChunks()/H2O.CLOUD.size(),1)];
    int j = 0;
    for(int i = 0; i < v.nChunks(); ++i){
      if(v.isHomedLocally(i)) {
        if(res.length == j) res = Arrays.copyOf(res,2*res.length);
        res[j++] = i;
      }
    }
    return (v._cids = j == res.length?res:Arrays.copyOf(res,j));
  }

  /**
   * Compute the mean (weighted) response per categorical level
   * Skip NA values (those are already a separate bucket in the tree building histograms, for which this is designed)
   */
  public static class MeanResponsePerLevelTask extends MRTask<MeanResponsePerLevelTask> {
    // OUTPUT
    public double[] meanWeightedResponse;
    public double meanOverallWeightedResponse;

    // Internal
    private double[] wcounts;
    private int _len;
    public MeanResponsePerLevelTask(int len) {
      _len = len;
    }
    @Override
    public void map(Chunk c, Chunk w, Chunk r) {
      wcounts = new double[_len]; // no larger than 1M elements, so OK to replicate per thread (faster)
      meanWeightedResponse = new double[_len];
      for (int i=0; i<c._len; ++i) {
        if (c.isNA(i)) continue;
        int level = (int)c.at8(i);
        if (w.isNA(i)) continue;
        double weight = w.atd(i);
        if (weight == 0) continue;
        if (r.isNA(i)) continue;
        double response = r.atd(i);
        wcounts[level] += weight;
        meanWeightedResponse[level] += weight*response;
      }
    }

    @Override
    public void reduce(MeanResponsePerLevelTask mrt) {
      ArrayUtils.add(wcounts, mrt.wcounts);
      ArrayUtils.add(meanWeightedResponse, mrt.meanWeightedResponse);
      mrt.wcounts = null;
      mrt.meanWeightedResponse = null;
    }

    @Override
    protected void postGlobal() {
      meanOverallWeightedResponse = 0;
      double sum = 0;
      for (int i = 0; i< meanWeightedResponse.length; ++i) {
        if (wcounts[i] != 0) {
          meanWeightedResponse[i] = meanWeightedResponse[i] / wcounts[i];
          meanOverallWeightedResponse += meanWeightedResponse[i];
          sum += wcounts[i];
        }
      }
      meanOverallWeightedResponse /= sum;
    }
  }

  /**
   * Reorder an integer (such as Enum storage) Vec using an int -> int mapping
   */
  public static class ReorderTask extends MRTask<ReorderTask> {
    private int[] _map;
    public ReorderTask(int[] mapping) { _map = mapping; }
    @Override
    public void map(Chunk c, NewChunk nc) {
      for (int i=0;i<c._len;++i) {
        if (c.isNA(i)) nc.addNA();
        else nc.addNum(_map[(int)c.at8(i)], 0);
      }
    }
  }

}
