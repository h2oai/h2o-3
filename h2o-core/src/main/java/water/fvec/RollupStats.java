package water.fvec;

import water.Futures;

import jsr166y.CountedCompleter;
import jsr166y.ForkJoinTask;
import water.*;
import water.H2O.H2OCallback;
import water.H2O.H2OCountedCompleter;
import water.nbhm.NonBlockingHashMap;
import water.parser.Categorical;
import water.parser.BufferedString;
import water.util.ArrayUtils;

import java.util.Arrays;

/** A class to compute the rollup stats.  These are computed lazily, thrown
 *  away if the Vec is written into, and then recomputed lazily.  Error to ask
 *  for them if the Vec is actively being written into.  It is common for all
 *  cores to ask for the same Vec rollup at once, so it is crucial that it be
 *  computed once across the cluster.
 *
 *  Rollups are kept in the K/V store, which also controls who manages the
 *  rollup work and final results.  Winner of a DKV CAS/PutIfMatch race gets to
 *  manage the M/R job computing the rollups.  Losers block for the same
 *  rollup.  Remote requests *always* forward to the Rollup Key's master.
 */
public final class RollupStats extends Iced {
  // Computed in 1st pass
  volatile long _naCnt; //count(!isNA(X))
  double _mean, _sigma; //sum(X) and sum(X^2) for non-NA values
  long    _rows,        //count(X) for non-NA values
          _nzCnt,       //count(X!=0) for non-NA values
          _size,        //byte size
          _pinfs,       //count(+inf)
          _ninfs;       //count(-inf)
  boolean _isInt=true;
  double[] _mins, _maxs;
  long _checksum;

  public long rowCnt() {return _rows;}
  public long nzCnt() {return _nzCnt;}

  public long posInfCnt() {return _pinfs;}
  public long negInfCnt() {return _ninfs;}

  public double[] mins() {return _mins;}
  public double[] maxs() {return _maxs;}

  public String typeStr(){
    switch(_type) {
      case Vec.T_CAT:
        return "enum";
      case Vec.T_NUM:
        if(_isInt)
          return "int";
        return "Double";
      case Vec.T_UUID:
        return "uuid";
      case Vec.T_TIME:
        return "time";
      default:
        throw H2O.unimpl();
    }
  }

  public boolean isBinary() {
    return _isInt && _mins[0] >= 0 && _maxs[0] <= 1;
  }



  public byte _type;


  public boolean hasHisto(){return _bins != null;}

  public long [] lazy_bins(){return _bins;}



  // Check for: Vector is mutating and rollups cannot be asked for
  boolean isMutating() { return _naCnt==-2; }
  // Check for: Rollups currently being computed
  private boolean isComputing() { return _naCnt==-1; }

  boolean isRemoved(){return _naCnt == -3;}
  public void setRemoved() { _naCnt = -3;}
  // Check for: Rollups available
  boolean isReady() { return _naCnt>=0; }

  RollupStats(int mode) {
    _mins = new double[5];
    _maxs = new double[5];
    Arrays.fill(_mins, Double.MAX_VALUE);
    Arrays.fill(_maxs,-Double.MAX_VALUE);
    _mean = _sigma = 0;
    _size = 0;
    _naCnt = mode;
  }

  private static RollupStats makeComputing() { return new RollupStats(-1); }
  static RollupStats makeMutating () { return new RollupStats(-2); }

