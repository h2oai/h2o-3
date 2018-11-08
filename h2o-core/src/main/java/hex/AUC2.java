package hex;

import water.Iced;
import water.MRTask;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Chunk;
import water.fvec.Vec;
import water.util.fp.Function;
import water.util.fp.Functions;

import java.util.Arrays;

import static hex.AUC2.ThresholdCriterion.precision;
import static hex.AUC2.ThresholdCriterion.recall;

/** One-pass approximate AUC
 *
 *  This algorithm can compute the AUC in 1-pass with good resolution.  During
 *  the pass, it builds an online histogram of the probabilities up to the
 *  resolution (number of bins) asked-for.  It also computes the true-positive
 *  and false-positive counts for the histogramed thresholds.  With these in
 *  hand, we can compute the TPR (True Positive Rate) and the FPR for the given
 *  thresholds; these define the (X,Y) coordinates of the AUC.
 */
public class AUC2 extends Iced {
  public final int _nBins; // Max number of bins; can be less if there are fewer points
  public final double[] _ths;   // Thresholds
  public final double[] _tps;     // True  Positives
  public final double[] _fps;     // False Positives
  public final double _p, _n;     // Actual trues, falses
  public final double _auc, _gini, _pr_auc; // Actual AUC value
  public final int _max_idx;    // Threshold that maximizes the default criterion

  public static final ThresholdCriterion DEFAULT_CM = ThresholdCriterion.f1;
  // Default bins, good answers on a highly unbalanced sorted (and reverse
  // sorted) datasets
  public static final int NBINS = 400;

  /** Criteria for 2-class Confusion Matrices
   *
   *  This is an Enum class, with an exec() function to compute the criteria
   *  from the basic parts, and from an AUC2 at a given threshold index.
   */
  public enum ThresholdCriterion {
    f1(false) { @Override double exec( double tp, double fp, double fn, double tn ) {
        final double prec = precision.exec(tp,fp,fn,tn);
        final double recl = tpr   .exec(tp,fp,fn,tn);
        return 2. * (prec * recl) / (prec + recl);
      } },
    f2(false) { @Override double exec( double tp, double fp, double fn, double tn ) {
        final double prec = precision.exec(tp,fp,fn,tn);
        final double recl = tpr   .exec(tp,fp,fn,tn);
        return 5. * (prec * recl) / (4. * prec + recl);
      } },
    f0point5(false) { @Override double exec( double tp, double fp, double fn, double tn ) {
        final double prec = precision.exec(tp,fp,fn,tn);
        final double recl = tpr   .exec(tp,fp,fn,tn);
        return 1.25 * (prec * recl) / (.25 * prec + recl);
      } },
    accuracy(false) { @Override double exec( double tp, double fp, double fn, double tn ) { return (tn+tp)/(tp+fn+tn+fp); } },
    precision(false) { @Override double exec( double tp, double fp, double fn, double tn ) { return tp/(tp+fp); } },
    recall(false) { @Override double exec( double tp, double fp, double fn, double tn ) { return tp/(tp+fn); } },
    specificity(false) { @Override double exec( double tp, double fp, double fn, double tn ) { return tn/(tn+fp); } },
    absolute_mcc(false) { @Override double exec( double tp, double fp, double fn, double tn ) {
        double mcc = (tp*tn - fp*fn);
        if (mcc == 0) return 0;
        mcc /= Math.sqrt((tp+fp)*(tp+fn)*(tn+fp)*(tn+fn));
        assert(Math.abs(mcc)<=1.) : tp + " " + fp + " " + fn + " " + tn;
        return Math.abs(mcc);
      } },
    // minimize max-per-class-error by maximizing min-per-class-accuracy.
    // Report from max_criterion is the smallest correct rate for both classes.
    // The max min-error-rate is 1.0 minus that.
    min_per_class_accuracy(false) { @Override double exec( double tp, double fp, double fn, double tn ) {
        return Math.min(tp/(tp+fn),tn/(tn+fp));
      } },
    mean_per_class_accuracy(false) { @Override double exec( double tp, double fp, double fn, double tn ) {
      return 0.5*(tp/(tp+fn) + tn/(tn+fp));
    } },
    tns(true ) { @Override double exec( double tp, double fp, double fn, double tn ) { return tn; } },
    fns(true ) { @Override double exec( double tp, double fp, double fn, double tn ) { return fn; } },
    fps(true ) { @Override double exec( double tp, double fp, double fn, double tn ) { return fp; } },
    tps(true ) { @Override double exec( double tp, double fp, double fn, double tn ) { return tp; } },
    tnr(false) { @Override double exec( double tp, double fp, double fn, double tn ) { return tn/(fp+tn); } },
    fnr(false) { @Override double exec( double tp, double fp, double fn, double tn ) { return fn/(fn+tp); } },
    fpr(false) { @Override double exec( double tp, double fp, double fn, double tn ) { return fp/(fp+tn); } },
    tpr(false) { @Override double exec( double tp, double fp, double fn, double tn ) { return tp/(tp+fn); } },
    ;
    public final boolean _isInt; // Integral-Valued data vs Real-Valued
    ThresholdCriterion(boolean isInt) { _isInt = isInt; }

