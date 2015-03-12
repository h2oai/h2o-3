package hex.tree;

import water.MemoryManager;
import water.util.ArrayUtils;
import water.util.IcedBitSet;

/**
   A Histogram, computed in parallel over a Vec.
   <p>
   Sums (and sums-of-squares) of binomials - 0 or 1.  Sums-of-squares==sums in this case.
   <p>
   @author Cliff Click
*/
public class DBinomHistogram extends DHistogram<DBinomHistogram> {
  public int _sums[]; // Sums (& square-sums since only 0 & 1 allowed), shared, atomically incremented

  public DBinomHistogram( String name, final int nbins, byte isInt, float min, float maxEx, long nelems ) {
    super(name,nbins,isInt,min,maxEx,nelems);
  }
  @Override boolean isBinom() { return true; }

  @Override public double mean(int b) {
    int n = _bins[b];
    return n>0 ? (double)_sums[b]/n : 0;
  }
  @Override public double var (int b) {
    int n = _bins[b];
    if( n<=1 ) return 0;
    return (_sums[b] - (double)_sums[b]*_sums[b]/n)/(n-1);
  }

  // Big allocation of arrays
  @Override void init0() {
    _sums = MemoryManager.malloc4(_nbin);
  }

  // Add one row to a bin found via simple linear interpolation.
  // Compute bin min/max.
  // Compute response mean & variance.
  @Override void incr0( int b, float y ) {
    water.util.AtomicUtils.IntArray.incr(_sums,b);
  }

  // Merge two equal histograms together.  Done in a F/J reduce, so no
  // synchronization needed.
  @Override void add0( DBinomHistogram dsh ) {
    water.util.ArrayUtils.add(_sums,dsh._sums);
  }

  // Compute a "score" for a column; lower score "wins" (is a better split).
  // Score is the sum of the MSEs when the data is split at a single point.
  // mses[1] == MSE for splitting between bins  0  and 1.
  // mses[n] == MSE for splitting between bins n-1 and n.
  @Override public DTree.Split scoreMSE( int col, int min_rows ) {
    final int nbins = nbins();
    assert nbins > 1;

    // Histogram arrays used for splitting, these are either the original bins
    // (for an ordered predictor), or sorted by the mean response (for an
    // unordered predictor, i.e. categorical predictor).
    int[] sums = _sums;
    int  [] bins = _bins;
    int idxs[] = null;          // and a reverse index mapping

    // For categorical (unordered) predictors, sort the bins by average
    // prediction then look for an optimal split.  Currently limited to enums
    // where we're one-per-bin.  No point for 3 or fewer bins as all possible
    // combinations (just 3) are tested without needing to sort.
    if( _isInt == 2 && _step == 1.0f && nbins >= 4 ) {
      // Sort the index by average response
      idxs = MemoryManager.malloc4(nbins+1); // Reverse index
      for( int i=0; i<nbins+1; i++ ) idxs[i] = i;
      final double[] avgs = MemoryManager.malloc8d(nbins+1);
      for( int i=0; i<nbins; i++ ) avgs[i] = _bins[i]==0 ? 0 : _sums[i]/_bins[i]; // Average response
      avgs[nbins] = Double.MAX_VALUE;
      ArrayUtils.sort(idxs, new ArrayUtils.IntComparator() {
        @Override
        public int compare(int x, int y) {
          return avgs[x] < avgs[y] ? -1 : (avgs[x] > avgs[y] ? 1 : 0);
        }
      });
      // Fill with sorted data.  Makes a copy, so the original data remains in
      // its original order.
      sums = MemoryManager.malloc4(nbins);
      bins = MemoryManager.malloc4 (nbins);
      for( int i=0; i<nbins; i++ ) {
        sums[i] = _sums[idxs[i]];
        bins[i] = _bins[idxs[i]];
      }
    }

    // Compute mean/var for cumulative bins from 0 to nbins inclusive.
    double sums0[] = MemoryManager.malloc8d(nbins+1);
    long     ns0[] = MemoryManager.malloc8 (nbins+1);
    for( int b=1; b<=nbins; b++ ) {
      double m0 = sums0[b-1],  m1 = sums[b-1];
      long   k0 = ns0  [b-1],  k1 = bins[b-1];
      if( k0==0 && k1==0 ) continue;
      sums0[b] = m0+m1;
      ns0  [b] = k0+k1;
    }
    long tot = ns0[nbins];
    // Is any split possible with at least min_obs?
    if( tot < 2*min_rows ) return null;
    // If we see zero variance, we must have a constant response in this
    // column.  Normally this situation is cut out before we even try to split,
    // but we might have NA's in THIS column...
    double var = sums0[nbins]*(tot - sums0[nbins]);
    if( var == 0 ) { assert isConstantResponse(); return null; }
    // If variance is really small, then the predictions (which are all at
    // single-precision resolution), will be all the same and the tree split
    // will be in vain.
    if( ((float)var) == 0f ) return null;

    // Compute mean/var for cumulative bins from nbins to 0 inclusive.
    double sums1[] = MemoryManager.malloc8d(nbins+1);
    long     ns1[] = MemoryManager.malloc8 (nbins+1);
    for( int b=nbins-1; b>=0; b-- ) {
      double m0 = sums1[b+1],  m1 = sums[b];
      long   k0 = ns1  [b+1],  k1 = bins[b];
      if( k0==0 && k1==0 ) continue;
      sums1[b] = m0+m1;
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
      double se0 = sums0[b]*(1. - sums0[b]/ns0[b]);
      double se1 = sums1[b]*(1. - sums1[b]/ns1[b]);
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
        long N =         ns0[b  ] + ns1[b+1];
        if( N < min_rows ) continue; // Ignore too small splits
        double sums2 = sums0[b  ]+sums1[b+1];
        double si =    sums2*(1.- sums2/N) ; // Left+right, excluding 'b'
        double sx =    sums [b]  -sums[b]*sums[b]/bins[b]; // Just 'b'
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
      for( int i=0; i<best; i++ ) {
        min=Math.min(min,idxs[i]);
        max=Math.max(max,idxs[i]);
      }
      bs = new IcedBitSet(max-min+1,min);
      for( int i=0; i<best; i++ ) bs.set(idxs[i]);
      equal = (byte)(bs.max() < 32 ? 2 : 3); // Flag for bitset split; also check max size
    }

    if( best==0 ) return null;  // No place to split
    double se = sums1[0]*(1 - sums1[0]/ns1[0]); // Squared Error with no split
    if( se <= best_se0+best_se1) return null; // Ultimately roundoff error loses, and no split actually helped
    long  n0 = equal == 0 ?   ns0[best] :   ns0[best]+  ns1[best+1];
    long  n1 = equal == 0 ?   ns1[best] :  bins[best]              ;
    double p0 = equal == 0 ? sums0[best] : sums0[best]+sums1[best+1];
    double p1 = equal == 0 ? sums1[best] :  sums[best]              ;
    return new DTree.Split(col,best,bs,equal,se,best_se0,best_se1,n0,n1,p0/n0,p1/n1);
  }

  @Override public long byteSize0() {
    return 8*1 +                // 1 more internal arrays
      24+_sums.length<<3;
  }
}
