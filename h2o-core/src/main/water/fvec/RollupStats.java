package water.fvec;

import water.*;

/** A class to compute the rollup stats */
class RollupStats extends Iced {
  /** The count of missing elements.... or -2 if we have active writers and no
   *  rollup info can be computed (because the vector is being rapidly
   *  modified!), or -1 if rollups have not been computed since the last
   *  modification.   */
  double _min=Double.MAX_VALUE, _max=-Double.MAX_VALUE, _mean, _sigma;
  long _rows, _naCnt, _size;
  boolean _isInt=true;

  // Check for: Vector is mutating and rollups cannot be asked for
  private boolean isMutating() { return _naCnt==-2; }
  // Check for: Rollups currently being computed
  private boolean isComputing() { return _naCnt==-1; }
  // Check for: Rollups available
  private boolean isReady() { return _naCnt>=0; }

  private static RollupStats MUTATING = new RollupStats(-2);
  private static RollupStats COMPUTING = new RollupStats(-1);
  private RollupStats( int mode ) { _naCnt = mode; }

  private RollupStats map( Chunk c ) {
    _size = c.byteSize();
    for( int i=0; i<c._len; i++ ) {
      double d = c.at0(i);
      if( Double.isNaN(d) ) _naCnt++;
      else {
        if( d < _min ) _min = d;
        if( d > _max ) _max = d;
        _mean += d;
        _rows++;
        if( _isInt && ((long)d) != d ) _isInt = false;
      }
    }
    _mean = _mean / _rows;
    for( int i=0; i<c._len; i++ ) {
      if( !c.isNA0(i) ) {
        double d = c.at0(i);
        _sigma += (d - _mean) * (d - _mean);
      }
    }
    return this;
  }

  private void reduce( RollupStats rs ) {
    _min = Math.min(_min,rs._min);
    _max = Math.max(_max,rs._max);
    _naCnt += rs._naCnt;
    double delta = _mean - rs._mean;
    if (_rows == 0) { _mean = rs._mean;  _sigma = rs._sigma; }
    else if (rs._rows > 0) {
      _mean = (_mean*_rows + rs._mean*rs._rows)/(_rows + rs._rows);
      _sigma = _sigma + rs._sigma + delta*delta * _rows*rs._rows / (_rows+rs._rows);
    }
    _rows += rs._rows;
    _size += rs._size;
    _isInt &= rs._isInt;
  }

  private static class Roll extends MRTask<Roll> {
    RollupStats _rs;
    @Override public void map( Chunk c ) { _rs = new RollupStats(0).map(c); }
    @Override public void reduce( Roll roll ) { _rs.reduce(roll._rs); }
    // Just toooo common to report always.  Drowning in multi-megabyte log file writes.
    @Override public boolean logVerbose() { return false; }
  }

  // Allow a bunch of rollups to run in parallel.  If Futures is passed in, run
  // the rollup in the background and do not return.
  static RollupStats get(Vec vec, Futures fs) {
    Key rskey = vec.rollupStatsKey();
    Value val = DKV.get(rskey); // Look for cached copy
    if( val != null ) {
      RollupStats rs = (RollupStats)val.get();
      if( rs.isReady() ) return rs;
      if( rs.isMutating() )
        throw new IllegalArgumentException("Cannot ask for roll-up stats while the vector is being actively written.");
      assert rs.isComputing();
      throw H2O.unimpl();       // untested; just flow into next code
    }
    // No local cached Rollups; go ask Master for a copy

    // only drive the rollups from master; 
    // dtask (similar to Atomic) to run a job remotely, 
    // remote job looks for in-progress; piles-on the wakeup list
    // else remote job atomic flips to in-progress; reply on wakeuplist, then kick off rollup


    throw H2O.unimpl();
    //RPC atom = new Atomic() { 
    //    @Override protected Value atomic(Value val) {
    //      throw H2O.unimpl();
    //    }
    //  }.fork(rskey);
    //if( fs == null ) return atom.get(); // Block & return Rollups
    //fs.add(atom);                       // Toss 'em onto the Futures list
    //return null;                        // And no rollups right now
  }

}