    /** @param tp True  Positives (predicted  true, actual true )
     *  @param fp False Positives (predicted  true, actual false)
     *  @param fn False Negatives (predicted false, actual true )
     *  @param tn True  Negatives (predicted false, actual false)
     *  @return criteria */
    abstract double exec( double tp, double fp, double fn, double tn );
    public double exec( AUC2 auc, int idx ) { return exec(auc.tp(idx),auc.fp(idx),auc.fn(idx),auc.tn(idx)); }
    public double max_criterion( AUC2 auc ) { return exec(auc,max_criterion_idx(auc)); }

    /** Convert a criterion into a threshold index that maximizes the criterion
     *  @return Threshold index that maximizes the criterion
     */
    public int max_criterion_idx( AUC2 auc ) {
      double md = -Double.MAX_VALUE;
      int mx = -1;
      for( int i=0; i<auc._nBins; i++ ) {
        double d = exec(auc,i);
        if( d > md ) { md = d; mx = i; }
      }
      return mx;
    }
    public static final ThresholdCriterion[] VALUES = values();
  } // public enum ThresholdCriterion

  public double threshold( int idx ) { return _ths[idx]; }
  public double tp( int idx ) { return _tps[idx]; }
  public double fp( int idx ) { return _fps[idx]; }
  public double tn( int idx ) { return _n-_fps[idx]; }
  public double fn( int idx ) { return _p-_tps[idx]; }

  /** @return maximum F1 */
  public double maxF1() { return ThresholdCriterion.f1.max_criterion(this); }
  
  public Function<Integer, Double> forCriterion(final ThresholdCriterion tc) {
    return new Function<Integer, Double>() {
      public Double apply(Integer i) {
        return tc.exec(AUC2.this, i);
      }
    };
  }

  /** Default bins, good answers on a highly unbalanced sorted (and reverse
   *  sorted) datasets */
  public AUC2( Vec probs, Vec actls ) { this(NBINS,probs,actls); }

  /** User-specified bin limits.  Time taken is product of nBins and rows;
   *  large nBins can be very slow. */
  AUC2( int nBins, Vec probs, Vec actls ) { this(new AUC_Impl(nBins).doAll(probs,actls)._bldr); }

