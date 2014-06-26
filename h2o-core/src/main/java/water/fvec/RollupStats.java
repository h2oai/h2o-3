package water.fvec;

import java.util.Arrays;
import water.*;

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
class RollupStats extends DTask<RollupStats> {
  final Key _rskey;
  final private byte _priority;
  /** The count of missing elements.... or -2 if we have active writers and no
   *  rollup info can be computed (because the vector is being rapidly
   *  modified!), or -1 if rollups have not been computed since the last
   *  modification.   */
  long _naCnt;
  double _mean, _sigma;
  long _rows, _nzCnt, _size, _pinfs, _ninfs;
  boolean _isInt=true;
  double[] _mins, _maxs;

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
    boolean isUUID = c._vec._isUUID;
    // Walk the non-zeros
    for( int i=c.nextNZ(-1); i<c._len; i=c.nextNZ(i) ) {
      double d = c.at0(i);
      if( Double.isNaN(d) ) {
        _naCnt++;

      } else if( isUUID ) {   // UUID columns do not compute min/max/mean/sigma
        if( c.at16l0(i)==0 && c.at16h0(i)==0 ) _nzCnt++;

      } else {                  // All other columns have useful rollups

        if( d == Double.POSITIVE_INFINITY) _pinfs++;
        else if( d == Double.NEGATIVE_INFINITY) _ninfs++;
        else {
          if( d == 0 ) _nzCnt++;
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
      if( zeros > 0 ) { min(0); max(0); } // At least 1 zero?
      _rows += zeros;
    }

    // UUID columns do not compute min/max/mean/sigma
    if( isUUID ) {
      _mean = _sigma = Double.NaN;
    } else if( !Double.isNaN(_mean) && _rows > 0 ) {
      _mean = _mean / _rows;
      for( int i=0; i<c._len; i++ ) {
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
    Roll( Key rskey ) { _rskey=rskey; }
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

  // Allow a bunch of rollups to run in parallel.  If Futures is passed in, run
  // the rollup in the background and do not return.
  static RollupStats get(Vec vec, Futures fs) {
    final Key rskey = vec.rollupStatsKey();
    RollupStats rs = check(rskey,null,DKV.get(rskey)); // Look for cached copy
    if( rs.isReady() ) return rs;                 // All good
    assert rs.isComputing();
    // No local cached Rollups; go ask Master for a copy
    H2ONode h2o = rskey.home_node();
    if( h2o.equals(H2O.SELF) ) {
      if( fs == null ) { rs.compute2(); return rs; } // Block till ready
      fs.add(H2O.submitTask(rs)); 
      throw H2O.unimpl();
      //return null;
    } else {                                   // Run remotely
      RPC<RollupStats> rpc = RPC.call(h2o,rs); // Run remote
      if( fs == null ) return rpc.get();       // Block till ready
      throw H2O.unimpl();
      //fs.add(comp); 
      //return null;
    }
  }

  // Fetch if present, but do not compute
  static RollupStats getOrNull(Vec vec) {
    final Key rskey = vec.rollupStatsKey();
    Value val = DKV.get(rskey);
    if( val == null ) return null;
    RollupStats rs = val.get(RollupStats.class);
    return rs.isReady() ? rs : null;
  }

  @Override protected void compute2() {
    assert _rskey.home();  // Only runs on Home node
    assert isComputing();
    Futures fs = new Futures(); // Just to track invalidates
    
    // Attempt to flip from no-rollups to computing-rollups
    Value nnn = new Value(_rskey,this);
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
    Vec vec = DKV.get(Vec.getVecKey(_rskey)).get();
    rs = new Roll(_rskey).doAll(vec)._rs;
    copyOver(rs);               // Copy over from rs into self
    assert isReady();           // We're Ready!!!
    Value old2 = DKV.DputIfMatch(_rskey,new Value(_rskey,this),nnn,fs);
    assert old2==nnn;           // Since we "have the lock" this "must work"
    fs.blockForPending();       // Block for any invalidates
    tryComplete();
  }
}