  public static RollupStats computeRollups( long start, Chunk c, boolean isUUID, boolean isString) {
    RollupStats rs = new RollupStats(0);
    int len = c.len();
    rs._size = c._mem.length;
    BufferedString tmpStr = new BufferedString();
    if (isString) rs._isInt = false;
    // Checksum support
    long checksum = 0;
    long l = 81985529216486895L;

    // Check for popular easy cases: All Constant
    double min=c.min(), max=c.max();
    if( min==max  ) {              // All constant or all NaN
      double d = min;             // It's the min, it's the max, it's the alpha and omega
      rs._checksum = (c.hasFloat()?Double.doubleToRawLongBits(d):(long)d)*len;
      Arrays.fill(rs._mins, d);
      Arrays.fill(rs._maxs, d);
      if( d == Double.POSITIVE_INFINITY) rs._pinfs++;
      else if( d == Double.NEGATIVE_INFINITY) rs._ninfs++;
      else {
        if( Double.isNaN(d)) rs._naCnt=len;
        else if( d != 0 ) rs._nzCnt=len;
        rs._mean = d;
        rs._rows=len;
      }
      rs._isInt = ((long)d) == d;
      rs._sigma = 0;               // No variance for constants
      return rs;
    }

    //all const NaNs
    if ((c instanceof C0DChunk && c.isNA_impl(0))) {
      rs._sigma=0; //count of non-NAs * variance of non-NAs
      rs._mean = 0; //sum of non-NAs (will get turned into mean)
      rs._naCnt=len;
      rs._nzCnt=0;
      return rs;
    }

    // Check for popular easy cases: Boolean, possibly sparse, possibly NaN
    if( min==0 && max==1 ) {
      int zs = len-c.sparseLenZero(); // Easy zeros
      int nans = 0;
      // Hard-count sparse-but-zero (weird case of setting a zero over a non-zero)
      for( int i=c.nextNZ(-1); i< len; i=c.nextNZ(i) )
        if( c.isNA_impl(i) ) nans++;
        else if( c.at8_impl(i)==0 ) zs++;
      int os = len-zs-nans;  // Ones
      rs._nzCnt += os;
      rs._naCnt += nans;
      for( int i=0; i<Math.min(rs._mins.length,zs); i++ ) { min(rs._mins,0); max(rs._maxs,0); }
      for( int i=0; i<Math.min(rs._mins.length,os); i++ ) { min(rs._mins,1); max(rs._maxs,1); }
      rs._rows += zs+os;
      rs._mean = (double)os/rs._rows;
      rs._sigma = zs*(0.0-rs._mean)*(0.0-rs._mean) + os*(1.0-rs._mean)*(1.0-rs._mean);
      return rs;
    }


    // Walk the non-zeros
    if( isUUID ) {   // UUID columns do not compute min/max/mean/sigma
      for( int i=c.nextNZ(-1); i< len; i=c.nextNZ(i) ) {
        if( c.isNA_impl(i) ) rs._naCnt++;
        else {
          long lo = c.at16l_impl(i), hi = c.at16h_impl(i);
          if (lo != 0 || hi != 0)rs._nzCnt++;
          l = lo ^ 37*hi;
        }
        if(l != 0) // ignore 0s in checksum to be consistent with sparse chunks
          checksum ^= (17 * (start+i)) ^ 23*l;
      }

    } else if( isString ) { // String columns do not compute min/max/mean/sigma
      for (int i = c.nextNZ(-1); i < len; i = c.nextNZ(i)) {
        if (c.isNA_impl(i)) rs._naCnt++;
        else {
          rs._nzCnt++;
          l = c.atStr_impl(tmpStr, i).hashCode();
        }
        if (l != 0) // ignore 0s in checksum to be consistent with sparse chunks
          checksum ^= (17 * (start + i)) ^ 23 * l;
      }
    } else {
      // Work off all numeric rows, or only the nonzeros for sparse
      if (c instanceof C1Chunk)
        checksum=new RollupStatsHelpers(rs).numericChunkRollup((C1Chunk) c, start, checksum);
      else if (c instanceof C1SChunk)
        checksum=new RollupStatsHelpers(rs).numericChunkRollup((C1SChunk) c, start, checksum);
      else if (c instanceof C1NChunk)
        checksum=new RollupStatsHelpers(rs).numericChunkRollup((C1NChunk) c, start, checksum);
      else if (c instanceof C2Chunk)
        checksum=new RollupStatsHelpers(rs).numericChunkRollup((C2Chunk) c, start, checksum);
      else if (c instanceof C2SChunk)
        checksum=new RollupStatsHelpers(rs).numericChunkRollup((C2SChunk) c, start, checksum);
      else if (c instanceof C4SChunk)
        checksum=new RollupStatsHelpers(rs).numericChunkRollup((C4SChunk) c, start, checksum);
      else if (c instanceof C4FChunk)
        checksum=new RollupStatsHelpers(rs).numericChunkRollup((C4FChunk) c, start, checksum);
      else if (c instanceof C4Chunk)
        checksum=new RollupStatsHelpers(rs).numericChunkRollup((C4Chunk) c, start, checksum);
      else if (c instanceof C8Chunk)
        checksum=new RollupStatsHelpers(rs).numericChunkRollup((C8Chunk) c, start, checksum);
      else if (c instanceof C8DChunk)
        checksum=new RollupStatsHelpers(rs).numericChunkRollup((C8DChunk) c, start, checksum);
      else
        checksum=new RollupStatsHelpers(rs).numericChunkRollup(c, start, checksum);

      // special case for sparse chunks
      // we need to merge with the mean (0) and variance (0) of the zeros count of 0s of the sparse chunk - which were skipped above
      // _rows is the count of non-zero rows
      // _mean is the mean of non-zero rows
      // _sigma is the mean of non-zero rows
      // handle the zeros
      if( c.isSparseZero() ) {
        int zeros = len - c.sparseLenZero();
        if (zeros > 0) {
          for( int i=0; i<Math.min(rs._mins.length,zeros); i++ ) { min(rs._mins,0); max(rs._maxs,0); }
          double zeromean = 0;
          double zeroM2 = 0;
          double delta = rs._mean - zeromean;
          rs._mean = (rs._mean * rs._rows + zeromean * zeros) / (rs._rows + zeros);
          rs._sigma += zeroM2 + delta*delta * rs._rows * zeros / (rs._rows + zeros); //this is the variance*(N-1), will do sqrt(_sigma/(N-1)) later in postGlobal
          rs._rows += zeros;
        }
      }
    }
    rs._checksum = checksum;

    // UUID and String columns do not compute min/max/mean/sigma
    if( isUUID || isString) {
      Arrays.fill(rs._mins,Double.NaN);
      Arrays.fill(rs._maxs,Double.NaN);
      rs._mean = rs._sigma = Double.NaN;
    }
    return rs;
  }


