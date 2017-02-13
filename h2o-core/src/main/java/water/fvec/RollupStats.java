package water.fvec;

import water.Futures;

import jsr166y.CountedCompleter;
import water.*;
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
final class RollupStats extends Iced {
  /** The count of missing elements.... or -2 if we have active writers and no
   *  rollup info can be computed (because the vector is being rapidly
   *  modified!), or -1 if rollups have not been computed since the last
   *  modification.   */

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

  // Expensive histogram & percentiles
  // Computed in a 2nd pass, on-demand, by calling computeHisto
  private static final int MAX_SIZE = 1000; // Standard bin count; categoricals can have more bins
  // the choice of MAX_SIZE being a power of 10 (rather than 1024) just aligns-to-the-grid of the common input of fixed decimal
  // precision numbers. It is still an estimate and makes no difference mathematically. It just gives tidier output in some
  // simple cases without penalty.
  volatile long[] _bins;
  // Approximate data value closest to the Xth percentile
  double[] _pctiles;

  public boolean hasHisto(){return _bins != null;}

  // Check for: Vector is mutating and rollups cannot be asked for
  boolean isMutating() { return _naCnt==-2; }
  // Check for: Rollups currently being computed
  protected boolean isComputing() { return _naCnt==-1; }
  // Check for: Rollups available
  protected boolean isReady() { return !isComputing(); }

  RollupStats(int mode) {
    _mins = new double[5];
    _maxs = new double[5];
    Arrays.fill(_mins, Double.MAX_VALUE);
    Arrays.fill(_maxs,-Double.MAX_VALUE);
    _pctiles = new double[Vec.PERCENTILES.length];  Arrays.fill(_pctiles, Double.NaN);
    _mean = _sigma = 0;
    _size = 0;
    _naCnt = mode;
  }

  private static RollupStats makeComputing() { return new RollupStats(-1); }
  static RollupStats makeMutating () { return new RollupStats(-2); }


