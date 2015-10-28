package water.rapids;

import water.util.ArrayUtils;
import water.util.SB;

import java.util.ArrayList;
import java.util.Arrays;

/** A collection of base/stride/cnts.  Bases are monotonically increasing, and
 *  base+stride*cnt is always less than the next base.  This is a syntatic form
 *  only, and never executes and never gets on the execution stack.
 */
public class ASTNumList extends ASTParameter {
  final double _bases[], _strides[];
  final long _cnts[];
  final boolean _isList;
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
    _isList = isList;

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
    _isList = true;
  }
  // A simple dense range ASTNumList
  ASTNumList( long lo, long hi_exclusive ) {
    _bases  = new double[]{lo};
    _strides= new double[]{1};
    _cnts   = new long  []{hi_exclusive-lo};
    _isList = false;
  }

  // An empty number list
  ASTNumList( ) {
    _bases  = new double[0];
    _strides= new double[0];
    _cnts   = new long  [0];
    _isList = true;
  }

  ASTNumList(double[] list) {
    _bases  = list;
    _strides= new double[list.length];
    _cnts   = new long[list.length];
    _isList = true;
    Arrays.fill(_strides,1);
    Arrays.fill(_cnts,1);
  }

  ASTNumList(int[] list) {
    this(ArrayUtils.copyFromIntArray(list));
  }

  // This is a special syntatic form; the number-list never executes and hits
  // the execution stack
  @Override
  public Val exec(Env env) { throw new IllegalArgumentException("Number list not allowed here"); }

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
  @Override public String toJavaString() {
    double[] ary = expand();
    if( ary==null || ary.length==0 ) return "\"null\"";
    SB sb = new SB().p('{');
    for(int i=0;i<ary.length;++i) {
      sb.p(ary[i]);
      if( i==ary.length-1) return sb.p('}').toString();
      sb.p(',');
    }
    throw new RuntimeException("Should never be here");
  }

  // Expand the compressed form into an array of doubles.
  public double[] expand() {
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
    boolean isEx = false;
    double[] bases=Arrays.copyOf(_bases,_bases.length); // if bases!=null then we do exclusion has -> !has
    double min = min();
    double max = max();
    // do something special for negative indexing
    if( min < 0 ) {
      for(int i=0; i<bases.length;++i) bases[i] = -1*bases[i] - 1; // make bases positive for bsearch; also do 1-based => 0-based conversion
      min = bases[0];
      max = bases[bases.length-1] + _cnts[_cnts.length-1]*_strides[_strides.length-1];
      isEx = true;
    }

    if( min <= v && v < max ) { // guarantees that ub is not out-of-bounds
      // binary search _bases for range to check, return true for exact match
      // if no exact base matches, check the ranges of the two "bounding" bases
      int[][] res = new int[2][]; // entry 0 is exact; entry 1 is [lb,ub]
      bsearch(bases, v, res);
      if( res[0] != null /* exact base match */ ) return !isEx;
      else {
        int lb = res[1][0], ub = res[1][1];
        if( bases[lb] <= v && v < bases[lb] + _cnts[lb] ) return !isEx;
        if( bases[ub] <= v && v < bases[ub] + _cnts[ub] ) return !isEx;
      }
    }
    return isEx;
  }

  private static void bsearch(double[] bases, long v, int[][] res) {
    int lb=0,ub=bases.length;
    int m=(ub+lb)>>1; // [lb,m) U [m,ub)
    do {
      if( v==bases[m] ) { res[0]=new int[]{m}; return; } // exact base match
      else if( v<bases[m] ) ub=m;
      else lb = m;
      m = (ub+lb)>>1;
    } while( m!=lb );
    res[1]=new int[]{lb,ub}; // return 2 closest bases
  }

  // Select columns by number.  Numbers are capped to the number of columns +1
  // - this allows R      to see a single out-of-range value and throw a range check
  // - this allows Python to see a single out-of-range value and ignore it
  // - this allows Python to pass [0:MAXINT] without blowing out the max number of columns.
  // Note that the Python front-end does not want to cap the max column size, because
  // this will force eager evaluation on a standard column slice operation.
  @Override int[] columns( String[] names ) { 
    // Count total values, capped by max len+1
    int nrows=0, r=0;
    for( int i=0; i<_bases.length; i++ )
      nrows += Math.min(_bases[i]+_cnts[i],names.length+1) - Math.min(_bases[i],names.length+1);
    // Fill in values
    int[] vals = new int[nrows];
    for( int i=0; i<_bases.length; i++ ) {
      int lim = Math.min((int)(_bases[i]+_cnts[i]),names.length+1);
      for( int d = Math.min((int)_bases[i],names.length+1); d<lim; d++ )
        vals[r++] = d;
    }
    return vals;
  }
}
