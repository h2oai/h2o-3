package hex.tree;

import water.H2O;
import water.MemoryManager;
import water.util.ArrayUtils;
import water.util.AtomicUtils;
import water.util.IcedBitSet;
import java.util.Comparator;
import java.util.Arrays;

/** A Histogram, computed in parallel over a Vec.
 *
 *  <p>Sums and sums-of-squares of floats
 *
 *  @author Cliff Click
 */
public class DRealHistogram extends DHistogram<DRealHistogram> {
  private float _sums[], _ssqs[]; // Sums & square-sums, shared, atomically incremented

  public DRealHistogram( String name, final int nbins, byte isInt, float min, float maxEx, long nelems ) {
    super(name,nbins,isInt,min,maxEx,nelems);
  }
  @Override boolean isBinom() { return false; }

  @Override public double mean(int b) {
    int n = _bins[b];
    return n>0 ? _sums[b]/n : 0;
  }
  @Override public double var (int b) {
    int n = _bins[b];
    if( n<=1 ) return 0;
    return (_ssqs[b] - _sums[b]*_sums[b]/n)/(n-1);
  }

  // Big allocation of arrays
  @Override void init0() {
    _sums = MemoryManager.malloc4f(_nbin);
    _ssqs = MemoryManager.malloc4f(_nbin);
  }

  // Add one row to a bin found via simple linear interpolation.
  // Compute response mean & variance.
  // Done racily instead F/J map calls, so atomic
  @Override void incr0( int b, float y ) {
    AtomicUtils.FloatArray.add(_sums,b,y);
    AtomicUtils.FloatArray.add(_ssqs,b,y*y);
  }
  // Same, except square done by caller
  void incr1( int b, float y, float yy ) {
    AtomicUtils.FloatArray.add(_sums,b,y);
    AtomicUtils.FloatArray.add(_ssqs,b,yy);
  }

  // Merge two equal histograms together.
  // Done in a F/J reduce, so no synchronization needed.
  @Override void add0( DRealHistogram dsh ) {
    ArrayUtils.add(_sums,dsh._sums);
    ArrayUtils.add(_ssqs,dsh._ssqs);
  }

