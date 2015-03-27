package hex;

import java.util.Arrays;
import water.*;
import water.fvec.*;
import water.MRTask;

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
  int _nBins;      // Max number of bins; can be less if there are fewer points
  double[] _ths;   // Thresholds
  long[] _tps;     // True  Positives
  long[] _fps;     // False Positives
  long _p, _n;     // Actual trues, falses
  double _auc;     // Actual AUC value

  /** Criteria for 2-class Confusion Matrices
   *
   *  This is an Enum class, with an exec() function to compute the criteria
   *  from the basic parts, and from an AUC2 at a given threshold index.
   */
  public enum ThresholdCriterion {
    f1() { @Override double exec( long tp, long fp, long fn, long tn ) {
        final double prec = precision.exec(tp,fp,fn,tn);
        final double recl = recall   .exec(tp,fp,fn,tn);
        return 2. * (prec * recl) / (prec + recl);
      } },
    f2() { @Override double exec( long tp, long fp, long fn, long tn ) {
        final double prec = precision.exec(tp,fp,fn,tn);
        final double recl = recall   .exec(tp,fp,fn,tn);
        return 5. * (prec * recl) / (4. * prec + recl);
      } },
    f0point5() { @Override double exec( long tp, long fp, long fn, long tn ) {
        final double prec = precision.exec(tp,fp,fn,tn);
        final double recl = recall   .exec(tp,fp,fn,tn);
        return 1.25 * (prec * recl) / (.25 * prec + recl);
      } },
    accuracy() { @Override double exec( long tp, long fp, long fn, long tn ) {
        return 1.0-((double)fn+fp)/(tp+fn+tn+fp);
      } },
    precision() { @Override double exec( long tp, long fp, long fn, long tn ) {
        return (double)tp/(tp+fp);
      } },
    recall() { @Override double exec( long tp, long fp, long fn, long tn ) {
        return (double)tp/(tp+fn);
      } },
    specificity() { @Override double exec( long tp, long fp, long fn, long tn ) {
        return (double)tn/(tn+fp);
      } },
    absolute_MCC() { @Override double exec( long tp, long fp, long fn, long tn ) {
        double mcc = (tp*tn - fp*fn)/Math.sqrt((tp+fp)*(tp+fn)*(tn+fp)*(tn+fn));
        return Math.abs(mcc);
      } },
    // minimize max-per-class-error by maximizing min-per-class-correct.
    // Report from max_criterion is the smallest correct rate for both classes.
    // The max min-error-rate is 1.0 minus that.
    minPerClassCorrect() { @Override double exec( long tp, long fp, long fn, long tn ) {
        return Math.min((double)tp/(tp+fn),(double)tn/(tn+fp));
      } },
    ;

    /** 
     *  @param tp True  Positives (predicted  true, actual true )
     *  @param fp False Positives (predicted  true, actual false)
     *  @param fn False Negatives (predicted false, actual true )
     *  @param tn True  Negatives (predicted false, actual false)
     *  The sum of actual Trues and Falses is count of obs not missing either actual or prediction
     *  @param p Actual Trues
     *  @param n Actual Falses
     *  @return criteria
     */
    abstract double exec( long tp, long fp, long fn, long tn );

    public double exec( AUC2 auc, int idx ) { return exec(auc.tp(idx),auc.fp(idx),auc.fn(idx),auc.tn(idx)); }

    public double max_criterion( AUC2 auc ) {
      return exec(auc,max_criterion_idx(auc));
    }

    // Convert a criterion into a threshold index that maximizes the criterion
    public int max_criterion_idx( AUC2 auc ) {
      double md = -Double.MAX_VALUE;
      int mx = -1;
      for( int i=0; i<auc._nBins; i++ ) {
        double d = exec(auc,i);
        if( d > md ) { md = d; mx = i; }
      }
      return mx;
    }
  }

  public double threshold( int idx ) { return _ths[idx]; }
  public long tp( int idx ) { return _tps[idx]; }
  public long fp( int idx ) { return _fps[idx]; }
  public long tn( int idx ) { return _n-_fps[idx]; }
  public long fn( int idx ) { return _p-_tps[idx]; }


  /** Default bins, good answers on a highly unbalanced sorted (and reverse
   *  sorted) datasets */
  public AUC2( Vec probs, Vec actls ) { this(256,probs,actls); }

  /** User-specified bin limits.  Time taken is product of nBins and rows;
   *  large nBins can be very slow. */
  AUC2( int nBins, Vec probs, Vec actls ) { 
    _nBins = nBins;
    AUC_Impl auc = new AUC_Impl(nBins).doAll(probs,actls);
    // Copy result arrays into base object, shrinking to match actual bins
    _nBins = nBins = auc._nBins;
    _ths = Arrays.copyOf(auc._ths,nBins);
    _tps = Arrays.copyOf(auc._tps,nBins);
    _fps = Arrays.copyOf(auc._fps,nBins);
    // Reverse everybody; thresholds from 1 down to 0, easier to read
    for( int i=0; i<((nBins)>>1); i++ ) {
      double tmp= _ths[i];  _ths[i] = _ths[nBins-1-i]; _ths[nBins-1-i] = tmp ;
      long tmpt = _tps[i];  _tps[i] = _tps[nBins-1-i]; _tps[nBins-1-i] = tmpt;
      long tmpf = _fps[i];  _fps[i] = _fps[nBins-1-i]; _fps[nBins-1-i] = tmpf;
    }

    // Rollup counts, so that computing the rates are easier.
    // The AUC is (TPR,FPR) as the thresholds roll about
    long p=0, n=0;
    for( int i=0; i<nBins; i++ ) { 
      p += _tps[i]; _tps[i] = p;
      n += _fps[i]; _fps[i] = n;
    }
    _p = p;  _n = n;
    _auc = compute_auc();
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

  // Compute an online histogram of the predicted probabilities, along with
  // true positive and false positive totals in each histogram bin.
  private static class AUC_Impl extends MRTask<AUC_Impl> {
    int _nBins;                 // Max Number of bins
    double _ths[];              // Histogram bins, center
    double _sqe[];              // Histogram bins, squared error
    long   _tps[];              // Histogram bins, true  positives
    long   _fps[];              // Histogram bins, false positives
    AUC_Impl( int nBins ) { _nBins = nBins; }

    @Override public void map( Chunk ps, Chunk as ) {
      double ths[] = new double[_nBins+1]; // Threshold; also the mean for this bin
      double sqe[] = new double[_nBins+1]; // Squared error (variance) in this bin
      long   tps[] = new long  [_nBins+1]; // True  positives
      long   fps[] = new long  [_nBins+1]; // False positives
      int n=0;
      for( int row = 0; row < ps._len; row++ ) {
        // Insert the prediction into the set of histograms in sorted order, as
        // if its a new histogram bin with 1 count.
        double pred  = ps.atd(row);
        if( Double.isNaN(pred) || as.isNA(row) ) continue;
        int act = (int)as.at8(row);
        assert act==0 || act==1; // Actual better be 0 or 1
        int idx = Arrays.binarySearch(ths,0,n,pred);
        if( idx >= 0 ) {        // Found already in histogram; merge results
          if( act==0 ) fps[idx]++; else tps[idx]++; // One more count; no change in squared error
          continue;
        }
        // Slide over to do the insert.  Horrible slowness.
        idx = -idx-1;           // Get index to insert at
        System.arraycopy(ths,idx,ths,idx+1,n-idx);
        System.arraycopy(sqe,idx,sqe,idx+1,n-idx);
        System.arraycopy(tps,idx,tps,idx+1,n-idx);
        System.arraycopy(fps,idx,fps,idx+1,n-idx);
        // Insert into the histogram
        ths[idx] = pred;        // New histogram center
        sqe[idx] = 0;           // Only 1 point, so no squared error
        if( act==0 ) { tps[idx]=0; fps[idx]=1; }
        else         { tps[idx]=1; fps[idx]=0; }
        n++;
        if( n <= _nBins ) continue; // No need to merge bins

        // Too many bins; must merge bins.  Merge into bins with least total
        // squared error.  Horrible slowness linear scan.  
        double minSQE = Double.MAX_VALUE;
        int minI = -1;
        for( int i=0; i<_nBins; i++ ) {
          long k0 = tps[i  ]+fps[i  ];
          long k1 = tps[i+1]+fps[i+1];
          double delta = ths[i+1]-ths[i];
          double sqe0 = sqe[i]+sqe[i+1]+delta*delta*k0*k1 / (k0+k1);
          if( sqe0 < minSQE ) {  minI = i;  minSQE = sqe0; }
        }

        // Here is code for merging bins with keeping the bins balanced in
        // size, but this leads to bad errors if the probabilities are sorted.
        // Also tried the original: merge bins with the least distance between
        // bin centers.  Same problem for sorted data.

        //long minV = Long.MAX_VALUE;
        //int minI = -1;
        //for( int i=0; i<_nBins; i++ ) {
        //  long sum = tps[i]+fps[i]+tps[i+1]+fps[i+1];
        //  if( sum < minV ||
        //      (sum==minV && ths[i+1]-ths[i] < ths[minI+1]-ths[minI]) ) {
        //    minI = i;  minV = sum; 
        //  }
        //}

        // Merge two bins.  Classic bins merging by averaging the histogram
        // centers based on counts.
        long k0 = tps[minI  ]+fps[minI  ];
        long k1 = tps[minI+1]+fps[minI+1];
        double d = (ths[minI]*k0+ths[minI+1]*k1)/(k0+k1);
        // Setup the new merged bin at index minI
        ths[minI] = d;
        sqe[minI] = minSQE;
        tps[minI] += tps[minI+1];
        fps[minI] += fps[minI+1];
        // Slide over to crush the removed bin at index (minI+1)
        System.arraycopy(ths,minI+2,ths,minI+1,n-minI-2);
        System.arraycopy(sqe,minI+2,sqe,minI+1,n-minI-2);
        System.arraycopy(tps,minI+2,tps,minI+1,n-minI-2);
        System.arraycopy(fps,minI+2,fps,minI+1,n-minI-2);
        n--;
      }
      // Final results for this chunk
      _ths = ths;
      _sqe = sqe;
      _tps = tps;
      _fps = fps;
      _nBins = n;
    }

    @Override public void reduce( AUC_Impl auc ) {
      throw H2O.unimpl();
    }

  }
}
