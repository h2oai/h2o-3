package water.currents;

import water.H2O;
import water.util.SB;

import java.util.ArrayList;
import java.util.Arrays;

/** A collection of base/stride/cnts.  Bases are monotonically increasing, and
 *  base+stride*cnt is always less than the next base.  This is a syntatic form
 *  only, and never executes and never gets on the execution stack.
 */
class ASTNumList extends AST {
  final double _bases[], _strides[];
  final long _cnts[];
  ASTNumList( Exec e ) {
    ArrayList<Double> bases  = new ArrayList<>();
    ArrayList<Double> strides= new ArrayList<>();
    ArrayList<Long>   cnts   = new ArrayList<>();

    // Parse a number list
    while( true ) {
      char c = e.skipWS();
      if( c==']' ) break;
      if( c=='#' ) e._x++;
      double base = e.number(), cnt=1, stride=1;
      c = e.skipWS();
      if( c==':' ) {
        e.xpeek(':'); e.skipWS();
        cnt = e.number();
        if( cnt < 1 || ((long)cnt) != cnt )
          throw new IllegalArgumentException("Count must be a integer larger than zero, "+cnt);
        c = e.skipWS();
        if( c==':' ) {
          e.xpeek(':'); e.skipWS();
          stride = e.number();
          if( stride < 0 )
            throw new IllegalArgumentException("Stride must be positive, "+stride);
          c = e.skipWS();
        }
      }
      bases.add(base);
      cnts.add((long)cnt);  
      strides.add(stride);
      // Optional comma seperating span
      if( c==',') e.xpeek(',');
    }
    e.xpeek(']');

    // Convert fixed-sized arrays
    _bases  = new double[bases.size()];
    _strides= new double[bases.size()];
    _cnts   = new long  [bases.size()];
    boolean isList = true;
    for( int i=0; i<_bases.length; i++ ) {
      _bases  [i] = bases  .get(i);
      _cnts   [i] = cnts   .get(i);
      _strides[i] = strides.get(i);
      if( _cnts[i] != 1 ) isList = false;
    }

    // Complain about unordered bases, unless it's a simple number list
    if( !isList )
      for( int i=1; i<_bases.length; i++ )
        if( _bases[i-1] >= _bases[i] )
          throw new IllegalArgumentException("Bases must be monotonically increasing");
  }

  // A simple ASTNumList of 1 number
  ASTNumList( double d ) {
    _bases  = new double[]{d};
    _strides= new double[]{1};
    _cnts   = new long  []{1};
  }
  // A simple dense range ASTNumList
  ASTNumList( long lo, long hi_exclusive ) {
    _bases  = new double[]{lo};
    _strides= new double[]{1};
    _cnts   = new long  []{hi_exclusive-lo};
  }


  // This is a special syntatic form; the number-list never executes and hits
  // the execution stack
  @Override Val exec( Env env ) { throw H2O.fail(); }

  @Override public String str() { 
    SB sb = new SB().p('[');
    for( int i=0; i<_bases.length; i++ ) {
      sb.p(_bases[i]);
      if( _cnts[i] != 1 ) {
        sb.p(':').p(_bases[i]+_cnts[i]*_strides[i]);
        if( _strides[i] != 1 || ((long)_bases[i])!=_bases[i] )
          sb.p(':').p(_strides[i]);
      }
      if( i < _bases.length-1 ) sb.p(',');
    }
    return sb.p(']').toString();
  }
  // Strange count of args, due to custom parsing
  @Override int nargs() { return -1; }

  // Expand the compressed form into an array of doubles.
  double[] expand() {
    // Count total values
    int nrows=(int)cnt(), r=0;
    // Fill in values
    double[] vals = new double[nrows];
    for( int i=0; i<_bases.length; i++ )
      for( double d = _bases[i]; d<_bases[i]+_cnts[i]*_strides[i]; d+=_strides[i] )
        vals[r++] = d;
    return vals;
  }

  int[] expand4() {
    // Count total values
    int nrows=(int)cnt(), r=0;
    // Fill in values
    int[] vals = new int[nrows];
    for( int i=0; i<_bases.length; i++ )
      for( double d = _bases[i]; d<_bases[i]+_cnts[i]*_strides[i]; d+=_strides[i] )
        vals[r++] = (int)d;
    return vals;
  }
  int[] expand4Sort() {
    int[] is = expand4();
    Arrays.sort(is);
    return is;
  }

  long[] expand8() {
    // Count total values
    int nrows=(int)cnt(), r=0;
    // Fill in values
    long[] vals = new long[nrows];
    for( int i=0; i<_bases.length; i++ )
      for( double d = _bases[i]; d<_bases[i]+_cnts[i]*_strides[i]; d+=_strides[i] )
        vals[r++] = (long)d;
    return vals;
  }
  long[] expand8Sort() {
    long[] is = expand8();
    Arrays.sort(is);
    return is;
  }

  double max() { return _bases[_bases.length-1] + _cnts[_cnts.length-1]*_strides[_strides.length-1]; } // largest exclusive value (weird rite?!)
  double min() { return _bases[0]; }
  long cnt() { return water.util.ArrayUtils.sum(_cnts); }
  boolean isDense() { return _cnts.length==1 && _bases[0]==0 && _strides[0]==1; }
  boolean isEmpty() { return _bases.length==0; }


  // check if n is in this list of numbers
  // NB: all contiguous ranges have already been checked to have stride 1
  boolean has(long v) {
    if( min() <= v && v < max() ) { // guarantees that ub is not out-of-bounds
      // binary search _bases for range to check, return true for exact match
      // if no exact base matches, check the ranges of the two "bounding" bases
      int[][] res = new int[2][]; // entry 0 is exact; entry 1 is [lb,ub]
      bsearch(v, res);
      if( res[0] != null /* exact base match */ ) return true;
      else {
        int lb = res[1][0], ub = res[1][1];
        if( _bases[lb] <= v && v < _bases[lb] + _cnts[lb] ) return true;
        if( _bases[ub] <= v && v < _bases[ub] + _cnts[ub] ) return true;
      }
    }
    return false;
  }

  private void bsearch(long v, int[][] res) {
    int lb=0,ub=_bases.length;
    int m=(ub+lb)>>1; // [lb,m) U [m,ub)
    do {
      if( v==_bases[m] ) { res[0]=new int[]{m}; return; } // exact base match
      else if( v<_bases[m] ) ub=m;
      else lb = m;
      m = (ub+lb)>>1;
    } while( m!=lb );
    res[1]=new int[]{lb,ub}; // return 2 closest bases
  }
}
