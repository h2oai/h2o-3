package water.fvec;

import water.Futures;

import jsr166y.CountedCompleter;
import jsr166y.ForkJoinTask;
import water.*;
import water.H2O.H2OCallback;
import water.H2O.H2OCountedCompleter;
import water.parser.Categorical;
import water.parser.ValueString;
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
class RollupStats extends Iced {
  /** The count of missing elements.... or -2 if we have active writers and no
   *  rollup info can be computed (because the vector is being rapidly
   *  modified!), or -1 if rollups have not been computed since the last
   *  modification.   */

  volatile transient ForkJoinTask _tsk;

  volatile long _naCnt;
  // Computed in 1st pass
  double _mean, _sigma;
  long _checksum;
  long _rows, _nzCnt, _size, _pinfs, _ninfs;
  boolean _isInt=true;
  double[] _mins, _maxs;

  // Expensive histogram & percentiles
  // Computed in a 2nd pass, on-demand, by calling computeHisto
  private static final int MAX_SIZE = 1024; // Standard bin count; enums can have more bins
  volatile long[] _bins;
  // Approximate data value closest to the Xth percentile
  double[] _pctiles;

  public boolean hasStats(){return _naCnt >= 0;}
  public boolean hasHisto(){return _bins != null;}

  // Check for: Vector is mutating and rollups cannot be asked for
  boolean isMutating() { return _naCnt==-2; }
  // Check for: Rollups currently being computed
  private boolean isComputing() { return _naCnt==-1; }
  // Check for: Rollups available
  private boolean isReady() { return _naCnt>=0; }

  private RollupStats() {
    _mean = _sigma = Double.NaN;
    _size = _naCnt = 0;
  }

  private RollupStats( int mode ) { _naCnt = mode; }
  private static RollupStats makeComputing(Key rskey) { return new RollupStats(-1); }
  static RollupStats makeMutating (Key rskey) { return new RollupStats(-2); }

  private RollupStats map( Chunk c ) {
    _size = c.byteSize();
    _mins = new double[5];  Arrays.fill(_mins, Double.MAX_VALUE);
    _maxs = new double[5];  Arrays.fill(_maxs,-Double.MAX_VALUE);
    boolean isUUID = c._vec.isUUID();
    boolean isString = c._vec.isString();
    ValueString vs = new ValueString();
    if (isString) _isInt = false;
    // Checksum support
    long checksum = 0;
    long start = c._start;
    long l = 81985529216486895L;

    // Check for popular easy cases: All Constant
    double min=c.min(), max=c.max();
    if( min==max ) {              // All constant, and not NaN
      double d = min;             // It's the min, it's the max, it's the alpha and omega
      _checksum = (c.hasFloat()?Double.doubleToRawLongBits(d):(long)d)*c._len;
      Arrays.fill(_mins,d);
      Arrays.fill(_maxs,d);
      if( d == Double.POSITIVE_INFINITY) _pinfs++;
      else if( d == Double.NEGATIVE_INFINITY) _ninfs++;
      else {
        if( d != 0 ) _nzCnt=c._len;
        _mean = d;
        _rows=c._len;
      }
      _isInt = ((long)d) == d;
      _sigma = 0;               // No variance for constants
      return this;
    }

    // Check for popular easy cases: Boolean, possibly sparse, possibly NaN
    if( min==0 && max==1 ) {
      int zs = c._len-c.sparseLen(); // Easy zeros
      int nans = 0;
      // Hard-count sparse-but-zero (weird case of setting a zero over a non-zero)
      for( int i=c.nextNZ(-1); i< c._len; i=c.nextNZ(i) )
        if( c.isNA(i) ) nans++;
        else if( c.at8(i)==0 ) zs++;
      int os = c._len-zs-nans;  // Ones
      _nzCnt = os;
      for( int i=0; i<Math.min(_mins.length,zs); i++ ) { min(0); max(0); }
      for( int i=0; i<Math.min(_mins.length,os); i++ ) { min(1); max(1); }
      _rows = zs+os;
      _mean = (double)os/_rows;
      _sigma = zs*(0.0-_mean)*(0.0-_mean) + os*(1.0-_mean)*(1.0-_mean);
      return this;
    }


    // Walk the non-zeros
    for( int i=c.nextNZ(-1); i< c._len; i=c.nextNZ(i) ) {
      if( c.isNA(i) ) {
        _naCnt++;
      } else if( isUUID ) {   // UUID columns do not compute min/max/mean/sigma
        long lo = c.at16l(i), hi = c.at16h(i);
        if (lo != 0 || hi != 0) _nzCnt++;
        l = lo ^ 37*hi;
      } else if( isString ) { // String columns do not compute min/max/mean/sigma
        _nzCnt++;
        l = c.atStr(vs, i).hashCode();
      } else {                  // All other columns have useful rollups
        double d = c.atd(i);
        l = c.hasFloat()?Double.doubleToRawLongBits(d):c.at8(i);
        if( d == Double.POSITIVE_INFINITY) _pinfs++;
        else if( d == Double.NEGATIVE_INFINITY) _ninfs++;
        else {
          if( d != 0 ) _nzCnt++;
          min(d);  max(d);
          _mean += d;
          _rows++;
          if( _isInt && ((long)d) != d ) _isInt = false;
        }
      }
      if(l != 0) // ignore 0s in checksum to be consistent with sparse chunks
        checksum ^= (17 * (start+i)) ^ 23*l;
    }
    _checksum = checksum;

    // Sparse?  We skipped all the zeros; do them now
    if( c.isSparse() ) {
      int zeros = c._len - c.sparseLen();
      for( int i=0; i<Math.min(_mins.length,zeros); i++ ) { min(0); max(0); }
      _rows += zeros;
    }

    // UUID and String columns do not compute min/max/mean/sigma
    if( isUUID || isString) {
      Arrays.fill(_mins,Double.NaN);
      Arrays.fill(_maxs,Double.NaN);
      _mean = _sigma = Double.NaN;
    } else if( !Double.isNaN(_mean) && _rows > 0 ) {
      _mean = _mean / _rows;
      // Handle all zero rows
      int zeros = c._len - c.sparseLen();
      _sigma += _mean*_mean*zeros;
      // Handle all non-zero rows
      for( int i=c.nextNZ(-1); i< c._len; i=c.nextNZ(i) ) {
        double d = c.atd(i);
        if( !Double.isNaN(d) ) {
          d -= _mean;
          _sigma += d*d;
        }
      }
    }
    return this;
  }

