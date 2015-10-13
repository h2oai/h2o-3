package water.util;

import java.util.Arrays;

import water.AutoBuffer;
import water.Futures;
import water.MRTask;
import water.MemoryManager;
import water.exceptions.H2OIllegalValueException;
import water.fvec.C0DChunk;
import water.fvec.CategoricalWrappedVec;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.exceptions.H2OIllegalArgumentException;
import water.nbhm.NonBlockingHashMapLong;
import water.parser.BufferedString;
import water.parser.Categorical;

public class VecUtils {
  /** Transform this vector to categorical.  If the vector is integer vector then its
   *  domain is collected and transformed to corresponding strings.  If the
   *  vector is categorical an identity transformation vector is returned.
   *  Transformation is done by a {@link CategoricalWrappedVec} which provides a mapping
   *  between values - without copying the underlying data.
   *  @return A new categorical Vec  */
//  public CategoricalWrappedVec toCategoricalVec() {
//    if( isCategorical() ) return adaptTo(domain()); // Use existing domain directly
//    if( !isInt() ) throw new IllegalArgumentException("Categorical conversion only works on integer columns");
//    int min = (int) min(), max = (int) max();
//    // try to do the fast domain collection
//    long domain[] = (min >=0 && max < Integer.MAX_VALUE-4) ? new CollectDomainFast(max).doAll(this).domain() : new CollectDomain().doAll(this).domain();
//    if( domain.length > Categorical.MAX_CATEGORICAL_SIZE )
//      throw new IllegalArgumentException("Column domain is too large to be represented as an categorical: " + domain.length + " > " + Categorical.MAX_CATEGORICAL_SIZE);
//    return adaptTo(ArrayUtils.toString(domain));
//  }

  /** Create a new Vec (as opposed to wrapping it) that is the categorical'ified version of the original.
   *  The original Vec is not mutated.  */
  public static Vec toCategoricalVec(Vec src) {
    if( src.isCategorical() ) return src.makeCopy(src.domain());
    if( !src.isInt() ) throw new IllegalArgumentException("Categorical conversion only works on integer columns");
    int min = (int) src.min(), max = (int) src.max();
    // try to do the fast domain collection
    long dom[] = (min >= 0 && max < Integer.MAX_VALUE - 4) ? new CollectDomainFast(max).doAll(src).domain() : new CollectDomain().doAll(src).domain();
    if (dom.length > Categorical.MAX_CATEGORICAL_COUNT)
      throw new IllegalArgumentException("Column domain is too large to be represented as an categorical: " + dom.length + " > " + Categorical.MAX_CATEGORICAL_COUNT);
    return copyOver(src, dom);
  }

  /** Transform an categorical Vec to a Int Vec. If the domain of the Vec is stringified ints, then
   * it will use those ints. Otherwise, it will use the raw domain mapping.
   * If the domain is stringified ints, then all of the domain must be able to be parsed as
   * an int. If it cannot be parsed as such, a NumberFormatException will be caught and
   * rethrown as an IllegalArgumentException that declares the illegal domain value.
   * Otherwise, the this pointer is copied to a new Vec whose domain is null.
   * @return A new Vec
   */
  public static Vec toIntVec(final Vec src) {
    if( src.isInt() && src.domain()==null ) return copyOver(src, null);
    if( !src.isCategorical() ) throw new IllegalArgumentException("toIntVec conversion only works on categorical and Int vecs");
    // check if the 1st lvl of the domain can be parsed as int
    boolean useDomain=false;
    Vec newVec = copyOver(src, null);
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

  /** Create a new Vec (as opposed to wrapping it) that is the Numeric'd version of the original.
   *  The original Vec is not mutated.  */
  public static Vec toNumericVec(Vec src) {
    if( src.isString() ) {
      return new MRTask() {
        @Override public void map(Chunk c, NewChunk nc) {
          BufferedString bStr = new BufferedString();
          for( int row=0;row<c._len;++row) {
            if( c.isNA(row) ) nc.addNA();
            else if( c.atStr(bStr,row).toString().equals("") ) nc.addNA();
            else              nc.addNum(Double.valueOf(c.atStr(bStr,row).toString()));
          }
        }
      }.doAll(1, Vec.T_NUM, new Frame(src)).outputFrame().anyVec();
    }
    return src.makeCopy(null, Vec.T_NUM);
  }

  /** Transform this vector to strings.  If the
   *  vector is categorical an identity transformation vector is returned.
   *  Transformation is done by a {@link Categorical2StrChkTask} which provides a mapping
   *  between values - without copying the underlying data.
   *  @return A new String Vec  */
  public static Vec toStringVec(Vec src) {
    if( !src.isCategorical() ) throw new IllegalArgumentException("String conversion only works on categorical columns");
    return new Categorical2StrChkTask(src.domain()).doAll(new byte[]{Vec.T_STR},src).outputFrame().anyVec();
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

  private static Vec copyOver(Vec src, long[] domain) {
    String[][] dom = new String[1][];
    dom[0]=domain==null?null:ArrayUtils.toString(domain);
    return new CPTask(domain).doAll(src.get_type(),src).outputFrame(null,dom).anyVec();
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