  public AUC2( AUCBuilder bldr ) {
    // Copy result arrays into base object, shrinking to match actual bins
    _nBins = bldr._n;
    assert _nBins >= 1 : "Must have >= 1 bins for AUC calculation, but got " + _nBins;
    _ths = Arrays.copyOf(bldr._ths,_nBins);
    _tps = Arrays.copyOf(bldr._tps,_nBins);
    _fps = Arrays.copyOf(bldr._fps,_nBins);
    // Reverse everybody; thresholds from 1 down to 0, easier to read
    for( int i=0; i<((_nBins)>>1); i++ ) {
      double tmp= _ths[i];  _ths[i] = _ths[_nBins-1-i]; _ths[_nBins-1-i] = tmp ;
      double tmpt = _tps[i];  _tps[i] = _tps[_nBins-1-i]; _tps[_nBins-1-i] = tmpt;
      double tmpf = _fps[i];  _fps[i] = _fps[_nBins-1-i]; _fps[_nBins-1-i] = tmpf;
    }

    // Rollup counts, so that computing the rates are easier.
    // The AUC is (TPR,FPR) as the thresholds roll about
    double p=0, n=0;
    for( int i=0; i<_nBins; i++ ) { 
      p += _tps[i]; _tps[i] = p;
      n += _fps[i]; _fps[i] = n;
    }
    _p = p;  _n = n;
    _auc = compute_auc();
    _pr_auc = pr_auc();
    _gini = 2*_auc-1;
    _max_idx = DEFAULT_CM.max_criterion_idx(this);
  }

  // empty AUC, helps avoid NPE in edge cases
  AUC2() {
    _nBins = 0;
    _ths = _tps = _fps = new double[0];
    _p =_n = 0;
    _auc = _gini = _pr_auc = Double.NaN;
    _max_idx = -1;
  }

  private boolean isEmpty() {
    return _nBins == 0;
  }

  public double pr_auc() {
    if (isEmpty()) {
      return Double.NaN;
    }
    checkRecallValidity();
    return Functions.integrate(forCriterion(recall), forCriterion(precision), 0, _nBins-1);
  }

  // Checks that recall is a non-decreasing function
  void checkRecallValidity() {
    double x0 = recall.exec(this, 0);
    for (int i = 1; i < _nBins; i++) {
      double x1 = recall.exec(this, i);
      if (x0 > x1)
        throw new H2OIllegalArgumentException(String.valueOf(i), "recall", x0 + " > " + x1);
      x0 = x1;
    }
  }

  // Compute the Area Under the Curve, where the curve is defined by (TPR,FPR)
  // points.  TPR and FPR are monotonically increasing from 0 to 1.
  private double compute_auc() {
    if (_fps[_nBins-1] == 0) return 1.0; //special case
    if (_tps[_nBins-1] == 0) return 0.0; //special case

    // All math is computed scaled by TP and FP.  We'll descale once at the
    // end.  Trapezoids from (tps[i-1],fps[i-1]) to (tps[i],fps[i])
    double tp0 = 0, fp0 = 0;
    double area = 0;
    for( int i=0; i<_nBins; i++ ) {
      area += (_fps[i]-fp0)*(_tps[i]+tp0)/2.0; // Trapezoid
      tp0 = _tps[i];  fp0 = _fps[i];
    }
    // Descale
    return area/_p/_n;
  }

  // Build a CM for a threshold index. - typed as doubles because of double observation weights
  public double[/*actual*/][/*predicted*/] buildCM( int idx ) {
    //  \ predicted:  0   1
    //    actual  0: TN  FP
    //            1: FN  TP
    return new double[][]{{tn(idx),fp(idx)},{fn(idx),tp(idx)}};
  }

  /** @return the default CM, or null for an empty AUC */
  public double[/*actual*/][/*predicted*/] defaultCM( ) { return _max_idx == -1 ? null : buildCM(_max_idx); }
  /** @return the default threshold; threshold that maximizes the default criterion */
  public double defaultThreshold( ) { return _max_idx == -1 ? 0.5 : _ths[_max_idx]; }
  /** @return the error of the default CM */
  public double defaultErr( ) { return _max_idx == -1 ? Double.NaN : (fp(_max_idx)+fn(_max_idx))/(_p+_n); }



