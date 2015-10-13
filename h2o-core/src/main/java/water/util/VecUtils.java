package water.util;

import java.util.Arrays;

import water.AutoBuffer;
import water.Futures;
import water.MRTask;
import water.MemoryManager;
import water.exceptions.H2OIllegalValueException;
import water.fvec.C0DChunk;
import water.fvec.Chunk;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.exceptions.H2OIllegalArgumentException;
import water.nbhm.NonBlockingHashMapLong;
import water.parser.BufferedString;
import water.parser.Categorical;

public class VecUtils {
  /** Create a new Vec (as opposed to wrapping it) that is the categorical'ified version of the original.
   *  The original Vec is not mutated.  */
  public static Vec toCategoricalVec(Vec src) {
    switch (src.get_type()) {
      case Vec.T_STR:
        throw new H2OIllegalArgumentException("Changing string columns to a categorical"
            + " column has not been implemented yet.");
        //return stringToCategorical(src);
      case Vec.T_CAT:
        return src.makeCopy(src.domain());
      case Vec.T_UUID:
        throw new H2OIllegalArgumentException("Changing UUID columns to a categorical"
            + " column has not been implemented yet.");
      case Vec.T_TIME:
        throw new H2OIllegalArgumentException("Changing time/date columns to a categorical"
            + " column has not been implemented yet.");
      case Vec.T_NUM:
        return numericToCategorical(src);
      default:
        throw new H2OIllegalArgumentException("Unrecognized column type " + src.get_type_str()
            + " given to toCategoricalVec()");
    }
  }

  public static Vec stringToCategorical(Vec src) {
    Vec res = null;
    return res;
  }

  public static Vec numericToCategorical(Vec src) {
    if (src.isInt()) {
      int min = (int) src.min(), max = (int) src.max();
      // try to do the fast domain collection
      long dom[] = (min >= 0 && max < Integer.MAX_VALUE - 4) ? new CollectDomainFast(max).doAll(src).domain() : new CollectDomain().doAll(src).domain();
      if (dom.length > Categorical.MAX_CATEGORICAL_COUNT)
        throw new H2OIllegalArgumentException("Column domain is too large to be represented as an categorical: " + dom.length + " > " + Categorical.MAX_CATEGORICAL_COUNT);
      return copyOver(src, Vec.T_CAT, dom);
    } else throw new H2OIllegalArgumentException("Categorical conversion can only currently be applied to integer columns.");
  }

  /**
   * Convert to a numeric value.
   *
   * For string columns, any row that can parse to a number does so,
   * and all others become NA.
   *
   * For enumeration columns, the levels of each category are returned.
   *
   * For all numeric columns, a copy is made.
   *
   */
  public static Vec toNumericVec(Vec src) {
    switch (src.get_type()) {
      case Vec.T_STR:
        return stringToNumeric(src);
      case Vec.T_CAT:
        return categoricalToInt(src);
      case Vec.T_UUID:
      case Vec.T_TIME:
      case Vec.T_NUM:
        return src.makeCopy(null, Vec.T_NUM);
      default:
        throw new H2OIllegalArgumentException("Unrecognized column type " + src.get_type_str()
            + " given to toNumericVec()");
    }
  }

  /**
   * Transform a string Vec where all values are string versions of a number to
   * a numeric Vec. Any rows that cannot be converted to a number are set to NA.
   * Currently only does basic numeric formats. No exponents, or hex values. Doesn't
   * even like commas or spaces.  :( Needs love.
   *
   * @return A new Vec
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


  /** Transform an categorical Vec to a Int Vec. If the domain of the Vec is stringified ints, then
   * it will use those ints. Otherwise, it will use the raw domain mapping.
   * If the domain is stringified ints, then all of the domain must be able to be parsed as
   * an int. If it cannot be parsed as such, a NumberFormatException will be caught and
   * rethrown as an IllegalArgumentException that declares the illegal domain value.
   * Otherwise, the this pointer is copied to a new Vec whose domain is null.
   * @return A new Vec
   */
  public static Vec categoricalToInt(final Vec src) {
    if( src.isInt() && src.domain()==null ) return copyOver(src, Vec.T_NUM, null);
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
   * Convert to a string value.
   *
   * For string columns, a copy of the column is made.
   *
   * For categorical columns, levels are dropped, and the column only records the string.
   *
   * For all numeric columns, the number is converted to a string.
   *
   * For all UUID columns, the hex representation is stored as a string.
   *
   * @return
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

  /** Transform this vector to strings.  If the
   *  vector is categorical an identity transformation vector is returned.
   *  Transformation is done by a {@link Categorical2StrChkTask} which provides a mapping
   *  between values - without copying the underlying data.
   *  @return A new String Vec  */
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

  public static Vec UUIDToStringVec(Vec src) {
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
   * Transform a categorical Vec where all domain values are string versions of a number to
   * a numeric Vec. Any rows that cannot be converted to a number are set to NA.
   *
   * @return A new Vec
   */
  public static Vec categoricalDomainsToNumeric(final Vec src) {
    if( !src.isCategorical() ) throw new H2OIllegalArgumentException("categoricalToNumeric() conversion only works on categorical vecs");
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

  /** Collect numeric domain of given vector
   *  A map-reduce task to collect up the unique values of an integer vector
   *  and returned as the domain for the vector.
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

    @Override public AutoBuffer write_impl( AutoBuffer ab ) {
      return ab.putA8(_uniques==null ? null : _uniques.keySetLong());
    }

    @Override public CollectDomain read_impl( AutoBuffer ab ) {
      assert _uniques == null || _uniques.size()==0;
      long ls[] = ab.getA8();
      _uniques = new NonBlockingHashMapLong<>();
      if( ls != null ) for( long l : ls ) _uniques.put(l, "");
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
    public long[] domain() {
      long[] dom = _uniques.keySetLong();
      Arrays.sort(dom);
      return dom;
    }
  }

  // >11x faster than CollectDomain
  /** (Optimized for positive ints) Collect numeric domain of given vector
   *  A map-reduce task to collect up the unique values of an integer vector
   *  and returned as the domain for the vector.
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
      Arrays.sort(_d);
    }

    /** Returns exact numeric domain of given vector computed by this task.
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
}
