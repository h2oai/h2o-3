package hex.tree;

import sun.misc.Unsafe;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.nbhm.UtilUnsafe;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.MathUtils;

/** A Histogram, computed in parallel over a Vec.
 *
 *  <p>A {@code DHistogram} bins every value added to it, and computes a the
 *  vec min and max (for use in the next split), and response mean and variance
 *  for each bin.  {@code DHistogram}s are initialized with a min, max and
 *  number-of- elements to be added (all of which are generally available from
 *  a Vec).  Bins run from min to max in uniform sizes.  If the {@code
 *  DHistogram} can determine that fewer bins are needed (e.g. boolean columns
 *  run from 0 to 1, but only ever take on 2 values, so only 2 bins are
 *  needed), then fewer bins are used.
 *  
 *  <p>{@code DHistogram} are shared per-node, and atomically updated.  There's
 *  an {@code add} call to help cross-node reductions.  The data is stored in
 *  primitive arrays, so it can be sent over the wire.
 *  
 *  <p>If we are successively splitting rows (e.g. in a decision tree), then a
 *  fresh {@code DHistogram} for each split will dynamically re-bin the data.
 *  Each successive split will logarithmically divide the data.  At the first
 *  split, outliers will end up in their own bins - but perhaps some central
 *  bins may be very full.  At the next split(s), the full bins will get split,
 *  and again until (with a log number of splits) each bin holds roughly the
 *  same amount of data.  This dynamic binning resolves a lot of problems with
 *  picking the proper bin count or limits - generally a few more tree levels
 *  will equal any fancy but fixed-size binning strategy.
 *
 *  @author Cliff Click
*/
public abstract class DHistogram<TDH extends DHistogram> extends Iced {
  public final transient String _name; // Column name (for debugging)
  public final byte  _isInt;    // 0: float col, 1: int col, 2: enum & int col
  public final char  _nbin;     // Bin count
  public final float _step;     // Linear interpolation step per bin
  public final float _min, _maxEx; // Conservative Min/Max over whole collection.  _maxEx is Exclusive.
  public      double _bins[];   // Bins, shared, atomically incremented

  // Atomically updated float min/max
  protected    float  _min2, _maxIn; // Min/Max, shared, atomically updated.  _maxIn is Inclusive.
  private static final Unsafe _unsafe = UtilUnsafe.getUnsafe();
  static private final long _min2Offset;
  static private final long _max2Offset;
  static {
    try {
      _min2Offset = _unsafe.objectFieldOffset(DHistogram.class.getDeclaredField("_min2"));
      _max2Offset = _unsafe.objectFieldOffset(DHistogram.class.getDeclaredField("_maxIn"));
    } catch( Exception e ) {
      throw H2O.fail();
    }
  }

  public void setMin( float min ) {
    int imin = Float.floatToRawIntBits(min);
    float old = _min2;
    while( min < old && !_unsafe.compareAndSwapInt(this, _min2Offset, Float.floatToRawIntBits(old), imin ) )
      old = _min2;
  }
  // Find Inclusive _max2
  public void setMax( float max ) {
    int imax = Float.floatToRawIntBits(max);
    float old = _maxIn;
    while( max > old && !_unsafe.compareAndSwapInt(this, _max2Offset, Float.floatToRawIntBits(old), imax ) )
      old = _maxIn;
  }

  public DHistogram(String name, final int nbins, int nbins_cats, final byte isInt, final float min, final float maxEx) {
    assert nbins > 1;
    assert nbins_cats > 1;
    assert maxEx > min : "Caller ensures "+maxEx+">"+min+", since if max==min== the column "+name+" is all constants";
    _isInt = isInt;
    _name = name;
    _min=min;
    _maxEx=maxEx;               // Set Exclusive max
    _min2 =  Float.MAX_VALUE;   // Set min/max to outer bounds
    _maxIn= -Float.MAX_VALUE;
    // See if we can show there are fewer unique elements than nbins.
    // Common for e.g. boolean columns, or near leaves.
    int xbins = isInt == 2 ? nbins_cats : nbins;
    if( isInt>0 && maxEx-min <= xbins ) {
      assert ((long)min)==min;                // No overflow
      xbins = (char)((long)maxEx-(long)min);  // Shrink bins
      _step = 1.0f;                           // Fixed stepsize
    } else {
      _step = xbins/(maxEx-min);              // Step size for linear interpolation, using mul instead of div
      assert _step > 0 && !Float.isInfinite(_step);
    }
    _nbin = (char)xbins;
    // Do not allocate the big arrays here; wait for scoreCols to pick which cols will be used.
  }

  // Interpolate d to find bin#
  int bin( float col_data ) {
    if( Float.isNaN(col_data) ) return 0; // Always NAs to bin 0
    if (Float.isInfinite(col_data)) // Put infinity to most left/right bin
      if (col_data<0) return 0;
      else return _bins.length-1;
    // When the model is exposed to new test data, we could have data that is
    // out of range of any bin - however this binning call only happens during
    // model-building.
    assert _min <= col_data && col_data < _maxEx : "Coldata "+col_data+" out of range "+this;
    int idx1  = (int)((col_data-_min)*_step);
    assert 0 <= idx1 && idx1 <= _bins.length : idx1 + " " + _bins.length;
    if( idx1 == _bins.length) idx1--; // Roundoff error allows idx1 to hit upper bound, so truncate
    return idx1;
  }
  float binAt( int b ) { return _min+b/_step; }

  public int nbins() { return _nbin; }
  public double bins(int b) { return _bins[b]; }
  abstract public double mean(int b);
  abstract public double var (int b);