  // Compute an online histogram of the predicted probabilities, along with
  // true positive and false positive totals in each histogram bin.
  private static class AUC_Impl extends MRTask<AUC_Impl> {
    final int _nBins;
    AUCBuilder _bldr;
    AUC_Impl( int nBins ) { _nBins = nBins; }
    @Override public void map( Chunk ps, Chunk as ) {
      AUCBuilder bldr = _bldr = new AUCBuilder(_nBins);
      for( int row = 0; row < ps._len; row++ )
        if( !ps.isNA(row) && !as.isNA(row) )
          bldr.perRow(ps.atd(row),(int)as.at8(row),1);
    }
    @Override public void reduce( AUC_Impl auc ) { _bldr.reduce(auc._bldr); }
  }

  public static class AUCBuilder extends Iced {
    final int _nBins;
    int _n;                     // Current number of bins
    final double _ths[];        // Histogram bins, center
    final double _sqe[];        // Histogram bins, squared error
    final double _tps[];        // Histogram bins, true  positives
    final double _fps[];        // Histogram bins, false positives
    // Merging this bin with the next gives the least increase in squared
    // error, or -1 if not known.  Requires a linear scan to find.
    int    _ssx;
    private boolean _useFastPath = true; // only used for unit tests to check that the
    public AUCBuilder(int nBins) {
      _nBins = nBins;
      _ths = new double[nBins<<1]; // Threshold; also the mean for this bin
      _sqe = new double[nBins<<1]; // Squared error (variance) in this bin
      _tps = new double[nBins<<1]; // True  positives
      _fps = new double[nBins<<1]; // False positives
      _ssx = -1;                   // Unknown best merge bin
    }

    // Intended for unit tests only
    AUCBuilder(int nBins, boolean useFastPath) {
      this(nBins);
      _useFastPath = useFastPath;
    }

    public void perRow(double pred, int act, double w ) {
      // Insert the prediction into the set of histograms in sorted order, as
      // if its a new histogram bin with 1 count.
      assert !Double.isNaN(pred);
      assert act==0 || act==1;  // Actual better be 0 or 1
      int idx = Arrays.binarySearch(_ths,0,_n,pred);
      if( idx >= 0 ) {          // Found already in histogram; merge results
        if( act==0 ) _fps[idx]+=w; else _tps[idx]+=w; // One more count; no change in squared error
        _ssx = -1;              // Blows the known best merge
        return;
      }
      idx = -idx-1;             // Get index to insert at

      // If already full bins, try to instantly merge into an existing bin
      if (_n == _nBins &&
              _useFastPath &&              // Optimization enabled
              idx > 0 && idx < _n &&       // Give up for the corner cases
              _ths[idx - 1] != _ths[idx])  // Histogram has duplicates (mergeOneBin will get rid of them)
      {       // Need to merge to shrink things
        final int ssx = find_smallest();
        double dssx = _sqe[ssx] + _sqe[ssx+1] + compute_delta_error(_ths[ssx+1], k(ssx+1), _ths[ssx], k(ssx));
        // See if this point will fold into either the left or right bin
        // immediately.  This is the desired fast-path.
        double d0 = _sqe[idx-1] + compute_delta_error(pred,w,_ths[idx-1],k(idx-1));
        double d1 = _sqe[idx] + compute_delta_error(_ths[idx],k(idx),pred,w);
        if (d0 < dssx || d1 < dssx) {
          if (d0 <= d1) idx--; // Pick correct bin
          if (ssx == idx-1 || ssx == idx)
            _ssx = -1;         // We don't know the minimum anymore
          double k = k(idx);
          if (act == 0) _fps[idx] += w; else _tps[idx] += w;
          _sqe[idx] = _sqe[idx] + compute_delta_error(pred, w, _ths[idx], k);
          _ths[idx] = (_ths[idx] * k + pred * w) / (k + w);
          return;
        }
      }

      // Must insert this point as it's own threshold (which is not insertion
      // point), either because we have too few bins or because we cannot
      // instantly merge the new point into an existing bin.
      if (idx == 0 || idx == _n ||     // Just because we didn't bother to deal with the corner cases ^^^
              idx == _ssx) _ssx = -1;  // Smallest error becomes one of the splits
      else if( idx < _ssx ) _ssx++; // Smallest error will slide right 1

      // Slide over to do the insert.  Horrible slowness.
      System.arraycopy(_ths,idx,_ths,idx+1,_n-idx);
      System.arraycopy(_sqe,idx,_sqe,idx+1,_n-idx);
      System.arraycopy(_tps,idx,_tps,idx+1,_n-idx);
      System.arraycopy(_fps,idx,_fps,idx+1,_n-idx);
      // Insert into the histogram
      _ths[idx] = pred;         // New histogram center
      _sqe[idx] = 0;            // Only 1 point, so no squared error
      if( act==0 ) { _tps[idx]=0; _fps[idx]=w; }
      else         { _tps[idx]=w; _fps[idx]=0; }
      _n++;
      if( _n > _nBins )         // Merge as needed back down to nBins
        mergeOneBin();          // Merge best pair of bins
    }

