package water.fvec;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jsr166y.CountedCompleter;
import water.*;
import water.H2O.H2OCountedCompleter;
import water.parser.Enum;
import water.util.ArrayUtils;
import water.H2O.H2OFuture;

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
public class RollupStats extends DTask<RollupStats> {
  final Key _rskey;
  final private byte _priority;
  /** The count of missing elements.... or -2 if we have active writers and no
   *  rollup info can be computed (because the vector is being rapidly
   *  modified!), or -1 if rollups have not been computed since the last
   *  modification.   */
  public long _naCnt;
  // Computed in 1st pass
  public double _mean, _sigma;
  public long _rows, _nzCnt, _size, _pinfs, _ninfs;
  public boolean _isInt=true;
  public double[] _mins, _maxs;

  // Expensive histogram & percentiles
  // Computed in a 2nd pass, on-demand, by calling computeHisto
  private final int MAX_SIZE = 1024;
  volatile public long[] _bins;
  // Approximate data value closest to the Xth percentile
  public static final double PERCENTILES[] = {0.01,0.10,0.25,1.0/3.0,0.50,2.0/3.0,0.75,0.90,0.99};
  public double[] _pctiles;

  // Check for: Vector is mutating and rollups cannot be asked for
  boolean isMutating() { return _naCnt==-2; }
  // Check for: Rollups currently being computed
  private boolean isComputing() { return _naCnt==-1; }
  // Check for: Rollups available
  private boolean isReady() { return _naCnt>=0; }

  private RollupStats( Key rskey, int mode ) { _rskey = rskey; _naCnt = mode; _priority = nextThrPriority(); }
  @Override public byte priority() { return _priority; }
  private static RollupStats makeComputing(Key rskey) { return new RollupStats(rskey,-1); }
  static RollupStats makeMutating (Key rskey) { return new RollupStats(rskey,-2); }