  void reduce(RollupStats rs) {
    for( double d : rs._mins ) if (!Double.isNaN(d)) min(rs._mins,d);
    for( double d : rs._maxs ) if (!Double.isNaN(d)) max(rs._maxs,d);
    _naCnt += rs._naCnt;
    _nzCnt += rs._nzCnt;
    _pinfs += rs._pinfs;
    _ninfs += rs._ninfs;
    if (_rows == 0) { _mean = rs._mean;  _sigma = rs._sigma; }
    else if(rs._rows != 0){
      double delta = _mean - rs._mean;
      _mean = (_mean * _rows + rs._mean * rs._rows) / (_rows + rs._rows);
      _sigma += rs._sigma + delta*delta * _rows*rs._rows / (_rows+rs._rows);
    }
    _rows += rs._rows;
    _size += rs._size;
    _isInt &= rs._isInt;
    _checksum ^= rs._checksum;
  }

  static double min(double [] mins, double d) {
    assert(!Double.isNaN(d));
    for( int i=0; i<mins.length; i++ )
      if( d < mins[i] )
        { double tmp = mins[i];  mins[i] = d;  d = tmp; }
    return mins[mins.length-1];
  }

  static double max(double [] maxs, double d ) {
    assert(!Double.isNaN(d));
    for( int i=0; i<maxs.length; i++ )
      if( d > maxs[i] )
        { double tmp = maxs[i];  maxs[i] = d;  d = tmp; }
    return maxs[maxs.length-1];
  }

