package water.fvec;

import water.*;
import water.util.ArrayUtils;
import water.util.IcedHashMap;
import water.util.IcedLong;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;

/**
 *
 * This class represents an interaction between two Vec instances.
 *
 * Another virtual Vec (akin to TransformWrappedVec) used to represent a
 * never-materialized interaction between two columns.
 *
 * There are 3 types of interactions to consider: Num-Num, Num-Enum, and Enum-Enums
 * Creation of these Vec instances is cheap except for the Enum-Enum case (since it is not
 * known apriori the co-occurrence of enums between any two categorical columns). So in
 * this specific case, an MRTask is done to collect the domain.
 *
 *
 * @author spencer
 */
public class InteractionWrappedVec extends WrappedVec {
  private final Key<Vec> _masterVecKey1;
  private final Key<Vec> _masterVecKey2;
  private transient Vec _masterVec1;  private String[] _v1Domain;
  private transient Vec _masterVec2;  private String[] _v2Domain;
  public final boolean _useAllFactorLevels;
  private boolean _skipMissing;
  private boolean _standardize;
  private long _bins[];

  private final String _v1Enums[]; // only interact these enums from vec 1
  private final String _v2Enums[]; // only interact these enums from vec 2

  public InteractionWrappedVec(Key key, int rowLayout, String[] vec1DomainLimit, String[] vec2DomainLimit, boolean useAllFactorLevels, boolean skipMissing, boolean standardize, Key<Vec> masterVecKey1, Key<Vec> masterVecKey2) {
    super(key, rowLayout, null);
    _masterVecKey1=masterVecKey1;
    _masterVecKey2=masterVecKey2;
    _v1Enums=vec1DomainLimit;
    _v2Enums=vec2DomainLimit;
    _masterVec1=_masterVecKey1.get();
    _masterVec2=_masterVecKey2.get();
    _useAllFactorLevels=useAllFactorLevels;
    _skipMissing=skipMissing;
    _standardize=standardize;
    setupDomain();  // performs MRTask if both vecs are categorical!!
    DKV.put(this);
  }

  public String[] v1Domain() { return _v1Enums==null?_v1Domain:_v1Enums; }
  public String[] v2Domain() { return _v2Enums==null?_v2Domain:_v2Enums; }
  public String[] domains() { // always returns the "correct" domains, so accidental mixup of domain vs domains is ok
    String[] res;
    if( null!=(res=domain())) return res;
    if( null==(res=v1Domain()) ) return v2Domain();
    return res;
  }

  public Vec v1() { return _masterVec1==null?(_masterVec1=_masterVecKey1.get()):_masterVec1; }
  public Vec v2() { return _masterVec2==null?(_masterVec2=_masterVecKey2.get()):_masterVec2; }

  /**
   * Obtain the length of the expanded (i.e. one-hot expanded) interaction column.
   */
  public int expandedLength() {
    if( _v1Domain==null && _v2Domain==null ) return 1; // 2 numeric columns -> 1 column
    else if( isCategorical() ) return domain().length; // 2 cat -> domains (limited) length
    else if( _v1Domain!=null ) return _v1Enums==null?_v1Domain.length - (_useAllFactorLevels?0:1):_v1Enums.length-(_useAllFactorLevels?0:1);
    else return _v2Enums==null?_v2Domain.length - (_useAllFactorLevels?0:1):_v2Enums.length - (_useAllFactorLevels?0:1);
  }

  @Override public double mean() { return 0; } // do no manip on interaction vecs (protects from auto normalization etc.)
  @Override public double sigma() {return 1; } // no scaling
  @Override public int mode() {
    if( !isCategorical() ) throw H2O.unimpl();
    return ArrayUtils.maxIndex(_bins);
  }
  public long[] getBins() { return _bins; }
  private void setupDomain() {
    if( _masterVec1.isCategorical() || _masterVec2.isCategorical() ) {
      _v1Domain = _masterVec1.domain();
      _v2Domain = _masterVec2.domain();
      if( _v1Domain!=null && _v2Domain!=null ) {
        CombineDomainTask t =new CombineDomainTask(_v1Domain, _v2Domain,_v1Enums,_v2Enums, _useAllFactorLevels,_skipMissing).doAll(_masterVec1, _masterVec2);
        setDomain(t._dom);
        _bins=t._bins;
        _type = Vec.T_CAT; // vec is T_NUM up to this point
      }
    }
  }