  private void reduce( RollupStats rs ) {
    for( double d : rs._mins ) min(d);
    for( double d : rs._maxs ) max(d);
    _naCnt += rs._naCnt;
    _nzCnt += rs._nzCnt;
    _pinfs += rs._pinfs;
    _ninfs += rs._ninfs;
    double delta = _mean - rs._mean;
    if (_rows == 0) { _mean = rs._mean;  _sigma = rs._sigma; }
    else {
      _mean = (_mean * _rows + rs._mean * rs._rows) / (_rows + rs._rows);
      _sigma = _sigma + rs._sigma + delta*delta * _rows*rs._rows / (_rows+rs._rows);
    }
    _rows += rs._rows;
    _size += rs._size;
    _isInt &= rs._isInt;
    _checksum ^= rs._checksum;
  }

  private void min( double d ) {
    if( d >= _mins[_mins.length-1] ) return;
    for( int i=0; i<_mins.length; i++ )
      if( d < _mins[i] )
        { double tmp = _mins[i];  _mins[i] = d;  d = tmp; }
  }
  private void max( double d ) {
    if( d <= _maxs[_maxs.length-1] ) return;
    for( int i=0; i<_maxs.length; i++ )
      if( d > _maxs[i] )
        { double tmp = _maxs[i];  _maxs[i] = d;  d = tmp; }
  }

  private static class Roll extends MRTask<Roll> {
    final Key _rskey;
    RollupStats _rs;
    Roll( H2OCountedCompleter cmp, Key rskey ) { super(cmp); _rskey=rskey; }
    @Override public void map( Chunk c ) { _rs = new RollupStats(0).map(c); }
    @Override public void reduce( Roll roll ) { _rs.reduce(roll._rs); }
    @Override public void postGlobal() {  _rs._sigma = Math.sqrt(_rs._sigma/(_rs._rows-1));}
    // Just toooo common to report always.  Drowning in multi-megabyte log file writes.
    @Override public boolean logVerbose() { return false; }
  }

  private static RollupStats check( Key rskey, RollupStats rs, Value val ) {
    if( val == null ) return rs==null ? makeComputing(rskey) : rs;
    rs = val.get(RollupStats.class);
    if( rs.isReady() ) return rs; // All good
    if( rs.isMutating() )
      throw new IllegalArgumentException("Cannot ask for roll-up stats while the vector is being actively written.");
    assert rs.isComputing();
    return rs;                  // In progress
  }

  static void start(final Vec vec, Futures fs){ start(vec,fs,false);}
  static void start(final Vec vec, Futures fs, boolean computeHisto) {
    final Key rskey = vec.rollupStatsKey();
    RollupStats rs = getOrNull(vec);
    if(rs == null || computeHisto && !rs.hasHisto())
      fs.add(new RPC(vec.rollupStatsKey().home_node(),new ComputeRollupsTask(vec,computeHisto)).addCompleter(new H2OCallback() {
        @Override
        public void callback(H2OCountedCompleter h2OCountedCompleter) {
          DKV.get(vec.rollupStatsKey()); // fetch new results via DKV to enable caching of the results.
        }
      }).call());
  }