  void postGlobal() {
    _sigma = Math.sqrt(_sigma/(_rows-1));
    if (_rows == 1) _sigma = 0;
    if (_rows < 5) for (int i=0; i<5-_rows; i++) {  // Fix PUBDEV-150 for files under 5 rows
      _maxs[4-i] = Double.NaN;
      _mins[4-i] = Double.NaN;
    }
    // mean & sigma not allowed on more than 2 classes; for 2 classes the assumption is that it's true/false
    if(_type == Vec.T_CAT && _rows > 2 && _maxs[2] != _mins[2])
      _mean = _sigma = Double.NaN;
  }

  double [] _pctiles;
  long [] _bins;

  public void computePercentiles() {
    _pctiles = new double[Vec.PERCENTILES.length];
    int j = 0;                 // Histogram bin number
    int k = 0;                 // The next non-zero bin after j
    long hsum = 0;             // Rolling histogram sum
    double base = h_base();
    double stride = h_stride();
    double lastP = -1.0;       // any negative value to pass assert below first time
    for (int i = 0; i < Vec.PERCENTILES.length; i++) {
      final double P = Vec.PERCENTILES[i];
      assert P >= 0 && P <= 1 && P >= lastP;   // rely on increasing percentiles here. If P has dup then strange but accept, hence >= not >
      lastP = P;
      double pdouble = 1.0 + P * (_rows - 1);   // following stats:::quantile.default type 7
      long pint = (long) pdouble;          // 1-based into bin vector
      double h = pdouble - pint;           // any fraction h to linearly interpolate between?
      assert P != 1 || (h == 0.0 && pint == _rows);  // i.e. max
      while (hsum < pint) hsum += _bins[j++];
      // j overshot by 1 bin; we added _bins[j-1] and this goes from too low to either exactly right or too big
      // pint now falls in bin j-1 (the ++ happened even when hsum==pint), so grab that bin value now
      _pctiles[i] = base + stride * (j - 1);
      if (h > 0 && pint == hsum) {
        // linearly interpolate between adjacent non-zero bins
        //      i) pint is the last of (j-1)'s bin count (>1 when either duplicates exist in input, or stride makes dups at lower accuracy)
        // AND ii) h>0 so we do need to find the next non-zero bin
        if (k < j) k = j; // if j jumped over the k needed for the last P, catch k up to j
        // Saves potentially winding k forward over the same zero stretch many times
        while (_bins[k] == 0) k++;  // find the next non-zero bin
        _pctiles[i] += h * stride * (k - j + 1);
      } // otherwise either h==0 and we know which bin, or fraction is between two positions that fall in the same bin
      // this guarantees we are within one bin of the exact answer; i.e. within (max-min)/MAX_SIZE
    }
  }
  public double min() {return _mins[0];}
  public double mean() {return _mean;}
  public double sigma() {return _sigma;}
  public long naCnt() {return _naCnt;}
  public double max() {return _maxs[0];}
  private static NonBlockingHashMap<Key,RPC> _pendingRollups = new NonBlockingHashMap<>();

  // Fetch if present, but do not compute
  static RollupStats getOrNull(Vec vec, final Key rskey ) {
    Value val = DKV.get(rskey);
    if( val == null )           // No rollup stats present?
      return vec.length() > 0 ? /*not computed*/null : /*empty vec*/new RollupStats(0);
    RollupStats rs = val.get(RollupStats.class);
    return rs.isReady() ? rs : null;
  }
  // Histogram base & stride
  public double h_base() { return _mins[0]; }
  public double h_stride() { return h_stride(_bins.length); }
  double h_stride(int nbins) { return (_maxs[0]-_mins[0]+(_isInt?1:0))/nbins; }

  public double[] pctiles() {
    return _pctiles;
  }

}