  private static class CombineDomainTask extends MRTask<CombineDomainTask> {
    private String[] _dom;        // out, sorted (uses Arrays.sort)
    private long[] _bins;         // out, sorted according to _dom
    private final String _left[]; // in
    private final String _rite[]; // in
    private final String _leftLimit[]; // in
    private final String _riteLimit[]; // in
    private final boolean _useAllLvls; // in
    private final boolean _skipMissing; // in
    private IcedHashMap<String, IcedLong> _perChkMap;

    CombineDomainTask(String[] left, String[] rite, String[] leftLimit, String[] riteLimit, boolean useAllLvls, boolean skipMissing) {
      _left = left;
      _rite = rite;
      _leftLimit = leftLimit;
      _riteLimit = riteLimit;
      _useAllLvls = useAllLvls;
      _skipMissing = skipMissing;
    }

    @Override public void map(Chunk[] c) {
      _perChkMap = new IcedHashMap<>();
      Chunk left = c[0];
      Chunk rite = c[1];
      String k;
      HashSet<String> A = _leftLimit == null ? null : new HashSet<String>();
      HashSet<String> B = _riteLimit == null ? null : new HashSet<String>();
      if (A != null) Collections.addAll(A, _leftLimit);
      if (B != null) Collections.addAll(B, _riteLimit);
      int lval,rval;
      String l,r;
      boolean leftIsNA, riteIsNA;
      for (int i = 0; i < left._len; ++i)
        if( (!((leftIsNA=left.isNA(i)) | (riteIsNA=rite.isNA(i)))) ) {
          lval = (int)left.at8(i);
          rval = (int)rite.at8(i);
          if( !_useAllLvls &&  ( 0==lval || 0==rval )) continue; // skipping first level! => but use all domains!
          l = _left[lval];
          r = _rite[rval];
          if (A != null && !A.contains(l)) continue;
          if (B != null && !B.contains(r)) continue;
          if( null!=_perChkMap.putIfAbsent((k = l + "_" + r), new IcedLong(1)) )
            _perChkMap.get(k)._val++;
        } else if( !_skipMissing ) {
          if( !(leftIsNA && riteIsNA) ) {  // not both missing
            if( leftIsNA ) {
              r = _rite[(int)rite.at8(i)];
              if( B!=null && !B.contains(r) ) continue;
              if( null!=_perChkMap.putIfAbsent((k="NA_"+r), new IcedLong(1)) )
                _perChkMap.get(k)._val++;
            } else {
              l = _left[(int)left.at8(i)];
              if( null!=A && !A.contains(l) ) continue;
              if( null!=_perChkMap.putIfAbsent((k=l+"_NA"), new IcedLong(1)) )
                _perChkMap.get(k)._val++;
            }
          }
        }
    }

    @Override public void reduce(CombineDomainTask t) {
      for (Map.Entry<String, IcedLong> e : t._perChkMap.entrySet()) {
        IcedLong i = _perChkMap.get(e.getKey());
        if (i != null) i._val += e.getValue()._val;
        else _perChkMap.put(e.getKey(), e.getValue());
      }
      t._perChkMap = null;
    }

    @Override public void postGlobal() {
      Arrays.sort(_dom = _perChkMap.keySet().toArray(new String[_perChkMap.size()]));
      int idx = 0;
      _bins = new long[_perChkMap.size()];
      for(String s:_dom)
        _bins[idx++] = _perChkMap.get(s)._val;
    }
  }

  @Override public Chunk chunkForChunkIdx(int cidx) {
    Chunk[] cs = new Chunk[2];
    cs[0] = (_masterVec1!=null?_masterVec1: (_masterVec1=_masterVecKey1.get())).chunkForChunkIdx(cidx);
    cs[1] = (_masterVec2!=null?_masterVec2: (_masterVec2=_masterVecKey2.get())).chunkForChunkIdx(cidx);
    return new InteractionWrappedChunk(this, cs,_standardize);
  }

