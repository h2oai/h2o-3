package hex;

import hex.quantile.Quantile;
import hex.quantile.QuantileModel;
import water.DKV;
import water.Iced;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;

import java.util.Arrays;
import java.util.Iterator;
import java.util.TreeSet;


/**
 * Area under uplift curve
 */
public class AUUC extends Iced{
    public final int _nBins;  // Max number of bins; can be less if there are fewer points
    public final int _maxIdx;  // Threshold that maximizes the default criterion
    public final double[] _ths; // Thresholds
    public final long[] _treatment; // Treatments  
    public final long[] _control; // Controls
    public final long[] _yTreatment; // Treatment y==1
    public final long[] _yControl; // Control y==1
    public double[] _uplift;

    
    // Default bins, good answers on a highly unbalanced sorted (and reverse
    // sorted) datasets
    public static final int NBINS = 400;

    public final AUUCType _auucType;
    public double _auuc;

    public double threshold( int idx ) { return _ths[idx]; }
    public long treatment( int idx ) { return _treatment[idx]; }
    public long control( int idx ) { return _control[idx]; }
    public long yTreatment( int idx ) { return _yTreatment[idx]; }
    public long yControl( int idx ) { return _yControl[idx]; }
    public double uplift( int idx) { return _uplift[idx]; }
    
    public AUUC(Vec probs, Vec y, Vec uplift, AUUCType auucType) {
        this(NBINS, probs, y, uplift, auucType);
    }

    public AUUC(int nBins, Vec probs, Vec y, Vec uplift, AUUCType auucType) {
        this(new AUUCImpl(calculateQuantileThresholds(nBins, probs)).doAll(probs, y, uplift)._bldr, auucType);
    }

    public AUUC( AUUCBuilder bldr, AUUCType auucType) {
        this(bldr, true, auucType);
    }

    private AUUC( AUUCBuilder bldr, boolean trueProbabilities, AUUCType auucType) {
        // Copy result arrays into base object, shrinking to match actual bins
        System.out.println(bldr.toDebugString());
        _auucType = auucType;
        _nBins = bldr._nBins;
        assert _nBins >= 1 : "Must have >= 1 bins for AUUC calculation, but got " + _nBins;
        assert trueProbabilities || bldr._thresholds[_nBins - 1] == 1 : "Bins need to contain pred = 1 when 0-1 probabilities are used";
        _ths = Arrays.copyOf(bldr._thresholds,_nBins);
        _treatment = Arrays.copyOf(bldr._treatment,_nBins);
        _control = Arrays.copyOf(bldr._control,_nBins);
        _yTreatment = Arrays.copyOf(bldr._yTreatment,_nBins);
        _yControl = Arrays.copyOf(bldr._yControl,_nBins);
        _uplift = new double[_nBins];

        // Rollup counts, so that computing the rates are easier.
        long tmpt=0, tmpc=0, tmptp = 0, tmpcp = 0;
        for( int i=0; i<_nBins; i++ ) {
            tmpt += _treatment[i]; _treatment[i] = tmpt;
            tmpc += _control[i]; _control[i] = tmpc;
            tmptp += _yTreatment[i]; _yTreatment[i] = tmptp;
            tmpcp += _yControl[i]; _yControl[i] = tmpcp;
        }
        for( int i=0; i<_nBins; i++ ) {
            _uplift[i] = _auucType.exec(this, i);
        }
        
        ArrayUtils.interpolateLinear(_uplift);
        
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
        _treatment = new long[]{auuc._treatment[idx]};
        _control = new long[]{auuc._control[idx]};
        _yTreatment = new long[]{auuc._yTreatment[idx]};
        _yControl = new long[]{auuc._yControl[idx]};
        _auuc = auuc._auuc;
        _maxIdx = auuc._maxIdx >= 0 ? 0 : -1;
        _auucType = auuc._auucType;
    }

