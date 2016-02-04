package hex.tree;

import sun.misc.Unsafe;
import water.H2O;
import water.Iced;
import water.MemoryManager;
import water.fvec.Frame;
import water.fvec.Vec;
import water.nbhm.UtilUnsafe;
import water.util.*;

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
public final class DHistogram extends Iced {
  public final transient String _name; // Column name (for debugging)
  public final byte  _isInt;    // 0: float col, 1: int col, 2: categorical & int col
  public final char  _nbin;     // Bin count
  public final double _step;     // Linear interpolation step per bin
  public final double _min, _maxEx; // Conservative Min/Max over whole collection.  _maxEx is Exclusive.
  public double _bins[];   // Bins, shared, atomically incremented
  private double _sums[], _ssqs[]; // Sums & square-sums, shared, atomically incremented

  // Atomically updated double min/max
  protected    double  _min2, _maxIn; // Min/Max, shared, atomically updated.  _maxIn is Inclusive.
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

  public static int[] activeColumns(DHistogram[] hist) {
    int[] cols = new int[hist.length];
    int len=0;
    for( int i=0; i<hist.length; i++ ) {
      if (hist[i]==null) continue;
      assert hist[i]._min < hist[i]._maxEx && hist[i].nbins() > 1 : "broken histo range "+ hist[i];
      cols[len++] = i;        // Gather active column
    }
//    cols = Arrays.copyOfRange(cols, len, hist.length);
    return cols;
  }

  public void setMin( double min ) {
    long imin = Double.doubleToRawLongBits(min);
    double old = _min2;
    while( min < old && !_unsafe.compareAndSwapLong(this, _min2Offset, Double.doubleToRawLongBits(old), imin ) )
      old = _min2;
  }
  // Find Inclusive _max2
  public void setMax( double max ) {
    long imax = Double.doubleToRawLongBits(max);
    double old = _maxIn;
    while( max > old && !_unsafe.compareAndSwapLong(this, _max2Offset, Double.doubleToRawLongBits(old), imax ) )
      old = _maxIn;
  }

  public DHistogram(String name, final int nbins, int nbins_cats, byte isInt, double min, double maxEx) {
    assert nbins > 1;
    assert nbins_cats > 1;
    assert maxEx > min : "Caller ensures "+maxEx+">"+min+", since if max==min== the column "+name+" is all constants";
    _isInt = isInt;
    _name = name;
    _min=min;
    _maxEx=maxEx;               // Set Exclusive max
    _min2 =  Double.MAX_VALUE;   // Set min/max to outer bounds
    _maxIn= -Double.MAX_VALUE;
    // See if we can show there are fewer unique elements than nbins.
    // Common for e.g. boolean columns, or near leaves.
    int xbins = isInt == 2 ? nbins_cats : nbins;
    if( isInt>0 && maxEx-min <= xbins ) {
      assert ((long)min)==min;                // No overflow
      xbins = (char)((long)maxEx-(long)min);  // Shrink bins
      _step = 1.0f;                           // Fixed stepsize
    } else {
      _step = xbins/(maxEx-min);              // Step size for linear interpolation, using mul instead of div
      assert _step > 0 && !Double.isInfinite(_step);
    }
    _nbin = (char)xbins;
    // Do not allocate the big arrays here; wait for scoreCols to pick which cols will be used.
  }