  static RollupStats get(Vec vec, boolean computeHisto) {
    final Key rskey = vec.rollupStatsKey();
    RollupStats rs = getOrNull(vec);
    while(rs == null || computeHisto && !rs.hasHisto() && DKV.get(vec._key) != null){
      // 1. compute
      RPC.call(rskey.home_node(),new ComputeRollupsTask(vec, computeHisto)).get();
      // 2. fetch - done in two steps to go through standard DKV.get and enable local caching
      rs = getOrNull(vec);
    }
    return rs;
  }
  // Allow a bunch of rollups to run in parallel.  If Futures is passed in, run
  // the rollup in the background and do not return.
  static RollupStats get(Vec vec) { return get(vec,false);}
  // Fetch if present, but do not compute
  static RollupStats getOrNull(Vec vec) {
    final Key rskey = vec.rollupStatsKey();
    Value val = DKV.get(rskey);
    if( val == null) {
      if (vec.length() > 0)
        return null;
      else  // empty vec, presuming header file
        return new RollupStats();
    } else {
      RollupStats rs = val.get(RollupStats.class);
      return rs.isReady() ? rs : null;
    }
  }
  // Histogram base & stride
  double h_base() { return _mins[0]; }
  double h_stride() { return h_stride(_bins.length); }
  private double h_stride(int nbins) { return (_maxs[0]-_mins[0]+(_isInt?1:0))/nbins; }

  // Compute expensive histogram
  private static class Histo extends MRTask<Histo> {
    final double _base, _stride; // Inputs
    final int _nbins;            // Inputs
    long[] _bins;                // Outputs
    Histo( H2OCountedCompleter cmp, RollupStats rs, int nbins ) { super(cmp);_base = rs.h_base(); _stride = rs.h_stride(nbins); _nbins = nbins; }
    @Override public void map( Chunk c ) {
      _bins = new long[_nbins];
      for( int i=c.nextNZ(-1); i< c._len; i=c.nextNZ(i) ) {
        double d = c.atd(i);
        if( Double.isNaN(d) ) continue;
        _bins[idx(d)]++;
      }
      // Sparse?  We skipped all the zeros; do them now
      if( c.isSparse() )
        _bins[idx(0.0)] += (c._len - c.sparseLen());
    }
    private int idx( double d ) { int idx = (int)((d-_base)/_stride); return Math.min(idx,_bins.length-1); }

    @Override public void reduce( Histo h ) { ArrayUtils.add(_bins,h._bins); }
    // Just toooo common to report always.  Drowning in multi-megabyte log file writes.
    @Override public boolean logVerbose() { return false; }
  }


  // Task to compute rollups on its homenode
  static final class ComputeRollupsTask extends DTask<ComputeRollupsTask>{
    final byte _priority;
    final Key _vecKey;
    final Key _rsKey;
    final boolean _computeHisto;
    transient volatile boolean _didCompute;
    private transient volatile Value nnn;
    private transient volatile RollupStats _rs;

    public ComputeRollupsTask(Vec v, boolean computeHisto){
      _priority = nextThrPriority();
      _vecKey = v._key;
      _rsKey = v.rollupStatsKey();
      _computeHisto = computeHisto;
    }
    @Override public byte priority(){return _priority; }