  @Override public Vec doCopy() {
    InteractionWrappedVec v = new InteractionWrappedVec(group().addVec(), _rowLayout,_v1Enums,_v2Enums, _useAllFactorLevels, _skipMissing, _standardize, _masterVecKey1, _masterVecKey2);
    if( null!=domain()  ) v.setDomain(domain());
    if( null!=_v1Domain ) v._v1Domain=_v1Domain.clone();
    if( null!=_v2Domain ) v._v2Domain=_v2Domain.clone();
    return v;
  }

  public static class InteractionWrappedChunk extends Chunk {
    public final transient Chunk _c[];
    private final boolean _c1IsCat; // left chunk is categorical
    private final boolean _c2IsCat; // rite chunk is categorical
    private final boolean _isCat;   // this vec is categorical
    // these values are used for on-the-fly standardization
    private final double _v1Sub;    // the mean of vec v1
    private final double _v2Sub;    // the mean of vec v2
    private double _v1Mul;    // the multiplier for vec v1
    private double _v2Mul;    // the multiplier for vec v2
    InteractionWrappedChunk(InteractionWrappedVec transformWrappedVec, Chunk[] c, boolean standardize) {
      // set all the chunk fields
      _c = c; set_len(_c[0]._len);
      _start = _c[0]._start; _vec = transformWrappedVec; _cidx = _c[0]._cidx;
      _c1IsCat=_c[0]._vec.isCategorical();
      _c2IsCat=_c[1]._vec.isCategorical();
      _isCat = _vec.isCategorical();
      _v1Sub=standardize?transformWrappedVec.v1().mean():0;
      _v2Sub=standardize?transformWrappedVec.v2().mean():0;
      _v1Mul=standardize?transformWrappedVec.v1().sigma():1;
      _v2Mul=standardize?transformWrappedVec.v2().sigma():1;
      if( _v1Mul==0 ) _v1Mul=1;
      if( _v2Mul==0 ) _v2Mul=1;
      _v1Mul=1./_v1Mul;
      _v2Mul=1./_v2Mul;
    }

    @Override public double atd_impl(int idx) {return _isCat ? Arrays.binarySearch(_vec.domain(), getKey(idx)) : ( _c1IsCat?1: _v1Mul*(_c[0].atd(idx)-_v1Sub )) * ( _c2IsCat?1: _v2Mul*(_c[1].atd(idx)-_v2Sub) );}

//    @Override public double atd_impl(int idx) { return _isCat ? Arrays.binarySearch(_vec.domain(), getKey(idx)) : ( _c1IsCat?1:_c[0].atd(idx) ) * ( _c2IsCat?1:_c[1].atd(idx) ); }
    @Override public long at8_impl(int idx)   { return _isCat ? Arrays.binarySearch(_vec.domain(), getKey(idx)) : ( _c1IsCat?1:_c[0].at8(idx) ) * ( _c2IsCat?1:_c[1].at8(idx) ); }
    private String getKey(int idx) { return _c[0]._vec.domain()[(int)_c[0].at8(idx)] + _c[1]._vec.domain()[(int)_c[1].at8(idx)]; }
    @Override public boolean isNA_impl(int idx) { return _c[0].isNA(idx) || _c[1].isNA(idx); }
    // Returns true if the masterVec is missing, false otherwise
    @Override public boolean set_impl(int idx, long l)   { return false; }
    @Override public boolean set_impl(int idx, double d) { return false; }
    @Override public boolean set_impl(int idx, float f)  { return false; }
    @Override public boolean setNA_impl(int idx)         { return false; }
    @Override public NewChunk inflate_impl(NewChunk nc) {
      nc.set_sparseLen(nc.set_len(0));
      if( _vec.isCategorical() )
        for(int i=0;i<_len;++i)
          if( isNA(i) ) nc.addNA();
          else          nc.addNum(at8(i),0);
      else
        for( int i=0; i< _len; i++ )
          if( isNA(i) ) nc.addNA();
          else          nc.addNum(atd(i));
      return nc;
    }
    @Override protected final void initFromBytes () { throw water.H2O.fail(); }
  }
}