    public void reduce( AUCBuilder bldr ) {
      // Merge sort the 2 sorted lists into the double-sized arrays.  The tail
      // half of the double-sized array is unused, but the front half is
      // probably a source.  Merge into the back.
      int x=     _n-1;
      int y=bldr._n-1;
      while( x+y+1 >= 0 ) {
        boolean self_is_larger = y < 0 || (x >= 0 && _ths[x] >= bldr._ths[y]);
        AUCBuilder b = self_is_larger ? this : bldr;
        int      idx = self_is_larger ?   x  :   y ;
        _ths[x+y+1] = b._ths[idx];
        _sqe[x+y+1] = b._sqe[idx];
        _tps[x+y+1] = b._tps[idx];
        _fps[x+y+1] = b._fps[idx];
        if( self_is_larger ) x--; else y--;
      }
      _n += bldr._n;

      // Merge elements with least squared-error increase until we get fewer
      // than _nBins and no duplicates.  May require many merges.
      while( _n > _nBins || dups() ) 
        mergeOneBin();
    }

    private void mergeOneBin( ) {
      // Too many bins; must merge bins.  Merge into bins with least total
      // squared error.  Horrible slowness linear arraycopy.
      int ssx = find_smallest();

      // Merge two bins.  Classic bins merging by averaging the histogram
      // centers based on counts.
      double k0 = k(ssx);
      double k1 = k(ssx+1);
      _sqe[ssx] = _sqe[ssx]+_sqe[ssx+1]+compute_delta_error(_ths[ssx+1],k1,_ths[ssx],k0);
      _ths[ssx] = (_ths[ssx]*k0 + _ths[ssx+1]*k1) / (k0+k1);
      _tps[ssx] += _tps[ssx+1];
      _fps[ssx] += _fps[ssx+1];
      // Slide over to crush the removed bin at index (ssx+1)
      System.arraycopy(_ths,ssx+2,_ths,ssx+1,_n-ssx-2);
      System.arraycopy(_sqe,ssx+2,_sqe,ssx+1,_n-ssx-2);
      System.arraycopy(_tps,ssx+2,_tps,ssx+1,_n-ssx-2);
      System.arraycopy(_fps,ssx+2,_fps,ssx+1,_n-ssx-2);
      _n--;
      _ssx = -1;
    }

    // Find the pair of bins that when combined give the smallest increase in
    // squared error.  Dups never increase squared error.
    //
    // I tried code for merging bins with keeping the bins balanced in size,
    // but this leads to bad errors if the probabilities are sorted.  Also
    // tried the original: merge bins with the least distance between bin
    // centers.  Same problem for sorted data.
    private int find_smallest() {
      if( _ssx == -1 ) return (_ssx = find_smallest_impl());
      return _ssx;
    }
    private int find_smallest_impl() {
      double minSQE = Double.MAX_VALUE;
      int minI = -1;
      int n = _n;
      for( int i=0; i<n-1; i++ ) {
        double derr = compute_delta_error(_ths[i+1],k(i+1),_ths[i],k(i));
        if( derr == 0 ) return i; // Dup; no increase in SQE so return immediately
        double sqe = _sqe[i]+_sqe[i+1]+derr;
        if( sqe < minSQE ) {
          minI = i;  minSQE = sqe;
        }
      }
      return minI;
    }