  private RollupStats map( Chunk c ) {
    _size = c.byteSize();
    _mins = new double[5];  Arrays.fill(_mins, Double.MAX_VALUE);
    _maxs = new double[5];  Arrays.fill(_maxs,-Double.MAX_VALUE);
    boolean isUUID = c._vec.isUUID();
    boolean isString = c._vec.isString();
    if (isString) _isInt = false;
    // Walk the non-zeros
    for( int i=c.nextNZ(-1); i< c._len; i=c.nextNZ(i) ) {
      if( c.isNA0(i) ) {
        _naCnt++;
      } else if( isUUID ) {   // UUID columns do not compute min/max/mean/sigma
        if (c.at16l0(i) != 0 || c.at16h0(i) != 0) _nzCnt++;
      } else if( isString ) { // String columns do not compute min/max/mean/sigma
        _nzCnt++;
      } else {                  // All other columns have useful rollups
        double d = c.at0(i);
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
    }

    // Sparse?  We skipped all the zeros; do them now
    if( c.isSparse() ) {
      int zeros = c._len - c.sparseLen();
      for( int i=0; i<Math.min(_mins.length,zeros); i++ ) { min(0); max(0); }
      _rows += zeros;
    }

    // UUID and String columns do not compute min/max/mean/sigma
    if( isUUID || isString) {
      _mean = _sigma = Double.NaN;
    } else if( !Double.isNaN(_mean) && _rows > 0 ) {
      _mean = _mean / _rows;
      for( int i=0; i< c._len; i++ ) {
        if( !c.isNA0(i) ) {
          double d = c.at0(i)-_mean;
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
      _mean = (_mean*_rows + rs._mean*rs._rows)/(_rows + rs._rows);
      _sigma = _sigma + rs._sigma + delta*delta * _rows*rs._rows / (_rows+rs._rows);
    }
    _rows += rs._rows;
    _size += rs._size;
    _isInt &= rs._isInt;
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
    @Override public void map( Chunk c ) { _rs = new RollupStats(_rskey,0).map(c); }
    @Override public void reduce( Roll roll ) { _rs.reduce(roll._rs); }
    @Override public void postGlobal() { _rs._sigma = Math.sqrt(_rs._sigma/(_rs._rows-1)); }
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


  public static H2OFuture<RollupStats> get(Vec v){
    final Key rskey = v.rollupStatsKey();
    final RollupStats rs = check(rskey,null,DKV.get(rskey)); // Look for cached copy
    if( rs.isReady() ) return new H2OFuture<RollupStats>(){
      @Override public boolean cancel(boolean mayInterruptIfRunning) {return false;}
      @Override public boolean isCancelled() { return false;}
      @Override public boolean isDone() {return true;}
      @Override public RollupStats get() throws InterruptedException, ExecutionException {return rs; }
      @Override public RollupStats get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {return rs;}
    };                 // All good
    assert rs.isComputing();
    // No local cached Rollups; go ask Master for a copy
    H2ONode h2o = rskey.home_node();
    final Future fs;
    if( h2o.equals(H2O.SELF) )
      fs = (H2O.submitTask(rs));
    else                        // Run remotely
      fs = (RPC.call(h2o,rs)); // Run remote
    return new H2OFuture<RollupStats>() {
      @Override public boolean cancel(boolean mayInterruptIfRunning) { return fs.cancel(mayInterruptIfRunning);}
      @Override public boolean isCancelled() { return fs.isCancelled();}
      @Override public boolean isDone() { return fs.isDone();}
      @Override public RollupStats get() throws InterruptedException, ExecutionException { fs.get(); return rs;}
      @Override public RollupStats get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException { fs.get(timeout, unit); return rs;}
    };
  }

  // Allow a bunch of rollups to run in parallel.  If Futures is passed in, run
  // the rollup in the background and do not return.
//  public static RollupStats get(Vec vec) {
//    final Key rskey = vec.rollupStatsKey();
//    RollupStats rs = check(rskey,null,DKV.get(rskey)); // Look for cached copy
//    if( rs.isReady() ) return rs;                 // All good
//    assert rs.isComputing();
//    // No local cached Rollups; go ask Master for a copy
//    if( rskey.home() ) {
//      rs.compute2();
//      return rs;  // Block till ready
//    } else                                    // Run remotely
//      return RPC.call(rskey.home_node(),rs).get(); // Run remote
//  }

  // Fetch if present, but do not compute
  static RollupStats getOrNull(Vec vec) {
    final Key rskey = vec.rollupStatsKey();
    Value val = DKV.get(rskey);
    if( val == null ) return null;
    RollupStats rs = val.get(RollupStats.class);
    return rs.isReady() ? rs : null;
  }

  private transient Value nnn;
  private transient Futures fs;
  @Override protected void compute2() {
    assert _rskey.home();  // Only runs on Home node
    assert isComputing();
    // Attempt to flip from no-rollups to computing-rollups
    nnn = new Value(_rskey,this);
    fs = new Futures();
    Value old = DKV.DputIfMatch(_rskey,nnn,null,fs);
    RollupStats rs = check(_rskey,this,old);
    if( rs.isReady() ) {        // Old stuff already is good stuff
      copyOver(rs);             // Update self with good stuff
      // tryComplete(); return;
      throw H2O.unimpl();
    }
    if( rs!=this ) {
      // need to block on an in-progress roll-up
      throw H2O.unimpl();
    }
    // This call to DKV "get the lock" on making the Rollups.
    // Do them Right Here, Right Now.
    Vec vec = Vec.getVecKey(_rskey).get();
    new Roll(this,_rskey).asyncExec(vec);
  }
  @Override public void onCompletion(CountedCompleter caller){
    RollupStats rs = ((Roll)caller)._rs;
    copyOver(rs);               // Copy over from rs into self
    assert isReady();           // We're Ready!!!
    Value old2 = DKV.DputIfMatch(_rskey,new Value(_rskey,this),nnn,fs);
    assert old2==nnn;           // Since we "have the lock" this "must work"
    fs.blockForPending();       // Block for any invalidates
  }

  // ----------------------------
  // Compute the expensive histogram on-demand
  public static void computeHisto(Vec vec) { computeHisto(vec,new Futures()).blockForPending(); }
  // Version that allows histograms to be computed in parallel
  static Futures computeHisto(Vec vec, Futures fs) {
    while( true ) {
      RollupStats rs = get(vec).getResult(); // Block for normal histogram
      if( rs._bins != null ) return fs;
      rs.computeHisto_impl(vec);
      Value old = DKV.get(rs._rskey);
      if( old.get(RollupStats.class) == rs ) {  // Nothing changed during the compute...
        DKV.DputIfMatch(rs._rskey,new Value(rs._rskey,rs),old,fs);
        return fs;
      }
    }
  }

  // Compute the expensive histogram
  private void computeHisto_impl(Vec vec) {
    // All NAs or non-math; histogram has zero bins
    if( _naCnt == vec.length() || vec.isUUID() ) { _bins = new long[0]; return; }
    // Constant: use a single bin
    double span = _maxs[0]-_mins[0];
    long rows = vec.length()-_naCnt;
    if( span==0 ) { _bins = new long[]{rows}; return;  }

    // Number of bins: MAX_SIZE by default.  For integers, bins for each unique int
    // - unless the count gets too high; allow a very high count for enums.
    int nbins=MAX_SIZE;
    if( _isInt && (int)span==span ) {
      nbins = (int)span+1;      // 1 bin per int
      int lim = vec.isEnum() ? Enum.MAX_ENUM_SIZE : MAX_SIZE;
      nbins = Math.min(lim,nbins); // Cap nbins at sane levels
    }
    _bins = new Histo(this,nbins).doAll(vec)._bins;

    // Compute percentiles from histogram
    _pctiles = new double[PERCENTILES.length];
    int j=0;                    // Histogram bin number
    long hsum=0;                // Rolling histogram sum
    double base = h_base();
    double stride = h_stride();
    for( int i=0; i<PERCENTILES.length; i++ ) {
      final double P = PERCENTILES[i];
      long pint = (long)(P*rows);
      while( hsum < pint ) hsum += _bins[j++];
      // j overshot by 1 bin; we added _bins[j-1] and this goes from too low to too big
      _pctiles[i] = base+stride*(j-1);
      // linear interpolate stride, based on fraction of bin
      _pctiles[i] += stride*((double)(pint-(hsum-_bins[j-1]))/_bins[j-1]);
    }

  }

  // Histogram base & stride
  public double h_base() { return _mins[0]; }
  public double h_stride() { return h_stride(_bins.length); }
  private double h_stride(int nbins) { return (_maxs[0]-_mins[0]+(_isInt?1:0))/nbins; }

  // Compute expensive histogram
  private static class Histo extends MRTask<Histo> {
    final double _base, _stride; // Inputs
    final int _nbins;            // Inputs
    long[] _bins;                // Outputs
    Histo( RollupStats rs, int nbins ) { _base = rs.h_base(); _stride = rs.h_stride(nbins); _nbins = nbins; }
    @Override public void map( Chunk c ) {
      _bins = new long[_nbins];
      for( int i=c.nextNZ(-1); i< c._len; i=c.nextNZ(i) ) {
        double d = c.at0(i);
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

}
