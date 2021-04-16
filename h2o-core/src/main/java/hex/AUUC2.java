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
public class AUUC2 extends Iced{
    public final int _nBins;  // Max number of bins; can be less if there are fewer points
    public final int _maxIdx;  // Threshold that maximizes the default criterion
    public final double[] _ths; // Thresholds
    public final long[] _treatment; // Treatments  
    public final long[] _control; // Controls
    public final long[] _yTreatment; // Treatment y==1
    public final long[] _yControl; // Control y==1
    public final long[] _frequency; // number of data in each bin
    public double[] _uplift;
    public final long _n;

    
    // Default bins, good answers on a highly unbalanced sorted (and reverse
    // sorted) datasets
    public static final int NBINS = 3500;

    public final AUUCType _auucType;
    public double _auuc;

    public double threshold( int idx ) { return _ths[idx]; }
    public long treatment( int idx ) { return _treatment[idx]; }
    public long control( int idx ) { return _control[idx]; }
    public long yTreatment( int idx ) { return _yTreatment[idx]; }
    public long yControl( int idx ) { return _yControl[idx]; }
    public long frequency( int idx ) { return _frequency[idx]; }
    public double uplift( int idx) { return _uplift[idx]; }
    
    public AUUC2(Vec probs, Vec y, Vec uplift, AUUCType auucType) {
        this(NBINS, probs, y, uplift, auucType);
    }

    public AUUC2(int nBins, Vec probs, Vec y, Vec uplift, AUUCType auucType) {
        this(new AUUCImpl(calculateQuantileThresholds(nBins, probs)).doAll(probs, y, uplift)._bldr, auucType);
    }

    public AUUC2(AUUCBuilder bldr, AUUCType auucType) {
        this(bldr, true, auucType);
    }

    private AUUC2(AUUCBuilder bldr, boolean trueProbabilities, AUUCType auucType) {
        // Copy result arrays into base object, shrinking to match actual bins
        System.out.println(bldr.toDebugString());
        _auucType = auucType;
        _nBins = bldr._nBins;
        assert _nBins >= 1 : "Must have >= 1 bins for AUUC calculation, but got " + _nBins;
        assert trueProbabilities || bldr._thresholds[_nBins - 1] == 1 : "Bins need to contain pred = 1 when 0-1 probabilities are used";
        _n = bldr._n;
        _ths = Arrays.copyOf(bldr._thresholds,_nBins);
        _treatment = Arrays.copyOf(bldr._treatment,_nBins);
        _control = Arrays.copyOf(bldr._control,_nBins);
        _yTreatment = Arrays.copyOf(bldr._yTreatment,_nBins);
        _yControl = Arrays.copyOf(bldr._yControl,_nBins);
        _frequency = Arrays.copyOf(bldr._frequency, _nBins);
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

    private AUUC2(AUUC2 auuc, int idx) {
        _nBins = 1;
        _n = auuc._n;
        _ths = new double[]{auuc._ths[idx]};
        _treatment = new long[]{auuc._treatment[idx]};
        _control = new long[]{auuc._control[idx]};
        _yTreatment = new long[]{auuc._yTreatment[idx]};
        _yControl = new long[]{auuc._yControl[idx]};
        _frequency = new long[]{auuc._frequency[idx]};
        _auuc = auuc._auuc;
        _maxIdx = auuc._maxIdx >= 0 ? 0 : -1;
        _auucType = auuc._auucType;
    }

    AUUC2() {
        _nBins = 0;
        _n = 0;
        _ths = new double[0];
        _treatment = _control = _yTreatment = _yControl = _frequency = new long[0];
        _auuc = Double.NaN;
        _maxIdx = -1;
        _auucType = AUUCType.AUTO;
    }
    
    /**
     * Creates a dummy AUUC instance with no metrics, meant to prevent possible NPEs
     * @return a valid AUUC instance
     */
    public static AUUC2 emptyAUUC() {
        return new AUUC2();
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
    
    private double computeAuuc(){
        double area = 0;
        for( int i = 0; i < _nBins; i++ ) {
            area += uplift(i) * frequency(i);
        }
        return area/_n;
    }
    
    public double auuc(){
        return _auuc;
    }

    private static class AUUCImpl extends MRTask<AUUCImpl> {
        final double[] _thresholds;
        AUUCBuilder _bldr;
        
        AUUCImpl(double[] thresholds) {
            _thresholds = thresholds; 
        }
        
        @Override public void map(Chunk ps, Chunk actuals, Chunk uplift) {
            AUUCBuilder bldr = _bldr = new AUUCBuilder(_thresholds);
            for( int row = 0; row < ps._len; row++ )
                if( !ps.isNA(row) && !uplift.isNA(row) )
                    bldr.perRow(ps.atd(row),1, actuals.atd(row), uplift.atd(row));
        }
        @Override public void reduce( AUUCImpl auuc ) { _bldr.reduce(auuc._bldr); }
    }

    /**
     * Builder to process input data to build histogram in parallel. This builder is used to calculate AUUC quickly.
     */
    public static class AUUCBuilder extends Iced {
        final int _nBins;
        final double[]_thresholds;      // thresholds
        final long[] _treatment;        // number of data from treatment group
        final long[] _control;          // number of data from control group
        final long[] _yTreatment;       // number of data from treatment group with prediction = 1 
        final long[] _yControl;         // number of data from control group with prediction = 1 
        final long[] _frequency;        // frequency of data in each bin
        long _n;
        public AUUCBuilder(double[] thresholds) {
            int nBins = thresholds.length;
            _nBins = nBins;
            _thresholds = thresholds;      
            _treatment = new long[nBins];  
            _control = new long[nBins];   
            _yTreatment = new long[nBins]; 
            _yControl = new long[nBins];  
            _frequency = new long[nBins];  
        }

        public void perRow(double pred, double w, double y, double uplift) {
            //TODO: for-loop or binary search? 
            if (w == 0) {return;}
            for(int t=0; t < _thresholds.length; t++) {
                if (pred >= _thresholds[t] && (t == 0 || pred <_thresholds[t-1])) {
                    _n++;
                    _frequency[t]++;
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
            _n += bldr._n;
            ArrayUtils.add(_treatment, bldr._treatment);
            ArrayUtils.add(_control, bldr._control);
            ArrayUtils.add(_yTreatment, bldr._yTreatment);
            ArrayUtils.add(_yControl, bldr._yControl);
            ArrayUtils.add(_frequency, bldr._frequency);
        }

        private String toDebugString() {
            return  "n =" +_n +
                    "; nBins = " + _nBins +
                    "; ths = " + Arrays.toString(_thresholds) +
                    "; treatCumsum = " + Arrays.toString(_treatment) +
                    "; contrCumsum = " + Arrays.toString(_control) +
                    "; yTreatCumsum = " + Arrays.toString(_yTreatment) +
                    "; yContCumsum = " + Arrays.toString(_yControl) +
                    "; frequency = " + Arrays.toString(_frequency);
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
        public double exec(AUUC2 auc, int idx ) { return exec(auc.treatment(idx),auc.control(idx),auc.yTreatment(idx),auc.yControl(idx)); }

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