  // Compute a "score" for a column; lower score "wins" (is a better split).
  // Score is the sum of the MSEs when the data is split at a single point.
  // mses[1] == MSE for splitting between bins  0  and 1.
  // mses[n] == MSE for splitting between bins n-1 and n.
  @Override public DTree.Split scoreMSE( int col ) {
    final int nbins = nbins();
    assert nbins > 1;

    // Compute mean/var for cumulative bins from 0 to nbins inclusive.
    double sums0[] = MemoryManager.malloc8d(nbins+1);
    double ssqs0[] = MemoryManager.malloc8d(nbins+1);
    long     ns0[] = MemoryManager.malloc8 (nbins+1);
    for( int b=1; b<=nbins; b++ ) {
      double m0 = sums0[b-1],  m1 = _sums[b-1];
      double s0 = ssqs0[b-1],  s1 = _ssqs[b-1];
      long   k0 = ns0  [b-1],  k1 = _bins[b-1];
      if( k0==0 && k1==0 ) continue;
      sums0[b] = m0+m1;
      ssqs0[b] = s0+s1;
      ns0  [b] = k0+k1;
    }
    long tot = ns0[nbins];
    // If we see zero variance, we must have a constant response in this
    // column.  Normally this situation is cut out before we even try to split,
    // but we might have NA's in THIS column...
    if( ssqs0[nbins]*tot - sums0[nbins]*sums0[nbins] == 0 ) { assert isConstantResponse(); return null; }

    // Compute mean/var for cumulative bins from nbins to 0 inclusive.
    double sums1[] = MemoryManager.malloc8d(nbins+1);
    double ssqs1[] = MemoryManager.malloc8d(nbins+1);
    long     ns1[] = MemoryManager.malloc8 (nbins+1);
    for( int b=nbins-1; b>=0; b-- ) {
      double m0 = sums1[b+1],  m1 = _sums[b];
      double s0 = ssqs1[b+1],  s1 = _ssqs[b];
      long   k0 = ns1  [b+1],  k1 = _bins[b];
      if( k0==0 && k1==0 ) continue;
      sums1[b] = m0+m1;
      ssqs1[b] = s0+s1;
      ns1  [b] = k0+k1;
      assert ns0[b]+ns1[b]==tot;
    }

    // Now roll the split-point across the bins.  There are 2 ways to do this:
    // split left/right based on being less than some value, or being equal/
    // not-equal to some value.  Equal/not-equal makes sense for categoricals
    // but both splits could work for any integral datatype.  Do the less-than
    // splits first.
    int best=0;                         // The no-split
    double best_se0=Double.MAX_VALUE;   // Best squared error
    double best_se1=Double.MAX_VALUE;   // Best squared error
    byte equal=0;                // Ranged check
    for( int b=1; b<=nbins-1; b++ ) {
      if( _bins[b] == 0 ) continue; // Ignore empty splits
      // We're making an unbiased estimator, so that MSE==Var.
      // Then Squared Error = MSE*N = Var*N
      //                    = (ssqs/N - mean^2)*N
      //                    = ssqs - N*mean^2
      //                    = ssqs - N*(sum/N)(sum/N)
      //                    = ssqs - sum^2/N
      double se0 = ssqs0[b] - sums0[b]*sums0[b]/ns0[b];
      double se1 = ssqs1[b] - sums1[b]*sums1[b]/ns1[b];
      if( (se0+se1 < best_se0+best_se1) || // Strictly less error?
          // Or tied MSE, then pick split towards middle bins
          (se0+se1 == best_se0+best_se1 &&
           Math.abs(b -(nbins>>1)) < Math.abs(best-(nbins>>1))) ) {
        best_se0 = se0;   best_se1 = se1;
        best = b;
      }
    }

    // If the min==max, we can also try an equality-based split
    if( _isInt > 0 && _step == 1.0f &&    // For any integral (not float) column
        _maxEx-_min > 2 ) { // Also need more than 2 (boolean) choices to actually try a new split pattern
      for( int b=1; b<=nbins-1; b++ ) {
        if( _bins[b] == 0 ) continue; // Ignore empty splits
        long N =        ns0[b+0] + ns1[b+1];
        if( N == 0 ) continue;
        double sums = sums0[b+0]+sums1[b+1];
        double ssqs = ssqs0[b+0]+ssqs1[b+1];
        double si =  ssqs    -  sums   * sums   /   N    ; // Left+right, excluding 'b'
        double sx = _ssqs[b] - _sums[b]*_sums[b]/_bins[b]; // Just 'b'
        if( si+sx < best_se0+best_se1 ) { // Strictly less error?
          best_se0 = si;   best_se1 = sx;
          best = b;        equal = 1; // Equality check
        }
      }
    }

    // For categorical (unordered) predictors, sort the bins by average
    // prediction then look for an optimal split.
    IcedBitSet bs = null;
    if( _isInt == 2 && _step == 1.0f && nbins >= 4 ) {
      for( int i=0; i<nbins; i++ )
        System.out.println("bin["+i+"] avg="+_sums[i]+"/"+_bins[i]+" = "+(_sums[i]/_bins[i]));

      int idxs[] = MemoryManager.malloc4(nbins);
      for( int i=0; i<nbins; i++ ) idxs[i] = i;
      final double[] avgs = sums0;    // Reuse sums0
      for( int i=0; i<nbins; i++ ) avgs[i] = _sums[i]/_bins[i];
      Arrays.sort(idxs, new Comparator() { 
          @Override public int compare( int x, int y ) { return avgs[x] < avgs[y] ? -1 : (avgs[x] > avgs[y] ? 1 : 0); }
        });
      for( int i=0; i<nbins; i++ )
        System.out.println("bin["+idxs[i]+"] avg= "+(_sums[idxs[i]]/_bins[idxs[i]]));

      throw H2O.unimpl();
    }

    if( best==0 ) return null;  // No place to split
    assert best > 0 : "Must actually pick a split "+best;
    long   n0 = equal == 0 ?   ns0[best] :   ns0[best]+  ns1[best+1];
    long   n1 = equal == 0 ?   ns1[best] : _bins[best]              ;
    double p0 = equal == 0 ? sums0[best] : sums0[best]+sums1[best+1];
    double p1 = equal == 0 ? sums1[best] : _sums[best]              ;
    return new DTree.Split(col,best,bs,equal,best_se0,best_se1,n0,n1,p0/n0,p1/n1);
  }

  @Override public long byteSize0() {
    return 8*2 +                // 2 more internal arrays
      24+_sums.length<<3 +
      24+_ssqs.length<<3 ;
  }
}
