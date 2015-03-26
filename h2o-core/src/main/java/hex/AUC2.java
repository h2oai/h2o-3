package hex;

import java.util.Arrays;
import water.*;
import water.fvec.*;

public class AUC2 extends Iced {
  int _nBins;                   // Number of bins
  double[] _ths;                // Thresholds
  long[] _tps;                  // True  Positives
  long[] _fps;                  // False Positives
  long _tp, _fp;                // True positive, false positive counts
  
  AUC2( Vec probs, Vec actls ) { this(128,probs,actls); }
  AUC2( int nBins, Vec probs, Vec actls ) { 
    _nBins = nBins;
    AUC_Impl auc = new AUC_Impl(nBins).doAll(probs,actls);
    // Copy result arrays into base object
    _ths = auc._ths;
    _tps = auc._tps;
    _fps = auc._fps;
    _nBins = nBins = auc._nBins;
    // Reverse everybody; thresholds from 1 down to 0, easier to read
    for( int i=0; i<((nBins)>>1); i++ ) {
      double tmp= _ths[i];  _ths[i] = _ths[nBins-1-i]; _ths[nBins-1-i] = tmp ;
      long tmpt = _tps[i];  _tps[i] = _tps[nBins-1-i]; _tps[nBins-1-i] = tmpt;
      long tmpf = _fps[i];  _fps[i] = _fps[nBins-1-i]; _fps[nBins-1-i] = tmpf;
    }

    // Rollup counts, so that computing the rates are easier.
    // The AUC is TPR/FPR as thresholds roll about
    long tp=0, fp=0;
    for( int i=0; i<nBins; i++ ) { 
      tp += _tps[i]; _tps[i] = tp;
      fp += _fps[i]; _fps[i] = fp;
    }
    _tp = tp;  _fp = fp;
  }


  // Compute an online histogram of the predicted probabilities, along with
  // true positive and false positive totals in each histogram bin.
  private static class AUC_Impl extends MRTask<AUC_Impl> {
    int _nBins;                 // Max Number of bins
    double _ths[];              // Histogram bins, center
    long   _tps[];              // Histogram bins, true  positives
    long   _fps[];              // Histogram bins, false positives
    AUC_Impl( int nBins ) { _nBins = nBins; }

    @Override public void map( Chunk ps, Chunk as ) {
      double ths[] = new double[_nBins+1];
      long   tps[] = new long  [_nBins+1];
      long   fps[] = new long  [_nBins+1];
      int n=0;
      for( int row = 0; row < ps._len; row++ ) {
        // Insert the prediction into the set of histograms in sorted order, as
        // if its a new histogram bin with 1 count.
        double pred  = ps.atd(row);
        int act = (int)as.at8(row); // Actual better be 0 or 1
        int idx = Arrays.binarySearch(ths,0,n,pred);
        if( idx >= 0 ) {        // Found already in histogram; merge results
          if( act==0 ) fps[idx]++; else tps[idx]++; // One more count
          continue;
        }
        // Slide over to do the insert.  Horrible slowness.
        idx = -idx-1;           // Get index to insert at
        System.arraycopy(ths,idx,ths,idx+1,n-idx);
        System.arraycopy(tps,idx,tps,idx+1,n-idx);
        System.arraycopy(fps,idx,fps,idx+1,n-idx);
        // Insert into the histogram
        ths[idx] = pred;
        if( act==0 ) fps[idx] = 1; else tps[idx] = 1;
        n++;
        if( n <= _nBins ) continue; // No need to merge bins

        // Too many bins; must merge bins.  Find smallest bin difference and
        // merge these two bins.  Horrible slowness linear scan.
        double minV = Double.MAX_VALUE;
        int minI = -1;
        for( int i=0; i<_nBins; i++ ) {
          double diff = ths[i+1] - ths[i];
          if( diff < minV ) { minI = i; minV = diff; }
        }

        // Merge two bins with smallest distance between them.  Classic bins
        // merging by averaging the histogram centers based on counts.
        long k0 = tps[minI  ]+fps[minI  ];
        long k1 = tps[minI+1]+fps[minI+1];
        long k = k0+k1;
        double d = (ths[minI]*k0+ths[minI+1]*k1)/k;
        // Setup the new merged bin at index minI
        ths[minI] = d;
        tps[minI] += tps[minI+1];
        fps[minI] += fps[minI+1];
        // Slide over to crush the removed bin at index (minI+1)
        System.arraycopy(ths,minI+2,ths,minI+1,n-minI-2);
        System.arraycopy(tps,minI+2,tps,minI+1,n-minI-2);
        System.arraycopy(fps,minI+2,fps,minI+1,n-minI-2);
        n--;
      }
      // Final results for this chunk
      _ths = ths;
      _tps = tps;
      _fps = fps;
      _nBins = n;
    }

    @Override public void reduce( AUC_Impl auc ) {
      throw H2O.unimpl();
    }

  }

  private static class Bin extends Iced implements Comparable { 
    public double _th; public long _tp, _fp; 
    Bin( double th, boolean act ) { _th = th; if( act ) _tp=1; else _fp=1; }
    @Override public int compareTo( Object b ) {
      if( b==this ) return 0;
      return Double.compare(_th,((Bin)b)._th);
    }
  }
}