    final void computeHisto(Vec vec){
      // All NAs or non-math; histogram has zero bins
      if( _rs._naCnt == vec.length() || vec.isUUID() ) { _rs._bins = new long[0]; return; }
      // Constant: use a single bin
      double span = _rs._maxs[0]-_rs._mins[0];
      final long rows = vec.length()-_rs._naCnt;
      assert rows > 0:"rows = " + rows + ", vec.len() = " + vec.length() + ", naCnt = " + _rs._naCnt;
      if( span==0 ) { _rs._bins = new long[]{rows}; return;  }

      // Number of bins: MAX_SIZE by default.  For integers, bins for each unique int
      // - unless the count gets too high; allow a very high count for enums.
      int nbins=MAX_SIZE;
      if( _rs._isInt && (int)span==span ) {
        nbins = (int)span+1;      // 1 bin per int
        int lim = vec.isEnum() ? Categorical.MAX_ENUM_SIZE : MAX_SIZE;
        nbins = Math.min(lim,nbins); // Cap nbins at sane levels
      }
      addToPendingCount(1);
      new Histo(new H2OCallback<Histo>(this){
        @Override
        public void callback(Histo histo) {
          _rs._bins = histo._bins;
          // Compute percentiles from histogram
          _rs._pctiles = new double[Vec.PERCENTILES.length];
          int j=0;                    // Histogram bin number
          long hsum=0;                // Rolling histogram sum
          double base = _rs.h_base();
          double stride = _rs.h_stride();
          long oldPint = 0;
          double oldVal = _rs._mins[0];
          for( int i=0; i<Vec.PERCENTILES.length; i++ ) {
            final double P = Vec.PERCENTILES[i];
            long pint = (long)(P*rows);
            if(pint == oldPint) { // can happen if rows < 100
              _rs._pctiles[i] = oldVal;
              continue;
            }
            oldPint = pint;
            while( hsum < pint ) hsum += _rs._bins[j++];
            // j overshot by 1 bin; we added _bins[j-1] and this goes from too low to too big
            _rs._pctiles[i] = base+stride*(j-1);
            // linear interpolate stride, based on fraction of bin
            _rs._pctiles[i] += stride*((double)(pint-(hsum-_rs._bins[j-1]))/_rs._bins[j-1]);
            oldVal = _rs._pctiles[i];
          }
        }
      },_rs,nbins).dfork(vec); // intentionally using dfork here to increase priority level
    }
    final void computeRollups(final Key rollUpsKey, Value oldValue){
      assert rollUpsKey.home();
      // Attempt to flip from no(or incomplete)-rollups to computing-rollups
      RollupStats newRs = RollupStats.makeComputing(_rsKey);
      final Value nnn = new Value(rollUpsKey,newRs);
      CountedCompleter cc = getCompleter(); // should be null or RPCCall
      if(cc != null) assert cc.getCompleter() == null;
      // note: if cc == null then onExceptionalCompletion tasks waiting on this may be woken up before exception handling iff exception is thrown.
      newRs._tsk = cc == null?this:cc; // need the task on the top of the tree, join() only works with tasks with no completers
      Futures fs = new Futures();
      Value v = DKV.DputIfMatch(rollUpsKey,nnn,oldValue,fs);
      if(v != oldValue && v == null) {
        //fixme: vector has been modified or removed? no way to know (Rollups may already be null while the Vec header invalidate is still in progress)
        v = DKV.DputIfMatch(rollUpsKey,nnn,oldValue = null,fs);
      }
      if(v == oldValue) { // got the lock, start the task to compute the rollups/histo/checksum
        this.nnn = nnn;
        _didCompute = true;
        final Vec vec = DKV.getGet(_vecKey);
        assert vec != null : "Asking for rollup stats but key was not found/missing/deleted:" +_vecKey;
        if(!_rs.hasStats()){
          addToPendingCount(1);
          new Roll(new H2OCallback<Roll>(this) {
            @Override
            public void callback(Roll r) {
              r._rs._checksum ^= vec.length();
              _rs = r._rs;
              if(_computeHisto)
                computeHisto(vec);
            }
          },rollUpsKey).dfork(vec);
        } else {
          if(_computeHisto)
            computeHisto(vec);
        }
      } else { // someone else already has the lock, wait for him to finish and check again
        newRs = v.get();
        updateRollups(newRs, v);
      }
    }

    private void updateRollups(RollupStats rs, Value oldValue){
      while(rs != null && rs.isComputing()){
        rs._tsk.join(); // potential problem here?: can block all live threads and cause deadlock in case of many parallel requests,
                        // that's why forking of tasks via dfork to run at higher priority level, however, priority level depends also on
                        // the task that requests rollups, so no guarantee here, for now, avoid many parallel rollup requests
        oldValue = DKV.get(_rsKey);
        rs = oldValue == null?null:oldValue.<RollupStats>get();
      }
      if(rs != null && rs.isMutating())
        throw new IllegalArgumentException("Can not compute rollup stats while vec is being changed.");
      if(rs == null)
        rs = RollupStats.makeComputing(_rsKey);
      _rs = rs;
      computeRollups(_rsKey, oldValue);
    }
    @Override
    protected void compute2() {
      assert _rsKey.home();
      Value v = DKV.get(_rsKey);
      updateRollups(v == null?null:v.<RollupStats>get(),v);
      tryComplete();
    }
    @Override // fetch rollups via DKV when done, so that the result is cached for others to use
    public void onCompletion(CountedCompleter cc) {
      if(_didCompute){
        Futures fs = new Futures();
        Value old = DKV.DputIfMatch(_rsKey,new Value(_rsKey,_rs),nnn,fs);
        fs.blockForPending();
        assert old == nnn || old == null; // should've succeeded (had the lock) unless Vec has been deleted in the meantime
      }
    }
    @Override
    public boolean onExceptionalCompletion(Throwable t, CountedCompleter cc){
      // if got exception while having the lock, do cleanup!
      // hopefully this happens before anyone waiting on join() gets woken up
      if(_didCompute) {
        Futures fs= new Futures();
        DKV.remove(_rsKey, fs);
        fs.blockForPending();
      }
      return true;
    }

  }



}