  // Big allocation of arrays
  abstract void init0();
  final void init() {
    assert _bins == null;
    _bins = MemoryManager.malloc8d(_nbin);
    init0();
  }

  // Add one row to a bin found via simple linear interpolation.
  // Compute bin min/max.
  // Compute response mean & variance.
  abstract void incr0( int b, double y, double w );
  final void incr( float col_data, double y, double w ) {
    assert Float.isNaN(col_data) || Float.isInfinite(col_data) || (_min <= col_data && col_data < _maxEx) : "col_data "+col_data+" out of range "+this;
    int b = bin(col_data);      // Compute bin# via linear interpolation
    water.util.AtomicUtils.DoubleArray.add(_bins,b,w); // Bump count in bin
    // Track actual lower/upper bound per-bin
    if (!Float.isInfinite(col_data)) {
      setMin(col_data);
      setMax(col_data);
    }
    if( y != 0 && w != 0) incr0(b,y,w);
  }

  // Merge two equal histograms together.  Done in a F/J reduce, so no
  // synchronization needed.
  abstract void add0( TDH dsh );
  void add( TDH dsh ) {
    assert _isInt == dsh._isInt && _nbin == dsh._nbin && _step == dsh._step &&
      _min == dsh._min && _maxEx == dsh._maxEx;
    assert (_bins == null && dsh._bins == null) || (_bins != null && dsh._bins != null);
    if( _bins == null ) return;
    ArrayUtils.add(_bins,dsh._bins);
    if( _min2  > dsh._min2  ) _min2  = dsh._min2 ;
    if( _maxIn < dsh._maxIn ) _maxIn = dsh._maxIn;
    add0(dsh);
  }

  // Inclusive min & max
  public float find_min  () { return _min2 ; }
  public float find_maxIn() { return _maxIn; }
  // Exclusive max
  public float find_maxEx() { return find_maxEx(_maxIn,_isInt); }
  static public float find_maxEx(float maxIn, int isInt ) {
    float ulp = Math.ulp(maxIn);
    if( isInt > 0 && 1 > ulp ) ulp = 1;
    float res = maxIn+ulp;
    return Float.isInfinite(res) ? maxIn : res;
  }

  // Compute a "score" for a column; lower score "wins" (is a better split).
  // Score is the sum of the MSEs when the data is split at a single point.
  // mses[1] == MSE for splitting between bins  0  and 1.
  // mses[n] == MSE for splitting between bins n-1 and n.
  abstract public DTree.Split scoreMSE( int col, double min_rows );

  // The initial histogram bins are setup from the Vec rollups.
  static public DHistogram[] initialHist(Frame fr, int ncols, int nbins, int nbins_cats, DHistogram hs[]) {
    Vec vecs[] = fr.vecs();
    for( int c=0; c<ncols; c++ ) {
      Vec v = vecs[c];
      final float minIn = (float)Math.max(v.min(),-Float.MAX_VALUE); // inclusive vector min
      final float maxIn = (float)Math.min(v.max(), Float.MAX_VALUE); // inclusive vector max
      final float maxEx = find_maxEx(maxIn,v.isInt()?1:0); // smallest exclusive max
      final long vlen = v.length();
      hs[c] = v.naCnt()==vlen || v.min()==v.max() ? null :
        make(fr._names[c],nbins, nbins_cats, (byte)(v.isEnum() ? 2 : (v.isInt()?1:0)), minIn, maxEx);
      assert (hs[c] == null || vlen > 0);
    }
    return hs;
  }

  static public DHistogram make(String name, final int nbins, int nbins_cats, byte isInt, float min, float maxEx) {
    return new DRealHistogram(name,nbins, nbins_cats, isInt, min, maxEx);
  }

  // Check for a constant response variable
  public boolean isConstantResponse() {
    double m = Double.NaN;
    for( int b=0; b<_bins.length; b++ ) {
      if( _bins[b] == 0 ) continue;
      if( var(b) > 1e-6 ) {
        Log.warn("Response should be constant, but variance of bin " + b + " (out of " + _bins.length + ") is " + var(b));
        return false;
      }
      double mean = mean(b);
      if( mean != m )
        if( Double.isNaN(m) ) m=mean; // Capture mean of first non-empty bin
        else if( !MathUtils.compare(m,mean,1e-5,1e-5) ) {
          Log.warn("Response should be constant, but mean of first non-empty bin is " + m + ", but another bin (" + b + ") has mean(b) = " + mean);
          return false;
        }
    }
    return true;
  }

  // Pretty-print a histogram
  @Override public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(_name).append(":").append(_min).append("-").append(_maxEx).append(" step="+(1/_step)+" nbins="+nbins()+" isInt="+_isInt);
    if( _bins != null ) {
      for( int b=0; b<_bins.length; b++ ) {
        sb.append(String.format("\ncnt=%d, [%f - %f], mean/var=", _bins[b],_min+b/_step,_min+(b+1)/_step));
        sb.append(String.format("%6.2f/%6.2f,", mean(b), var(b)));
      }
      sb.append('\n');
    }
    return sb.toString();
  }

  abstract public long byteSize0();
  public long byteSize() {
    long sum = 8+8;             // Self header
    sum += 1+2;                 // enum; nbin
    sum += 4+4+4+4+4;           // step,min,max,min2,max2
    sum += 8*1;                 // 1 internal arrays
    if( _bins == null ) return sum;
    // + 20(array header) + len<<2 (array body)
    sum += 24+_bins.length<<3;
    sum += byteSize0();
    return sum;
  }
}
