package hex;

import water.Iced;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Vec;

import java.util.Arrays;


/**
 * Area under uplift curve
 */
public class AUUC extends Iced{
    public final int _nBins;  // Max number of bins; can be less if there are fewer points
    public final int _maxIdx;  // Threshold that maximizes the default criterion
    public final double[] _ths; // Thresholds
    public final double[] _treatment; // Treatments  
    public final double[] _control; // Controls
    public final double[] _yTreatment; // Treatment predictions
    public final double[] _yControl; // Control predictions

    
    // Default bins, good answers on a highly unbalanced sorted (and reverse
    // sorted) datasets
    public static final int NBINS = 400;

    public final AUUCType _auucType;
    public double _auuc;

    public double threshold( int idx ) { return _ths[idx]; }
    public double treatment( int idx ) { return _treatment[idx]; }
    public double control( int idx ) { return _control[idx]; }
    public double yTreatment( int idx ) { return _yTreatment[idx]; }
    public double yControl( int idx ) { return _yControl[idx]; }
    
    
    public AUUC(Vec probs, Vec y, Vec uplift, AUUCType auucType) { 
        this(NBINS, probs, y, uplift, auucType);
    }

    public AUUC(int nBins, Vec probs, Vec y, Vec uplift, AUUCType auucType) {
        this(new AUUCImpl(nBins).doAll(probs, y, uplift)._bldr, auucType);
    }

    public AUUC( AUUCBuilder bldr, AUUCType auucType) {
        this(bldr, true, auucType);
    }

    private AUUC( AUUCBuilder bldr, boolean trueProbabilities, AUUCType auucType) {
        // Copy result arrays into base object, shrinking to match actual bins
        System.out.println(bldr.toDebugString());
        _auucType = auucType;
        _nBins = bldr._n;
        assert _nBins >= 1 : "Must have >= 1 bins for AUUC calculation, but got " + _nBins;
        assert trueProbabilities || bldr._ths[_nBins - 1] == 1 : "Bins need to contain pred = 1 when 0-1 probabilities are used";
        _ths = Arrays.copyOf(bldr._ths,_nBins);
        _treatment = Arrays.copyOf(bldr._treatment,_nBins);
        _control = Arrays.copyOf(bldr._control,_nBins);
        _yTreatment = Arrays.copyOf(bldr._yTreatment,_nBins);
        _yControl = Arrays.copyOf(bldr._yContr,_nBins);
        // Reverse everybody; thresholds from 1 down to 0, easier to read
        for( int i=0; i<((_nBins)>>1); i++ ) {
            double tmp = _ths[i];  _ths[i] = _ths[_nBins-1-i]; _ths[_nBins-1-i] = tmp ;
            double tmpt = _treatment[i];  _treatment[i] = _treatment[_nBins-1-i]; _treatment[_nBins-1-i] = tmpt;
            double tmpc = _control[i];  _control[i] = _control[_nBins-1-i]; _control[_nBins-1-i] = tmpc;
            double tmptp = _yTreatment[i];  _yTreatment[i] = _yTreatment[_nBins-1-i]; _yTreatment[_nBins-1-i] = tmptp;
            double tmpcp = _yControl[i];  _yControl[i] = _yControl[_nBins-1-i]; _yControl[_nBins-1-i] = tmpcp;
        }

        // Rollup counts, so that computing the rates are easier.
        double tmpt=0, tmpc=0, tmptp = 0, tmpcp = 0;
        for( int i=0; i<_nBins; i++ ) {
            tmpt += _treatment[i]; _treatment[i] = tmpt;
            tmpc += _control[i]; _control[i] = tmpc;
            tmptp += _yTreatment[i]; _yTreatment[i] = tmptp;
            tmpcp += _yControl[i]; _yControl[i] = tmpcp;
        }
        
        if (trueProbabilities) {
            _auuc = computeAuuc();
            _maxIdx = 0;
        } else {
            _maxIdx = 0;
        }
    }

    private AUUC(AUUC auuc, int idx) {
        _nBins = 1;
        _ths = new double[]{auuc._ths[idx]};
        _treatment = new double[]{auuc._treatment[idx]};
        _control = new double[]{auuc._control[idx]};
        _yTreatment = new double[]{auuc._yTreatment[idx]};
        _yControl = new double[]{auuc._yControl[idx]};
        _auuc = auuc._auuc;
        _maxIdx = auuc._maxIdx >= 0 ? 0 : -1;
        _auucType = auuc._auucType;
    }

    AUUC() {
        _nBins = 0;
        _ths = _treatment = _control = _yTreatment = _yControl = new double[0];
        _auuc = Double.NaN;
        _maxIdx = -1;
        _auucType = AUUCType.AUTO;
    }


    /**
     * Creates a dummy AUUC instance with no metrics, meant to prevent possible NPEs
     * @return a valid AUUC instance
     */
    public static AUUC emptyAUUC() {
        return new AUUC();
    }

    private boolean isEmpty() {
        return _nBins == 0;
    }
    
