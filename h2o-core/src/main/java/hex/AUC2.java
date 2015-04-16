package hex;

import java.util.Arrays;
import water.Iced;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Vec;

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
  public final long[] _tps;     // True  Positives
  public final long[] _fps;     // False Positives
  public final long _p, _n;     // Actual trues, falses
  public final double _auc, _gini; // Actual AUC value
  public final int _max_idx;    // Threshold that maximizes the default criterion

  public static final ThresholdCriterion DEFAULT_CM = ThresholdCriterion.f1;
  // Default bins, good answers on a highly unbalanced sorted (and reverse
  // sorted) datasets
  public static final int NBINS = 256;

  /** Criteria for 2-class Confusion Matrices
   *
   *  This is an Enum class, with an exec() function to compute the criteria
   *  from the basic parts, and from an AUC2 at a given threshold index.
   */
  public enum ThresholdCriterion {
    f1(false) { @Override double exec( long tp, long fp, long fn, long tn ) {
        final double prec = precision.exec(tp,fp,fn,tn);
        final double recl = recall   .exec(tp,fp,fn,tn);
        return 2. * (prec * recl) / (prec + recl);
      } },
    f2(false) { @Override double exec( long tp, long fp, long fn, long tn ) {
        final double prec = precision.exec(tp,fp,fn,tn);
        final double recl = recall   .exec(tp,fp,fn,tn);
        return 5. * (prec * recl) / (4. * prec + recl);
      } },
    f0point5(false) { @Override double exec( long tp, long fp, long fn, long tn ) {
        final double prec = precision.exec(tp,fp,fn,tn);
        final double recl = recall   .exec(tp,fp,fn,tn);
        return 1.25 * (prec * recl) / (.25 * prec + recl);
      } },
    accuracy(false) { @Override double exec( long tp, long fp, long fn, long tn ) {
        return 1.0-((double)fn+fp)/(tp+fn+tn+fp);
      } },
    precision(false) { @Override double exec( long tp, long fp, long fn, long tn ) {
        return (double)tp/(tp+fp);
      } },
    recall(false) { @Override double exec( long tp, long fp, long fn, long tn ) {
        return (double)tp/(tp+fn);
      } },
    specificity(false) { @Override double exec( long tp, long fp, long fn, long tn ) {
        return (double)tn/(tn+fp);
      } },
    absolute_MCC(false) { @Override double exec( long tp, long fp, long fn, long tn ) {
        double mcc = ((double)tp*tn - (double)fp*fn)/Math.sqrt(((double)tp+fp)*((double)tp+fn)*((double)tn+fp)*((double)tn+fn));
        assert(Math.abs(mcc)<=1.) : tp + " " + fp + " " + fn + " " + tn;
        return Math.abs(mcc);
      } },
    // minimize max-per-class-error by maximizing min-per-class-correct.
    // Report from max_criterion is the smallest correct rate for both classes.
    // The max min-error-rate is 1.0 minus that.
    min_per_class_correct(false) { @Override double exec( long tp, long fp, long fn, long tn ) {
        return Math.min((double)tp/(tp+fn),(double)tn/(tn+fp));
      } },
    tns(true) { @Override double exec( long tp, long fp, long fn, long tn ) { return tn; } },
    fns(true) { @Override double exec( long tp, long fp, long fn, long tn ) { return fn; } },
    fps(true) { @Override double exec( long tp, long fp, long fn, long tn ) { return fp; } },
    tps(true) { @Override double exec( long tp, long fp, long fn, long tn ) { return tp; } },
    ;
    public final boolean _isInt; // Integral-Valued data vs Real-Valued
    ThresholdCriterion(boolean isInt) { _isInt = isInt; }

    /** @param tp True  Positives (predicted  true, actual true )
     *  @param fp False Positives (predicted  true, actual false)
     *  @param fn False Negatives (predicted false, actual true )
     *  @param tn True  Negatives (predicted false, actual false)
     *  @return criteria */
    abstract double exec( long tp, long fp, long fn, long tn );
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
  public long tp( int idx ) { return _tps[idx]; }
  public long fp( int idx ) { return _fps[idx]; }
  public long tn( int idx ) { return _n-_fps[idx]; }
  public long fn( int idx ) { return _p-_tps[idx]; }

  /** @return maximum F1 */
  public double maxF1() { return ThresholdCriterion.f1.max_criterion(this); }

  /** Default bins, good answers on a highly unbalanced sorted (and reverse
   *  sorted) datasets */
  public AUC2( Vec probs, Vec actls ) { this(NBINS,probs,actls); }

  /** User-specified bin limits.  Time taken is product of nBins and rows;
   *  large nBins can be very slow. */
  AUC2( int nBins, Vec probs, Vec actls ) { this(new AUC_Impl(nBins).doAll(probs,actls)._bldr); }

  public AUC2( AUCBuilder bldr ) { 
    // Copy result arrays into base object, shrinking to match actual bins
    _nBins = bldr._n;
    _ths = Arrays.copyOf(bldr._ths,_nBins);
    _tps = Arrays.copyOf(bldr._tps,_nBins);
    _fps = Arrays.copyOf(bldr._fps,_nBins);
    // Reverse everybody; thresholds from 1 down to 0, easier to read
    for( int i=0; i<((_nBins)>>1); i++ ) {
      double tmp= _ths[i];  _ths[i] = _ths[_nBins-1-i]; _ths[_nBins-1-i] = tmp ;
      long tmpt = _tps[i];  _tps[i] = _tps[_nBins-1-i]; _tps[_nBins-1-i] = tmpt;
      long tmpf = _fps[i];  _fps[i] = _fps[_nBins-1-i]; _fps[_nBins-1-i] = tmpf;
    }

    // Rollup counts, so that computing the rates are easier.
    // The AUC is (TPR,FPR) as the thresholds roll about
    long p=0, n=0;
    for( int i=0; i<_nBins; i++ ) { 
      p += _tps[i]; _tps[i] = p;
      n += _fps[i]; _fps[i] = n;
    }
    _p = p;  _n = n;
    _auc = compute_auc();
    _gini = 2*_auc-1;
    _max_idx = DEFAULT_CM.max_criterion_idx(this);
  }

  // Compute the Area Under the Curve, where the curve is defined by (TPR,FPR)
  // points.  TPR and FPR are monotonically increasing from 0 to 1.
  private double compute_auc() {
    // All math is computed scaled by TP and FP.  We'll descale once at the
    // end.  Trapezoids from (tps[i-1],fps[i-1]) to (tps[i],fps[i])
    long tp0 = 0, fp0 = 0;
    double area = 0;
    for( int i=0; i<_nBins; i++ ) {
      area += tp0*(_fps[i]-fp0); // Trapezoid: Square + 
      area += (_tps[i]-tp0)*(_fps[i]-fp0)/2.0; // Right Triangle
      tp0 = _tps[i];  fp0 = _fps[i];
    }
    // Descale
    return area/_p/_n;
  }

  // Build a CM for a threshold index.
  public long[/*actual*/][/*predicted*/] buildCM( int idx ) {
    //  \ predicted:  0   1
    //    actual  0: TN  FP
    //            1: FN  TP
    return new long[][]{{tn(idx),fp(idx)},{fn(idx),tp(idx)}};
  }

  /** @return the default CM, or null for an empty AUC */
  public long[/*actual*/][/*predicted*/] defaultCM( ) { return _max_idx == -1 ? null : buildCM(_max_idx); }
  /** @return the default threshold; threshold that maximizes the default criterion */
  public double defaultThreshold( ) { return _ths[_max_idx]; }
  /** @return the error of the default CM */
  public double defaultErr( ) { return ((double)fp(_max_idx)+fn(_max_idx))/(_p+_n); }



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
          bldr.perRow(ps.atd(row),(int)as.at8(row));
    }
    @Override public void reduce( AUC_Impl auc ) { _bldr.reduce(auc._bldr); }
  }

  public static class AUCBuilder extends Iced {
    final int _nBins;
    int _n;                     // Current number of bins
    double _ths[];              // Histogram bins, center
    double _sqe[];              // Histogram bins, squared error
    long   _tps[];              // Histogram bins, true  positives
    long   _fps[];              // Histogram bins, false positives
    public AUCBuilder(int nBins) {
      _nBins = nBins;
      _ths = new double[nBins<<1]; // Threshold; also the mean for this bin
      _sqe = new double[nBins<<1]; // Squared error (variance) in this bin
      _tps = new long  [nBins<<1]; // True  positives
      _fps = new long  [nBins<<1]; // False positives
    }    

    public void perRow(double pred, int act ) {
      // Insert the prediction into the set of histograms in sorted order, as
      // if its a new histogram bin with 1 count.
      assert !Double.isNaN(pred);
      assert act==0 || act==1; // Actual better be 0 or 1
      int idx = Arrays.binarySearch(_ths,0,_n,pred);
      if( idx >= 0 ) {        // Found already in histogram; merge results
        if( act==0 ) _fps[idx]++; else _tps[idx]++; // One more count; no change in squared error
        return;
      }
      // Slide over to do the insert.  Horrible slowness.
      idx = -idx-1;           // Get index to insert at
      System.arraycopy(_ths,idx,_ths,idx+1,_n-idx);
      System.arraycopy(_sqe,idx,_sqe,idx+1,_n-idx);
      System.arraycopy(_tps,idx,_tps,idx+1,_n-idx);
      System.arraycopy(_fps,idx,_fps,idx+1,_n-idx);
      // Insert into the histogram
      _ths[idx] = pred;         // New histogram center
      _sqe[idx] = 0;            // Only 1 point, so no squared error
      if( act==0 ) { _tps[idx]=0; _fps[idx]=1; }
      else         { _tps[idx]=1; _fps[idx]=0; }
      _n++;
      if( _n > _nBins )         // Merge as needed back down to nBins
        mergeOneBin();
    }

    public void reduce( AUCBuilder bldr ) {
      // Merge sort the 2 sorted lists into the double-sized arrays.  The tail
      // half of the double-sized array is unused, but the front half is
      // probably a source.  Merge into the back.
      //assert sorted();
      //assert bldr.sorted();
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
      //assert sorted();

      // Merge elements with least squared-error increase until we get fewer
      // than _nBins and no duplicates.
      boolean dups = true;
      while( (dups && _n > 1) || _n > _nBins )
        dups = mergeOneBin();
    }

//    private boolean sorted() {
//      double t = _ths[0];
//      for( int i=1; i<_n; i++ ) {
//        if( _ths[i] < t )
//          return false;
//        t = _ths[i];
//      }
//      return true;
//    }

    private boolean mergeOneBin() {
      // Too many bins; must merge bins.  Merge into bins with least total
      // squared error.  Horrible slowness linear scan.  
      boolean dups = false;
      double minSQE = Double.MAX_VALUE;
      int minI = -1;
      for( int i=0; i<_n-1; i++ ) {
        long k0 = _tps[i  ]+_fps[i  ];
        long k1 = _tps[i+1]+_fps[i+1];
        double delta = _ths[i+1]-_ths[i];
        double sqe0 = _sqe[i]+_sqe[i+1]+delta*delta*k0*k1 / (k0+k1);
        if( sqe0 < minSQE || delta==0 ) {  
          minI = i;  minSQE = sqe0; 
          if( delta==0 ) { dups = true; break; }
        }
      }

      // Here is code for merging bins with keeping the bins balanced in
      // size, but this leads to bad errors if the probabilities are sorted.
      // Also tried the original: merge bins with the least distance between
      // bin centers.  Same problem for sorted data.

      //long minV = Long.MAX_VALUE;
      //int minI = -1;
      //for( int i=0; i<_n; i++ ) {
      //  long sum = _tps[i]+_fps[i]+_tps[i+1]+_fps[i+1];
      //  if( sum < minV ||
      //      (sum==minV && _ths[i+1]-_ths[i] < _ths[minI+1]-_ths[minI]) ) {
      //    minI = i;  minV = sum; 
      //  }
      //}

      // Merge two bins.  Classic bins merging by averaging the histogram
      // centers based on counts.
      long k0 = _tps[minI  ]+_fps[minI  ];
      long k1 = _tps[minI+1]+_fps[minI+1];
      double d = (_ths[minI]*k0+_ths[minI+1]*k1)/(k0+k1);
      // Setup the new merged bin at index minI
      _ths[minI] = d;
      _sqe[minI] = minSQE;
      _tps[minI] += _tps[minI+1];
      _fps[minI] += _fps[minI+1];
      // Slide over to crush the removed bin at index (minI+1)
      System.arraycopy(_ths,minI+2,_ths,minI+1,_n-minI-2);
      System.arraycopy(_sqe,minI+2,_sqe,minI+1,_n-minI-2);
      System.arraycopy(_tps,minI+2,_tps,minI+1,_n-minI-2);
      System.arraycopy(_fps,minI+2,_fps,minI+1,_n-minI-2);
      _n--;
      return dups;
    }
  }
}
