package water.rapids;

import water.H2O;
import water.util.ArrayUtils;
import water.util.SB;

import java.util.ArrayList;
import java.util.Arrays;

/** A collection of base/stride/cnts.  
 *  Syntax: { {num | num:cnt | num:cnt:stride},* }
 *
 *  The bases can be unordered with dups (often used for column selection where
 *  repeated columns are allowed, and order matters).  The _isList flag tracks
 *  that all cnts are 1 (and hence all strides are ignored and 1); these lists
 *  may or may not be sorted.  Note that some column selection is dense
 *  (typical all-columns is: {0:MAX_INT}), and this has cnt>1.
 *
 *  When cnts are > 1, bases must be sorted, with base+stride*cnt always less
 *  than the next base.  Typical use-case might be a list of probabilities for
 *  computing quantiles, or grid-search parameters.
 * 
 *  Asking for a sorted integer expansion will sort the bases internally, and
 *  also demand no overlap between bases.  The has(), min() and max() calls
 *  require a sorted list.
 */
public class ASTNumList extends ASTParameter {
  final double _bases[], _strides[];
  final long _cnts[];
  final boolean _isList; // True if an unordered list of numbers (cnts are 1, stride is ignored)
        boolean _isSort; // True if bases are sorted.  May get updated later.
  
  ASTNumList( Rapids e ) {
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
      if( cnt==1 && stride != 1 ) throw new IllegalArgumentException("If count is 1, then stride must be one (and ignored)");
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
    boolean isSort = true;
    for( int i=1; i<_bases.length; i++ )
      if( _bases[i-1] >= _bases[i] )
        if( _isList ) isSort = false;
        else throw new IllegalArgumentException("Bases must be monotonically increasing");
    _isSort = isSort;
  }

  // A simple ASTNumList of 1 number
  ASTNumList( double d ) {
    _bases  = new double[]{d};
    _strides= new double[]{1};
    _cnts   = new long  []{1};
    _isList = _isSort = true;
  }
  // A simple dense range ASTNumList
  ASTNumList( long lo, long hi_exclusive ) {
    _bases  = new double[]{lo};
    _strides= new double[]{1};
    _cnts   = new long  []{hi_exclusive-lo};
    _isList = false;
    _isSort = true;
  }

  // An empty number list
  ASTNumList( ) {
    _bases  = new double[0];
    _strides= new double[0];
    _cnts   = new long  [0];
    _isList = _isSort = true;
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
    for(int i=0;i<ary.length-1;++i) sb.p(ary[i]).p(',');
    return sb.p('}').toString();
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

  // Update-in-place sort of bases
  ASTNumList sort() {
    if( _isSort ) return this;  // Flow coding fast-path cutout
    int[] idxs = ArrayUtils.seq(0,_bases.length);
    ArrayUtils.sort(idxs,_bases);
    double[] bases  = _bases  .clone();
    double[] strides= _strides.clone();
    long  [] cnts   = _cnts   .clone();
    for( int i=0; i<idxs.length; i++ ) {
      _bases  [i] = bases  [idxs[i]];
      _strides[i] = strides[idxs[i]];
      _cnts   [i] = cnts   [idxs[i]];
    }
    _isSort = true;
    return this;
  }

  // Expand the compressed form into an array of ints; 
  // often used for unordered column lists
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
  // Expand the compressed form into an array of ints; 
  // often used for sorted column lists
  int[] expand4Sort() { return sort().expand4(); }

  // Expand the compressed form into an array of longs; 
  // often used for unordered row lists
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
  // Expand the compressed form into an array of longs; 
  // often used for sorted row lists
  long[] expand8Sort() { return sort().expand8(); }

  double max() { assert _isSort; return _bases[_bases.length-1] + _cnts[_cnts.length-1]*_strides[_strides.length-1]; } // largest exclusive value (weird rite?!)
  double min() { assert _isSort; return _bases[0]; }
  long cnt() { return water.util.ArrayUtils.sum(_cnts); }
  boolean isDense() { return _cnts.length==1 && _bases[0]==0 && _strides[0]==1; }
  boolean isEmpty() { return _bases.length==0; }

  // check if n is in this list of numbers
  // NB: all contiguous ranges have already been checked to have stride 1
  boolean has(long v) {
    assert _isSort; // Only called when already sorted
    // do something special for negative indexing... that does not involve
    // allocating arrays, once per list element!
    if( v < 0 )  throw H2O.unimpl();
    int idx = Arrays.binarySearch(_bases, v);
    if( idx >= 0 ) return true;
    idx = -idx-2;  // See Arrays.binarySearch; returns (-idx-1), we want +idx-1  ... if idx == -1 => then this transformation has no effect
    if( idx < 0 ) return false;
    assert _bases[idx] < v;     // Sanity check binary search, AND idx >= 0
    return v < _bases[idx]+_cnts[idx]*_strides[idx];
  }

  // Select columns by number.  Numbers are capped to the number of columns +1
  // - this allows R      to see a single out-of-range value and throw a range check
  // - this allows Python to see a single out-of-range value and ignore it
  // - this allows Python to pass [0:MAXINT] without blowing out the max number of columns.
  // Note that the Python front-end does not want to cap the max column size, because
  // this will force eager evaluation on a standard column slice operation.
  // Note that the list is often unsorted (_isSort is false).
  // Note that the list is often dense with cnts>1 (_isList is false).
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