  // Interpolate d to find bin#
  public int bin( double col_data ) {
    if( Double.isNaN(col_data) ) return 0; // Always NAs to bin 0
    if (Double.isInfinite(col_data)) // Put infinity to most left/right bin
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
  public double binAt( int b ) { return _min+b/_step; }

  public int nbins() { return _nbin; }
  public double bins(int b) { return _bins[b]; }

  // Big allocation of arrays
  public void init() {
    assert _bins == null;
    _bins = MemoryManager.malloc8d(_nbin);
    init0();
  }

  // Add one row to a bin found via simple linear interpolation.
  // Compute bin min/max.
  // Compute response mean & variance.
  void incr( double col_data, double y, double w ) {
    assert Double.isNaN(col_data) || Double.isInfinite(col_data) || (_min <= col_data && col_data < _maxEx) : "col_data "+col_data+" out of range "+this;
    int b = bin(col_data);      // Compute bin# via linear interpolation
    water.util.AtomicUtils.DoubleArray.add(_bins,b,w); // Bump count in bin
    // Track actual lower/upper bound per-bin
    if (!Double.isInfinite(col_data)) {
      setMin(col_data);
      setMax(col_data);
    }
    if( y != 0 && w != 0) incr0(b,y,w);
  }

  // Merge two equal histograms together.  Done in a F/J reduce, so no
  // synchronization needed.
  public void add( DHistogram dsh ) {
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
  public double find_min  () { return _min2 ; }
  public double find_maxIn() { return _maxIn; }
  // Exclusive max
  public double find_maxEx() { return find_maxEx(_maxIn,_isInt); }
  public static double find_maxEx(double maxIn, int isInt ) {
    double ulp = Math.ulp(maxIn);
    if( isInt > 0 && 1 > ulp ) ulp = 1;
    double res = maxIn+ulp;
    return Double.isInfinite(res) ? maxIn : res;
  }

  // The initial histogram bins are setup from the Vec rollups.
  public static DHistogram[] initialHist(Frame fr, int ncols, int nbins, int nbins_cats, DHistogram hs[]) {
    Vec vecs[] = fr.vecs();
    for( int c=0; c<ncols; c++ ) {
      Vec v = vecs[c];
      final double minIn = Math.max(v.min(),-Double.MAX_VALUE); // inclusive vector min
      final double maxIn = Math.min(v.max(), Double.MAX_VALUE); // inclusive vector max
      final double maxEx = find_maxEx(maxIn,v.isInt()?1:0); // smallest exclusive max
      final long vlen = v.length();
      hs[c] = v.naCnt()==vlen || v.min()==v.max() ? null :
        make(fr._names[c],nbins, nbins_cats, (byte)(v.isCategorical() ? 2 : (v.isInt()?1:0)), minIn, maxEx);
      assert (hs[c] == null || vlen > 0);
    }
    return hs;
  }

  public static DHistogram make(String name, final int nbins, int nbins_cats, byte isInt, double min, double maxEx) {
    return new DHistogram(name,nbins, nbins_cats, isInt, min, maxEx);
  }

  // Check for a constant response variable
  private boolean isConstantResponse() {
    double m = Double.NaN;
    for( int b=0; b<_bins.length; b++ ) {
      if( _bins[b] == 0 ) continue;
      if( var(b) > 1e-6) {
        Log.warn("Response should be constant, but variance of bin " + b + " (out of " + _bins.length + ") is " + var(b));
        return false;
      }
      double mean = mean(b);
      if( mean != m )
        if( Double.isNaN(m) ) m=mean; // Capture mean of first non-empty bin
        else if( !MathUtils.compare(m,mean,1e-3/*abs*/,1e-3/*rel*/) ) {
          Log.warn("Response should be constant, but mean of first non-empty bin is " + m + ", but another bin (" + b + ") has mean(b) = " + mean);
          return false;
        }
    }
    return true;
  }

  // Pretty-print a histogram
  @Override public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(_name).append(":").append(_min).append("-").append(_maxEx).append(" step=" + (1 / _step) + " nbins=" + nbins() + " isInt=" + _isInt);
    if( _bins != null ) {
      for( int b=0; b<_bins.length; b++ ) {
        sb.append(String.format("\ncnt=%f, [%f - %f], mean/var=", _bins[b],_min+b/_step,_min+(b+1)/_step));
        sb.append(String.format("%6.2f/%6.2f,", mean(b), var(b)));
      }
      sb.append('\n');
    }
    return sb.toString();
  }
  double mean(int b) {
    double n = _bins[b];
    return n>0 ? _sums[b]/n : 0;
  }

  /**
   * compute the sample variance within a given bin
   * @param b bin id
   * @return sample variance (>= 0)
   */
  public double var (int b) {
    double n = _bins[b];
    if( n<=1 ) return 0;
    return Math.max(0, (_ssqs[b] - _sums[b]*_sums[b]/n)/(n-1)); //not strictly consistent with what is done elsewhere (use n instead of n-1 to get there)
  }
  // Big allocation of arrays
  void init0() {
    _sums = MemoryManager.malloc8d(_nbin);
    _ssqs = MemoryManager.malloc8d(_nbin);
  }

  // Add one row to a bin found via simple linear interpolation.
  // Compute response mean & variance.
  // Done racily instead F/J map calls, so atomic
  public void incr0( int b, double y, double w ) {
    AtomicUtils.DoubleArray.add(_sums,b,w*y); //See 'HistogramTest' JUnit for float-casting rationalization (not done right now)
    AtomicUtils.DoubleArray.add(_ssqs,b,w*y*y);
  }
  // Same, except square done by caller
  public void incr1( int b, double y, double yy) {
    AtomicUtils.DoubleArray.add(_sums,b,y); //See 'HistogramTest' JUnit for float-casting rationalization (not done right now)
    AtomicUtils.DoubleArray.add(_ssqs,b,yy);
  }

  // Merge two equal histograms together.
  // Done in a F/J reduce, so no synchronization needed.
  public void add0( DHistogram dsh ) {
    ArrayUtils.add(_sums,dsh._sums);
    ArrayUtils.add(_ssqs,dsh._ssqs);
  }

  // Compute a "score" for a column; lower score "wins" (is a better split).
  // Score is the sum of the MSEs when the data is split at a single point.
  // mses[1] == MSE for splitting between bins  0  and 1.
  // mses[n] == MSE for splitting between bins n-1 and n.
  public DTree.Split scoreMSE( int col, double min_rows, int nid ) {
    final int nbins = nbins();
    assert nbins > 1;

    // Histogram arrays used for splitting, these are either the original bins
    // (for an ordered predictor), or sorted by the mean response (for an
    // unordered predictor, i.e. categorical predictor).
    double[] sums = _sums;
    double[] ssqs = _ssqs;
    double[] bins = _bins;
    int idxs[] = null;          // and a reverse index mapping

    // For categorical (unordered) predictors, sort the bins by average
    // prediction then look for an optimal split.  Currently limited to categoricals
    // where we're one-per-bin.  No point for 3 or fewer bins as all possible
    // combinations (just 3) are tested without needing to sort.
    if( _isInt == 2 && _step == 1.0f && nbins >= 4 ) {
      // Sort the index by average response
      idxs = MemoryManager.malloc4(nbins+1); // Reverse index
      for( int i=0; i<nbins+1; i++ ) idxs[i] = i;
      final double[] avgs = MemoryManager.malloc8d(nbins+1);
      for( int i=0; i<nbins; i++ ) avgs[i] = _bins[i]==0 ? 0 : _sums[i]/_bins[i]; // Average response
      avgs[nbins] = Double.MAX_VALUE;
      ArrayUtils.sort(idxs, avgs);
      // Fill with sorted data.  Makes a copy, so the original data remains in
      // its original order.
      sums = MemoryManager.malloc8d(nbins);
      ssqs = MemoryManager.malloc8d(nbins);
      bins = MemoryManager.malloc8d(nbins);
      for( int i=0; i<nbins; i++ ) {
        sums[i] = _sums[idxs[i]];
        ssqs[i] = _ssqs[idxs[i]];
        bins[i] = _bins[idxs[i]];
      }
    }

    // Compute mean/var for cumulative bins from 0 to nbins inclusive.
    double sums0[] = MemoryManager.malloc8d(nbins+1);
    double ssqs0[] = MemoryManager.malloc8d(nbins+1);
    double   ns0[] = MemoryManager.malloc8d(nbins+1);
    for( int b=1; b<=nbins; b++ ) {
      double m0 = sums0[b-1],  m1 = sums[b-1];
      double s0 = ssqs0[b-1],  s1 = ssqs[b-1];
      double k0 = ns0  [b-1],  k1 = bins[b-1];
      if( k0==0 && k1==0 )
        continue;
      sums0[b] = m0+m1;
      ssqs0[b] = s0+s1;
      ns0  [b] = k0+k1;
    }
    double tot = ns0[nbins];
    // Is any split possible with at least min_obs?
    if( tot < 2*min_rows )
      return null;
    // If we see zero variance, we must have a constant response in this
    // column.  Normally this situation is cut out before we even try to split,
    // but we might have NA's in THIS column...
    double var = ssqs0[nbins]*tot - sums0[nbins]*sums0[nbins];
    if( var == 0 ) {
//      assert isConstantResponse();
      return null;
    }
    // If variance is really small, then the predictions (which are all at
    // single-precision resolution), will be all the same and the tree split
    // will be in vain.
    if( ((float)var) == 0f )
      return null;

    // Compute mean/var for cumulative bins from nbins to 0 inclusive.
    double sums1[] = MemoryManager.malloc8d(nbins+1);
    double ssqs1[] = MemoryManager.malloc8d(nbins+1);
    double   ns1[] = MemoryManager.malloc8d(nbins+1);
    for( int b=nbins-1; b>=0; b-- ) {
      double m0 = sums1[b+1], m1 = sums[b];
      double s0 = ssqs1[b+1], s1 = ssqs[b];
      double k0 = ns1  [b+1], k1 = bins[b];
      if( k0==0 && k1==0 )
        continue;
      sums1[b] = m0+m1;
      ssqs1[b] = s0+s1;
      ns1  [b] = k0+k1;
      assert MathUtils.compare(ns0[b]+ns1[b],tot,1e-5,1e-5);
    }

    // Now roll the split-point across the bins.  There are 2 ways to do this:
    // split left/right based on being less than some value, or being equal/
    // not-equal to some value.  Equal/not-equal makes sense for categoricals
    // but both splits could work for any integral datatype.  Do the less-than
    // splits first.
    int best=0;                         // The no-split
    double best_se0=Double.MAX_VALUE;   // Best squared error
    double best_se1=Double.MAX_VALUE;   // Best squared error
    byte equal=0;                       // Ranged check
    for( int b=1; b<=nbins-1; b++ ) {
      if( bins[b] == 0 ) continue; // Ignore empty splits
      if( ns0[b] < min_rows ) continue;
      if( ns1[b] < min_rows ) break; // ns1 shrinks at the higher bin#s, so if it fails once it fails always
      // We're making an unbiased estimator, so that MSE==Var.
      // Then Squared Error = MSE*N = Var*N
      //                    = (ssqs/N - mean^2)*N
      //                    = ssqs - N*mean^2
      //                    = ssqs - N*(sum/N)(sum/N)
      //                    = ssqs - sum^2/N
      double se0 = ssqs0[b] - sums0[b]*sums0[b]/ns0[b];
      double se1 = ssqs1[b] - sums1[b]*sums1[b]/ns1[b];
      if( se0 < 0 ) se0 = 0;    // Roundoff error; sometimes goes negative
      if( se1 < 0 ) se1 = 0;    // Roundoff error; sometimes goes negative
      if( (se0+se1 < best_se0+best_se1) || // Strictly less error?
              // Or tied MSE, then pick split towards middle bins
              (se0+se1 == best_se0+best_se1 &&
                      Math.abs(b -(nbins>>1)) < Math.abs(best-(nbins>>1))) ) {
        best_se0 = se0;   best_se1 = se1;
        best = b;
      }
    }

    // If the bin covers a single value, we can also try an equality-based split
    if( _isInt > 0 && _step == 1.0f &&    // For any integral (not float) column
            _maxEx-_min > 2 && idxs==null ) { // Also need more than 2 (boolean) choices to actually try a new split pattern
      for( int b=1; b<=nbins-1; b++ ) {
        if( bins[b] < min_rows ) continue; // Ignore too small splits
        double N = ns0[b] + ns1[b+1];
        if( N < min_rows )
          continue; // Ignore too small splits
        double sums2 = sums0[b  ]+sums1[b+1];
        double ssqs2 = ssqs0[b  ]+ssqs1[b+1];
        double si =    ssqs2     -sums2  *sums2  /   N   ; // Left+right, excluding 'b'
        double sx =    ssqs [b]  -sums[b]*sums[b]/bins[b]; // Just 'b'
        if( si < 0 ) si = 0;    // Roundoff error; sometimes goes negative
        if( sx < 0 ) sx = 0;    // Roundoff error; sometimes goes negative
        if( si+sx < best_se0+best_se1 ) { // Strictly less error?
          best_se0 = si;   best_se1 = sx;
          best = b;        equal = 1; // Equality check
        }
      }
    }

    // For categorical (unordered) predictors, we sorted the bins by average
    // prediction then found the optimal split on sorted bins
    IcedBitSet bs = null;       // In case we need an arbitrary bitset
    if( idxs != null ) {        // We sorted bins; need to build a bitset
      int min=Integer.MAX_VALUE;// Compute lower bound and span for bitset
      int max=Integer.MIN_VALUE;
      for( int i=best; i<nbins; i++ ) {
        min=Math.min(min,idxs[i]);
        max=Math.max(max,idxs[i]);
      }
      bs = new IcedBitSet(max-min+1,min); // Bitset with just enough span to cover the interesting bits
      for( int i=best; i<nbins; i++ ) bs.set(idxs[i]); // Reverse the index then set bits
      equal = (byte)(bs.max() <= 32 ? 2 : 3); // Flag for bitset split; also check max size
    }

    if( best==0 ) return null;  // No place to split
    double se = ssqs1[0] - sums1[0]*sums1[0]/ns1[0]; // Squared Error with no split
    if( se <= best_se0+best_se1) return null; // Ultimately roundoff error loses, and no split actually helped
    double n0 = equal != 1 ?   ns0[best] :   ns0[best]+  ns1[best+1];
    double n1 = equal != 1 ?   ns1[best] :  bins[best]              ;
    double p0 = equal != 1 ? sums0[best] : sums0[best]+sums1[best+1];
    double p1 = equal != 1 ? sums1[best] :  sums[best]              ;
    if( MathUtils.equalsWithinOneSmallUlp((float)(p0/n0),(float)(p1/n1)) ) return null; // No difference in predictions, which are all at 1 float ULP
    return new DTree.Split(col,best,bs,equal,se,best_se0,best_se1,n0,n1,p0/n0,p1/n1);
  }

}