  private RollupStats map( ChunkAry ca, int col ) {
    _size = ca.byteSize();
    boolean isUUID = ca._vec.isUUID(col);
    boolean isString = ca._vec.isString(col);
    BufferedString tmpStr = new BufferedString();
    if (isString) _isInt = false;
    // Checksum support
    long checksum = 0;
    long start = ca._start;
    long l = 81985529216486895L;

    // Check for popular easy cases: All Constant
    double min=ca.min(col), max=ca.max(col);
    if( min==max  ) {              // All constant or all NaN
      double d = min;             // It's the min, it's the max, it's the alpha and omega
      _checksum = (ca.hasFloat()?Double.doubleToRawLongBits(d):(long)d)*ca._len;
      Arrays.fill(_mins, d);
      Arrays.fill(_maxs, d);
      if( d == Double.POSITIVE_INFINITY) _pinfs++;
      else if( d == Double.NEGATIVE_INFINITY) _ninfs++;
      else {
        if( Double.isNaN(d)) _naCnt=ca._len;
        else if( d != 0 ) _nzCnt=ca._len;
        _mean = d;
        _rows=ca._len;
      }
      _isInt = ((long)d) == d;
      _sigma = 0;               // No variance for constants
      return this;
    }

    //all const NaNs
    if (ca.isSparseNA(col) && ca.sparseLenNA(col) == 0) {
      _sigma=0; //count of non-NAs * variance of non-NAs
      _mean = 0; //sum of non-NAs (will get turned into mean)
      _naCnt=ca._len;
      _nzCnt=0;
      return this;
    }

    // Check for popular easy cases: Boolean, possibly sparse, possibly NaN
    if( min==0 && max==1 ) {
      int zs = ca._len -ca.sparseLenZero(col); // Easy zeros
      int nans = 0;
      // Hard-count sparse-but-zero (weird case of setting a zero over a non-zero)
      for(Chunk.SparseNum sv = ca.sparseNum(col); sv.rowId() < ca._len; sv.nextNZ())
        if( Double.isNaN(sv.dval()) ) nans++;
        else if( sv.dval()==0 ) zs++;
      int os = ca._len -zs-nans;  // Ones
      _nzCnt += os;
      _naCnt += nans;
      for( int i=0; i<Math.min(_mins.length,zs); i++ ) { min(0); max(0); }
      for( int i=0; i<Math.min(_mins.length,os); i++ ) { min(1); max(1); }
      _rows += zs+os;
      _mean = (double)os/_rows;
      _sigma = zs*(0.0-_mean)*(0.0-_mean) + os*(1.0-_mean)*(1.0-_mean);
      return this;
    }


    // Walk the non-zeros
    if( isUUID ) {   // UUID columns do not compute min/max/mean/sigma
      for (int i = 0; i < ca._len; ++i) {
        if (ca.isNA(i, col)) _naCnt++;
        else {
          long lo = ca.at16l(i, col), hi = ca.at16h(i, col);
          if (lo != 0 || hi != 0) _nzCnt++;
          l = lo ^ 37 * hi;
        }
        if (l != 0) // ignore 0s in checksum to be consistent with sparse chunks
          checksum ^= (17 * (start + col)) ^ 23 * l;
      }
    } else if( isString ) { // String columns do not compute min/max/mean/sigma
      for (Chunk.SparseString sv = ca.sparseStr(col); sv.rowId() < ca._len; sv.nextNZ()) {
        if (sv.isNA()) _naCnt++;
        else {
          _nzCnt++;
          l = sv.val().hashCode();
        }
        if (l != 0) // ignore 0s in checksum to be consistent with sparse chunks
          checksum ^= (17 * (start + sv.rowId())) ^ 23 * l;
      }
    } else {
      checksum=new RollupStatsHelpers(this).numericChunkRollup(ca, col, checksum);

      // special case for sparse chunks
      // we need to merge with the mean (0) and variance (0) of the zeros count of 0s of the sparse chunk - which were skipped above
      // _rows is the count of non-zero rows
      // _mean is the mean of non-zero rows
      // _sigma is the mean of non-zero rows
      // handle the zeros
      if( ca.isSparseZero(col) ) {
        int zeros = ca._len - ca.sparseLenZero(col);
        if (zeros > 0) {
          for( int i=0; i<Math.min(_mins.length,zeros); i++ ) { min(0); max(0); }
          double zeromean = 0;
          double zeroM2 = 0;
          double delta = _mean - zeromean;
          _mean = (_mean * _rows + zeromean * zeros) / (_rows + zeros);
          _sigma += zeroM2 + delta*delta * _rows * zeros / (_rows + zeros); //this is the variance*(N-1), will do sqrt(_sigma/(N-1)) later in postGlobal
          _rows += zeros;
        }
      } else if(ca.isSparseNA(col)){
        _naCnt = ca._len - ca.sparseLenNA(col);
      }
    }
    _checksum = checksum;

    // UUID and String columns do not compute min/max/mean/sigma
    if( isUUID || isString) {
      Arrays.fill(_mins,Double.NaN);
      Arrays.fill(_maxs,Double.NaN);
      _mean = _sigma = Double.NaN;
    }
    return this;
  }