    private double computeArea(double uplift, double prevUplift, double threshold, double prevThreshold){
        return (threshold - prevThreshold) * (uplift + prevUplift) / 2.0; // Trapezoid
    }
    
    private double computeAuuc(){
        double area = 0;
        double up0 = 0, th0 = 0;
        for( int i=0; i<_nBins; i++ ) {
            double thres = _ths[i];
            double uplift = _auucType.exec(treatment(i), control(i), yTreatment(i), yControl(i));
            if(Double.isNaN(uplift)) {
                uplift = 0;
            }
            area += computeArea(thres, th0, uplift, up0);
            up0 = uplift;  th0 = thres;
        }
        return area;
    }
    
    public double auuc(){
        return _auuc;
    }

    private static class AUUCImpl extends MRTask<AUUCImpl> {
        final int _nBins;
        AUUCBuilder _bldr;
        AUUCImpl( int nBins ) { _nBins = nBins; }
        @Override public void map(Chunk ps, Chunk actuals, Chunk uplift) {
            AUUCBuilder bldr = _bldr = new AUUCBuilder(_nBins);
            for( int row = 0; row < ps._len; row++ )
                if( !ps.isNA(row) && !uplift.isNA(row) )
                    bldr.perRow(ps.atd(row),1, actuals.atd(row), uplift.atd(row));
        }
        @Override public void reduce( AUUCImpl auuc ) { _bldr.reduce(auuc._bldr); }
    }

    public static class AUUCBuilder extends Iced {
        final int _nBins;
        int _n;                     // Current number of bins
        final double _ths[];        // Histogram bins, center
        final double _treatment[];        // Histogram bins, treatment cumsum
        final double _control[];        // Histogram bins, control cumsum
        final double _yTreatment[];        // Histogram bins, treatment prediction cumsum
        final double _yContr[];        // Histogram bins, control prediction cumsum
        // Merging this bin with the next gives the least increase in squared
        // error, or -1 if not known.  Requires a linear scan to find.
        int    _ssx;
        public AUUCBuilder(int nBins) {
            _nBins = nBins;
            _ths = new double[nBins<<1]; // Threshold; also the mean for this bin
            _treatment = new double[nBins<<1]; // treatment cumsum
            _control = new double[nBins<<1]; // control cumsum
            _yTreatment = new double[nBins<<1]; // treatment prediction cumsum 
            _yContr = new double[nBins<<1]; // contol prediction cumsum
            _ssx = -1;                   // Unknown best merge bin
        }

        public void perRow(double pred, double w, double y, double uplift) {
            // Insert the prediction into the set of histograms in sorted order, as
            // if its a new histogram bin with 1 count.
            assert !Double.isNaN(pred);
            assert !Double.isNaN(w) && !Double.isInfinite(w);
            int idx = Arrays.binarySearch(_ths,0,_n,pred);
            if( idx >= 0 ) {          // Found already in histogram; merge results
                if(uplift == 0) {
                    _control[idx]+= w;
                } else {
                    _treatment[idx]+= w;
                }
                if(y == 0){
                    _yContr[idx] += y * uplift ;
                } else {
                    _yTreatment[idx] += y * (1-uplift);
                }
                _ssx = -1;              // Blows the known best merge
                return;
            }
            idx = -idx-1;             // Get index to insert at

            // Must insert this point as it's own threshold (which is not insertion
            // point), either because we have too few bins or because we cannot
            // instantly merge the new point into an existing bin.
            if (idx == 0 || idx == _n ||     // Just because we didn't bother to deal with the corner cases ^^^
                    idx == _ssx) _ssx = -1;  // Smallest error becomes one of the splits
            else if( idx < _ssx ) _ssx++; // Smallest error will slide right 1

            // Slide over to do the insert.  Horrible slowness.
            System.arraycopy(_ths,idx,_ths,idx+1,_n-idx);
            System.arraycopy(_treatment, idx, _treatment,idx+1,_n-idx);
            System.arraycopy(_control,idx, _control,idx+1,_n-idx);
            // Insert into the histogram
            _ths[idx] = pred;         // New histogram center
            if(uplift == 0){
                _control[idx] = w;
                _treatment[idx] = 0;
            } else {
                _treatment[idx] = w;
                _control[idx] = 0;
            }
            _yContr[idx] = y * (1-uplift);
            _yTreatment[idx] = y * uplift;
            _n++;
            if( _n > _nBins )         // Merge as needed back down to nBins
                mergeOneBin();          // Merge best pair of bins
        }