    AUUC() {
        _nBins = 0;
        _ths = new double[0];
        _treatment = _control = _yTreatment = _yControl = new long[0];
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
    
    public static double[] calculateQuantileThresholds(int groups, Vec preds) {
        Frame fr = null;
        QuantileModel qm = null;
        double[] quantiles;
        try {
            QuantileModel.QuantileParameters qp = new QuantileModel.QuantileParameters();
            fr = new Frame(Key.<Frame>make(), new String[]{"predictions"}, new Vec[]{preds});
            DKV.put(fr);
            qp._train = fr._key;
            assert groups > 0;
            qp._probs = new double[groups];
            for (int i = 0; i < groups; ++i) {
                qp._probs[i] = (groups - i - 1.) / groups; // This is 0.9, 0.8, 0.7, 0.6, ..., 0.1, 0 for 10 groups
            }
            qm = new Quantile(qp).trainModel().get();
            quantiles = qm._output._quantiles[0];
            // find uniques
            TreeSet<Double> hs = new TreeSet<>();
            for (double d : quantiles) hs.add(d);
            quantiles = new double[hs.size()];
            Iterator<Double> it = hs.descendingIterator();
            int i = 0;
            while (it.hasNext()) quantiles[i++] = it.next();
        } finally {
            if (qm != null) qm.remove();
            if (fr != null) DKV.remove(fr._key);
        }
        return quantiles;
    }
    
    
    private double computeArea(double threshold, double prevThreshold, double uplift, double prevUplift){
        return (threshold - prevThreshold) * (uplift + prevUplift) / 2.0; // Trapezoid
    }
    
    private double computeAuuc(){
        double area = 0;
        double up0 = 0, th0 = 0;
        for( int i=0; i<_nBins; i++ ) {
            double thres = _ths[i];
            double uplift = _uplift[i];
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
        final double[] _thresholds;
        AUUCBuilder _bldr;
        AUUCImpl( double[] thresholds) { _thresholds = thresholds; }
        @Override public void map(Chunk ps, Chunk actuals, Chunk uplift) {
            AUUCBuilder bldr = _bldr = new AUUCBuilder(_thresholds);
            for( int row = 0; row < ps._len; row++ )
                if( !ps.isNA(row) && !uplift.isNA(row) )
                    bldr.perRow(ps.atd(row),1, actuals.atd(row), uplift.atd(row));
        }
        @Override public void reduce( AUUCImpl auuc ) { _bldr.reduce(auuc._bldr); }
    }

    public static class AUUCBuilder extends Iced {
        final int _nBins;
        final double _thresholds[];        // Histogram bins, center
        final long _treatment[];        // Histogram bins, treatment cumsum
        final long _control[];        // Histogram bins, control cumsum
        final long _yTreatment[];        // Histogram bins, treatment prediction cumsum
        final long _yControl[];        // Histogram bins, control prediction cumsum
        // Merging this bin with the next gives the least increase in squared
        // error, or -1 if not known.  Requires a linear scan to find.
        int    _ssx;
        public AUUCBuilder(double[] thresholds) {
            int nBins = thresholds.length;
            _nBins = nBins;
            _thresholds = thresholds; // Threshold; also the mean for this bin
            _treatment = new long[nBins]; // treatment cumsum
            _control = new long[nBins]; // control cumsum
            _yTreatment = new long[nBins]; // treatment prediction cumsum 
            _yControl = new long[nBins]; // contol prediction cumsum
            _ssx = -1;                   // Unknown best merge bin
        }

        public void perRow(double pred, double w, double y, double uplift) {
            //for-loop is faster than binary search for small number of thresholds
            if(w == 0) {return;}
            for( int t=0; t < _thresholds.length; t++ ) {
                if (pred >= _thresholds[t] && (t == 0 || pred <_thresholds[t-1])) {
                    if(uplift == 1){
                        _treatment[t]++;
                        if(y == 1){ 
                            _yTreatment[t]++;
                        }
                    } else {
                        _control[t]++;
                        if(y == 1){
                            _yControl[t]++;
                        }
                    }
                    break;
                }
            }
        }

        public void reduce( AUUCBuilder bldr) {
            ArrayUtils.add(_treatment, bldr._treatment);
            ArrayUtils.add(_control, bldr._control);
            ArrayUtils.add(_yTreatment, bldr._yTreatment);
            ArrayUtils.add(_yControl, bldr._yControl);
        }

        private String toDebugString() {
            return "_ssx = " + _ssx +
                    "; nBins = " + _nBins +
                    "; ths = " + Arrays.toString(_thresholds) +
                    "; treatCumsum = " + Arrays.toString(_treatment) +
                    "; contrCumsum = " + Arrays.toString(_control) +
                    "; yTreatCumsum = " + Arrays.toString(_yTreatment) +
                    "; yContCumsum = " + Arrays.toString(_yControl);
        }
    }

    /** AUUC type enum
     *
     *  This is an Enum class, with an exec() function to compute the criteria
     *  from the basic parts, and from an AUUC at a given threshold index.
     */
    public enum AUUCType {
        AUTO() { @Override double exec(long treatment, long control, long yTreatment, long yControl) {
            return qini.exec(treatment, control, yTreatment, yControl);
        } },
        qini() { @Override double exec(long treatment, long control, long yTreatment, long yControl) {
            double norm = treatment / (double)control;
            return yTreatment - yControl * norm;
        } },
        lift() { @Override double exec(long treatment, long control, long yTreatment, long yControl) {
            return yTreatment / (double)treatment - yControl / (double)control;
        } },
        gain() { @Override double exec(long treatment, long control, long yTreatment, long yControl) {
            return lift.exec(treatment, control, yTreatment, yControl) * (double)(treatment + control);
        } };
        
        /** @param threshold
         *  @param control
         *  @param yTreatment
         *  @param yControl
         *  @return metric value */
        abstract double exec(long threshold, long control, long yTreatment, long yControl );
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