    private boolean dups() {
      int n = _n;
      for( int i=0; i<n-1; i++ ) {
        double derr = compute_delta_error(_ths[i+1],k(i+1),_ths[i],k(i));
        if( derr == 0 ) { _ssx = i; return true; }
      }
      return false;
    }


    private double compute_delta_error( double ths1, double n1, double ths0, double n0 ) {
      // If thresholds vary by less than a float ULP, treat them as the same.
      // Some models only output predictions to within float accuracy (so a
      // variance here is junk), and also it's not statistically sane to have
      // a model which varies predictions by such a tiny change in thresholds.
      double delta = (float)ths1-(float)ths0;
      // Parallel equation drawn from:
      //  http://en.wikipedia.org/wiki/Algorithms_for_calculating_variance#Online_algorithm
      return delta*delta*n0*n1 / (n0+n1);
    }

    private double k( int idx ) { return _tps[idx]+_fps[idx]; }
  }


  // ==========
  // Given the probabilities of a 1, and the actuals (0/1) report the perfect
  // AUC found by sorting the entire dataset.  Expensive, and only works for
  // small data (probably caps out at about 10M rows).
  public static double perfectAUC( Vec vprob, Vec vacts ) {
    if( vacts.min() < 0 || vacts.max() > 1 || !vacts.isInt() )
      throw new IllegalArgumentException("Actuals are either 0 or 1");
    if( vprob.min() < 0 || vprob.max() > 1 )
      throw new IllegalArgumentException("Probabilities are between 0 and 1");
    // Horrible data replication into array of structs, to sort.  
    Pair[] ps = new Pair[(int)vprob.length()];
    Vec.Reader rprob = vprob.new Reader();
    Vec.Reader racts = vacts.new Reader();
    for( int i=0; i<ps.length; i++ )
      ps[i] = new Pair(rprob.at(i),(byte)racts.at8(i));
    return perfectAUC(ps);
  }
  public static double perfectAUC( double ds[], double[] acts ) {
    Pair[] ps = new Pair[ds.length];
    for( int i=0; i<ps.length; i++ )
      ps[i] = new Pair(ds[i],(byte)acts[i]);
    return perfectAUC(ps);
  }

  private static double perfectAUC( Pair[] ps ) {
    // Sort by probs, then actuals - so tied probs have the 0 actuals before
    // the 1 actuals.  Sort probs from largest to smallest - so both the True
    // and False Positives are zero to start.
    Arrays.sort(ps,new java.util.Comparator<Pair>() {
        @Override public int compare( Pair a, Pair b ) {
          return a._prob<b._prob ? 1 : (a._prob==b._prob ? (b._act-a._act) : -1);
        }
      });

    // Compute Area Under Curve.  
    // All math is computed scaled by TP and FP.  We'll descale once at the
    // end.  Trapezoids from (tps[i-1],fps[i-1]) to (tps[i],fps[i])
    int tp0=0, fp0=0, tp1=0, fp1=0;
    double prob = 1.0;
    double area = 0;
    for( Pair p : ps ) {
      if( p._prob!=prob ) { // Tied probabilities: build a diagonal line
        area += (fp1-fp0)*(tp1+tp0)/2.0; // Trapezoid
        tp0 = tp1; fp0 = fp1;
        prob = p._prob;
      }
      if( p._act==1 ) tp1++; else fp1++;
    }
    area += (double)tp0*(fp1-fp0); // Trapezoid: Rectangle + 
    area += (double)(tp1-tp0)*(fp1-fp0)/2.0; // Right Triangle

    // Descale
    return area/tp1/fp1;
  }

  private static class Pair {
    final double _prob; final byte _act;
    Pair( double prob, byte act ) { _prob = prob; _act = act; }
  }

}