        public void reduce( AUUCBuilder bldr ) {
            // Merge sort the 2 sorted lists into the double-sized arrays.  The tail
            // half of the double-sized array is unused, but the front half is
            // probably a source.  Merge into the back.
            int x=     _n-1;
            int y=bldr._n-1;
            while( x+y+1 >= 0 ) {
                boolean self_is_larger = y < 0 || (x >= 0 && _ths[x] >= bldr._ths[y]);
                AUUCBuilder b = self_is_larger ? this : bldr;
                int      idx = self_is_larger ?   x  :   y ;
                _ths[x+y+1] = b._ths[idx];
                _treatment[x+y+1] = b._treatment[idx];
                _control[x+y+1] = b._control[idx];
                _yTreatment[x+y+1] = b._yTreatment[idx];
                _yContr[x+y+1] = b._yContr[idx];
                if( self_is_larger ) x--; else y--;
            }
            _n += bldr._n;
            _ssx = -1; // We no longer know what bin has the smallest error

            // Merge elements with least squared-error increase until we get fewer
            // than _nBins and no duplicates.  May require many merges.
            while( _n > _nBins || dups() )
                mergeOneBin();
        }

        static double combine_centers(double ths1, double n1, double ths0, double n0) {
            double center = (ths0 * n0 + ths1 * n1) / (n0 + n1);
            if (Double.isNaN(center) || Double.isInfinite(center)) {
                // use a simple average as a fallback
                return (ths0 + ths1) / 2;
            }
            return center;
        }

        private void mergeOneBin( ) {
            // Too many bins; must merge bins.  Merge into bins with least total
            // squared error.  Horrible slowness linear arraycopy.
            int ssx = find_smallest();

            // Merge two bins.  Classic bins merging by averaging the histogram
            // centers based on counts.
            double k0 = k(ssx);
            double k1 = k(ssx+1);
            _ths[ssx] = combine_centers(_ths[ssx], k0, _ths[ssx+1], k1);
            _treatment[ssx] += _treatment[ssx+1];
            _control[ssx] += _control[ssx+1];
            _yTreatment[ssx] += _yTreatment[ssx+1];
            _yContr[ssx] += _yContr[ssx+1];
            // Slide over to crush the removed bin at index (ssx+1)
            System.arraycopy(_ths,ssx+2,_ths,ssx+1,_n-ssx-2);
            System.arraycopy(_treatment,ssx+2, _treatment,ssx+1,_n-ssx-2);
            System.arraycopy(_control,ssx+2, _control,ssx+1,_n-ssx-2);
            System.arraycopy(_yTreatment,ssx+2, _yTreatment,ssx+1,_n-ssx-2);
            System.arraycopy(_yContr,ssx+2, _yContr,ssx+1,_n-ssx-2);
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
            if( _ssx == -1 ) {
                _ssx = find_smallest_impl();
                assert _ssx != -1 : toDebugString();
            }
            return _ssx;
        }

        private String toDebugString() {
            return "_ssx = " + _ssx +
                    "; n = " + _n +
                    "; ths = " + Arrays.toString(_ths) +
                    "; treatCumsum = " + Arrays.toString(_treatment) +
                    "; contrCumsum = " + Arrays.toString(_control) +
                    "; yTreatCumsum = " + Arrays.toString(_yTreatment) +
                    "; yContCumsum = " + Arrays.toString(_yContr);
        }

        private int find_smallest_impl() {
            if (_n == 1)
                return 0;
            int n = _n;
            int minI = 0;
            double minDist = _ths[1] - _ths[0];
            for (int i = 1; i < n - 1; i++) {
                double dist = _ths[i + 1] - _ths[i];
                if (dist < minDist) {
                    minDist = dist;
                    minI = i;
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
            if (delta == 0)
                return 0;
            // Parallel equation drawn from:
            //  http://en.wikipedia.org/wiki/Algorithms_for_calculating_variance#Online_algorithm
            return delta*delta*n0*n1 / (n0+n1);
        }

        private double k( int idx ) { return _treatment[idx]+ _control[idx]; }
    }

    /** AUUC type enum
     *
     *  This is an Enum class, with an exec() function to compute the criteria
     *  from the basic parts, and from an AUUC at a given threshold index.
     */
    public enum AUUCType {
        AUTO() { @Override double exec(double treatment, double control, double yTreatment, double yControl) {
            return qini.exec(treatment, control, yTreatment, yControl);
        } },
        qini() { @Override double exec(double treatment, double control, double yTreatment, double yControl) {
            double norm =  control == 0 ? 1 : treatment / control;
            return treatment - control * norm;
        } },
        lift() { @Override double exec(double treatment, double control, double yTreatment, double yControl) {
            return yTreatment/treatment - yControl/control;
        } },
        gain() { @Override double exec(double treatment, double control, double yTreatment, double yControl) {
            return lift.exec(treatment, control, yTreatment, yControl) / (treatment + control);
        } };
        
        /** @param threshold
         *  @param control
         *  @param yThreshold
         *  @param yControl
         *  @return metric value */
        abstract double exec( double threshold, double control, double yThreshold, double yControl );
        public double exec( AUUC auc, int idx ) { return exec(auc.treatment(idx),auc.control(idx),auc.yTreatment(idx),auc.yControl(idx)); }

        public static final AUUCType[] VALUES = values();

        public static AUUCType fromString(String strRepr) {
            for (AUUCType tc : AUUCType.values()) {
                if (tc.toString().equalsIgnoreCase(strRepr)) {
                    return tc;
                }
            }
            return null;
        }
    }
}