  void reduce( RollupStats rs ) {
    for( double d : rs._mins ) if (!Double.isNaN(d)) min(d);
    for( double d : rs._maxs ) if (!Double.isNaN(d)) max(d);
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

  double min( double d ) {
    assert(!Double.isNaN(d));
    for( int i=0; i<_mins.length; i++ )
      if( d < _mins[i] )
        { double tmp = _mins[i];  _mins[i] = d;  d = tmp; }
    return _mins[_mins.length-1];
  }
  double max( double d ) {
    assert(!Double.isNaN(d));
    for( int i=0; i<_maxs.length; i++ )
      if( d > _maxs[i] )
        { double tmp = _maxs[i];  _maxs[i] = d;  d = tmp; }
    return _maxs[_maxs.length-1];
  }

  private static class Roll extends MRTask<Roll> {
    final Key _rskey;
    RollupsAry _rs;
    Roll( H2OCountedCompleter cmp, Key rskey ) { super(cmp); _rskey=rskey; }
    @Override public void map( ChunkAry c ) {
      RollupStats [] ary = new RollupStats[c._numCols];
      for(int i = 0; i < c._numCols; ++i)
        ary[i] = new RollupStats(0).map(c,i);
      _rs = new RollupsAry(ary);
    }
    @Override public void reduce( Roll roll ) { _rs.reduce(roll._rs); }
    @Override public void postGlobal() {
      if( _rs == null )
        _rs = new RollupsAry(0);
      else {
        _rs.postGlobal();
      }
      for(int i = 0; i < _fr.numCols(); ++i) {
        // mean & sigma not allowed on more than 2 classes; for 2 classes the assumption is that it's true/false
        if (_fr.anyVec().isCategorical(i) && _fr.anyVec().domain(i).length > 2)
          _rs.setCategorical(i);
      }
    }
    // Just toooo common to report always.  Drowning in multi-megabyte log file writes.
    @Override public boolean logVerbose() { return false; }

    /**
     * Added to avoid deadlocks when running from idea in debug mode (evaluating toSgtring on mr task causes rollups to be computed)
     * @return
     */
    @Override public String toString(){return "Roll(" + _fr.anyVec()._key +")";}
  }

  private static NonBlockingHashMap<Key,RPC> _pendingRollups = new NonBlockingHashMap<>();

  // Histogram base & stride
  double h_base() { return _mins[0]; }
  double h_stride() { return h_stride(_bins.length); }
  private double h_stride(int nbins) { return (_maxs[0]-_mins[0]+(_isInt?1:0))/nbins; }

  // Compute expensive histogram
  private static class Histo extends MRTask<Histo> {
    final double [] _base, _stride; // Inputs
    final int [] _nbins;            // Inputs
    long[][] _bins;                // Outputs
    Histo( H2OCountedCompleter cmp, int [] nbins, double [] base, double [] stride) {
      super(cmp);
      _base = base;
      _stride = stride;
      _nbins = nbins;
    }
    @Override public void map( ChunkAry cs ) {
      _bins = new long[_nbins.length][];
      for(int c = 0; c < cs._numCols; ++c) {
        if(_nbins[c] == -1)continue;
        _bins[c] = MemoryManager.malloc8(_nbins[c]);
        for(Chunk.SparseNum sv = cs.sparseNum(c); sv.rowId() < cs._len; sv.nextNZ() ) {
          double d = sv.dval();
          if( !Double.isNaN(d) ) _bins[c][idx(c,d)]++;
        }
        // Sparse?  We skipped all the zeros; do them now
        if( cs.isSparseZero(c) )
          _bins[c][idx(c,0.0)] += (cs._len - cs.sparseLenZero(c));
      }
    }
    private int idx( int c, double d ) { int idx = (int)((d-_base[c])/_stride[c]); return Math.min(idx,_bins[c].length-1); }

    @Override public void reduce( Histo h ) {
      for(int i = 0; i < _nbins.length; ++i)
        if(_nbins[i] != -1)
          ArrayUtils.add(_bins[i],h._bins[i]);
    }
    // Just toooo common to report always.  Drowning in multi-megabyte log file writes.
    @Override public boolean logVerbose() { return false; }
  }


  // Task to compute rollups on its homenode if needed.
  // Only computes the rollups, does not fetch them, caller should fetch them via DKV store (to preserve caching).
  // Only comutes the rollups if needed (i.e. are null or do not have histo and histo is required)
  // If rs computation is already in progress, it will wait for it to finish.
  // Throws IAE if the Vec is being modified (or removed) while this task is in progress.
  static final class ComputeRollupsTask extends DTask<ComputeRollupsTask>{
    final Key _vecKey;
    final Key _rsKey;
    final boolean _computeHisto;

    public ComputeRollupsTask(Vec v, boolean computeHisto){
      super((byte)(Thread.currentThread() instanceof H2O.FJWThr ? currThrPriority()+1 : H2O.MIN_HI_PRIORITY-3));
      _vecKey = v._key;
      _rsKey = v.rollupStatsKey();
      _computeHisto = computeHisto;
    }

    private Value makeComputing(){
      CountedCompleter cc = getCompleter(); // should be null or RPCCall
      if(cc != null) assert cc.getCompleter() == null;
      return new Value(_rsKey,RollupsAry.makeComputing(cc == null?this:cc));
    }
    private void installResponse(Value nnn, RollupsAry rs) {
      Futures fs = new Futures();
      Value old = DKV.DputIfMatch(_rsKey, new Value(_rsKey, rs), nnn, fs);
      if(old != nnn)
        throw new IllegalArgumentException("Can not compute rollup stats while vec is being modified. (2)");
      fs.blockForPending();
    }

    @Override
    public void compute2() {
      assert _rsKey.home();
      final Vec vec = DKV.getGet(_vecKey);
      while(true) {
        Value v = DKV.get(_rsKey);
        RollupsAry rs = (v == null) ? null : v.<RollupsAry>get();
        // Fetched current rs from the DKV, rs can be:
        //   a) computed
        //        a.1) has histo or histo not required => do nothing
        //        a.2) no histo and histo is required  => only compute histo
        //   b) computing => wait for the task computing it to finish and check again
        //   c) mutating  => throw IAE
        //   d) null      => compute new rollups
        if (rs != null) {
          if (rs.isReady(vec,false)) {
            if (_computeHisto && !rs.isReady(vec,true)) { // a.2 => compute rollups
              CountedCompleter cc = getCompleter(); // should be null or RPCCall
              if(cc != null) assert cc.getCompleter() == null;
              // note: if cc == null then onExceptionalCompletion tasks waiting on this may be woken up before exception handling iff exception is thrown.
              Value nnn = makeComputing();
              Futures fs = new Futures();
              Value oldv = DKV.DputIfMatch(_rsKey, nnn, v, fs);
              fs.blockForPending();
              if(oldv == v){ // got the lock
                computeHisto(rs, vec, nnn);
                break;
              } // else someone else is modifying the rollups => try again
            } else
              break; // a.1 => do nothing
          } else if (rs.isComputing()) { // b) => wait for current computation to finish
            rs._tsk.join();
          } else if(rs.isMutating()) // c) => throw IAE
            throw new IllegalArgumentException("Can not compute rollup stats while vec is being modified. (3)");
        } else { // d) => compute the rollups
          final Value nnn = makeComputing();
          Futures fs = new Futures();
          Value oldv = DKV.DputIfMatch(_rsKey, nnn, v, fs);
          fs.blockForPending();
          if(oldv == v){ // got the lock, compute the rollups
            Roll r = new Roll(null,_rsKey).doAll(vec);
                // computed the stats, now compute histo if needed and install the response and quit
            r._rs._checksum ^= vec.length();
            if(_computeHisto)
              computeHisto(r._rs, vec, nnn);
            else
              installResponse(nnn, r._rs);
            break;
          } // else someone else is modifying the rollups => try again
        }
      }
      tryComplete();
    }

    final void computeHisto(final RollupsAry rsa, Vec vec, final Value nnn) {
      int [] nbins = new int[vec.numCols()];
      double [] base = new double[nbins.length];
      double [] stride = new double[nbins.length];
      for (int c = 0; c < rsa.numCols(); ++c) {
        RollupStats rs = rsa.getRollups(c);
        if(!(vec.isNumeric(c) || vec.isCategorical(c)) || rs._naCnt == vec.length()){
          nbins[c] = -1; continue;
        }
        // Constant: use a single bin
        double span = rs._maxs[0] - rs._mins[0];
        final long rows = vec.length() - rs._naCnt;
        assert rows > 0 : "rows = " + rows + ", vec.len() = " + vec.length() + ", naCnt = " + rs._naCnt;
        if (span == 0) {
          nbins[c] = -1;
          continue;
        }
        // Number of bins: MAX_SIZE by default.  For integers, bins for each unique int
        // - unless the count gets too high; allow a very high count for categoricals.
        int c_nbins = MAX_SIZE;
        if (rs._isInt && span < Integer.MAX_VALUE) {
          c_nbins = (int) span + 1;      // 1 bin per int
          int lim = vec.isCategorical() ? Categorical.MAX_CATEGORICAL_COUNT : MAX_SIZE;
          c_nbins = Math.min(lim, c_nbins); // Cap nbins at sane levels
        }
        nbins[c] = c_nbins;
        stride[c] = rs.h_stride(c_nbins);
        base[c] = rs.h_base();
      }
      Histo histo = new Histo(null, nbins, base, stride).doAll(vec);
      for(int i = 0; i < nbins.length; ++i){
        if(nbins[i] == -1) continue;
        RollupStats rs = rsa.getRollups(i);
        final long rows = vec.length() - rs._naCnt;
        if(ArrayUtils.sum(histo._bins[i]) != rows){
          System.out.println(ArrayUtils.sum(histo._bins[i]) + " != " + rows);
        }
        assert ArrayUtils.sum(histo._bins[i]) == rows;
        rs._bins = histo._bins[i];
        // Compute percentiles from histogram
        rs._pctiles = new double[Vec.PERCENTILES.length];
        int j = 0;                 // Histogram bin number
        int k = 0;                 // The next non-zero bin after j
        long hsum = 0;             // Rolling histogram sum
        double c_base = rs.h_base();
        double c_stride = rs.h_stride();
        double lastP = -1.0;       // any negative value to pass assert below first time
        for (int x = 0; x < Vec.PERCENTILES.length; x++) {
          final double P = Vec.PERCENTILES[x];
          assert P >= 0 && P <= 1 && P >= lastP;   // rely on increasing percentiles here. If P has dup then strange but accept, hence >= not >
          lastP = P;
          double pdouble = 1.0 + P * (rows - 1);   // following stats:::quantile.default type 7
          long pint = (long) pdouble;          // 1-based into bin vector
          double h = pdouble - pint;           // any fraction h to linearly interpolate between?
          assert P != 1 || (h == 0.0 && pint == rows);  // i.e. max
          while (hsum < pint) hsum += rs._bins[j++];
          // j overshot by 1 bin; we added _bins[j-1] and this goes from too low to either exactly right or too big
          // pint now falls in bin j-1 (the ++ happened even when hsum==pint), so grab that bin value now
          rs._pctiles[x] = c_base + c_stride * (j - 1);
          if (h > 0 && pint == hsum) {
            // linearly interpolate between adjacent non-zero bins
            //      i) pint is the last of (j-1)'s bin count (>1 when either duplicates exist in input, or stride makes dups at lower accuracy)
            // AND ii) h>0 so we do need to find the next non-zero bin
            if (k < j) k = j; // if j jumped over the k needed for the last P, catch k up to j
            // Saves potentially winding k forward over the same zero stretch many times
            while (rs._bins[k] == 0) k++;  // find the next non-zero bin
            rs._pctiles[x] += h * c_stride * (k - j + 1);
          } // otherwise either h==0 and we know which bin, or fraction is between two positions that fall in the same bin
          // this guarantees we are within one bin of the exact answer; i.e. within (max-min)/MAX_SIZE
        }
      }
      installResponse(nnn, rsa);
    }
  }
}
