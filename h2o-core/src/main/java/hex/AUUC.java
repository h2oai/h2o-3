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
 * Object to calculate uplift curve and area under uplift curve
 */
public class AUUC extends Iced {
    public final int _nBins;              // max number of bins; can be less if there are fewer points
    public final int _maxIdx;             // id that maximize uplift
    public final double[] _ths;           // threshold of predictions created based on quantile computation
    public final long[] _treatment;       // treatments  
    public final long[] _control;         // controls
    public final long[] _yTreatment;      // treatment group and y==1
    public final long[] _yControl;        // control group and y==1
    public final long[] _frequency;       // number of data in each bin
    public final long[] _frequencyCumsum; // cumulative sum of frequency to plot AUUC
    public double[][] _uplift;            // output uplift values
    public double[][] _upliftRandom;      // output random uplift values
    public double[][] _upliftNormalized;  // output normalized uplift values
    public final long _n;                 // number of data
    
    public static final int NBINS = 1000;

    public final AUUCType _auucType;     // default auuc metric
    public final int _auucTypeIndx;      // default auuc metric index
    public double[] _auucs;              // areas under random uplif curve for all metrics
    public double[] _auucsRandom;        // areas under random uplift curve for all metrics
    public double[] _aecu;               // average excess cumulative uplift (auuc - auuc random)
    public double[] _auucsNormalized;     // normalized auuc
    
    public double threshold( int idx ) { return _ths[idx]; }
    public long treatment( int idx ) { return _treatment[idx]; }
    public long control( int idx ) { return _control[idx]; }
    public long yTreatment( int idx ) { return _yTreatment[idx]; }
    public long yControl( int idx ) { return _yControl[idx]; }
    public long frequency( int idx ) { return _frequency[idx]; }
    public double uplift( int idx) { return _uplift[_auucTypeIndx][idx]; }
    
    private int getIndexByAUUCType(AUUCType type){
        return ArrayUtils.find(AUUC.AUUCType.VALUES, type);
    }
    
    
    public double[] upliftByType(AUUCType type){
        int idx = getIndexByAUUCType(type);
        return idx < 0  ? null : _uplift[idx];
    }

    public double[] upliftNormalizedByType(AUUCType type){
        int idx = getIndexByAUUCType(type);
        return idx < 0  ? null : _upliftNormalized[idx];
    }
    
    public double[] upliftRandomByType(AUUCType type){
        int idx = getIndexByAUUCType(type);
        return idx < 0  ? null : _upliftRandom[idx];
    }

    public AUUC(Vec preds, Vec y, Vec uplift, AUUCType auucType, int nbins, double[] probs) {
        this(new AUUCImpl(calculateQuantileThresholds(probs, preds), nbins, probs).doAll(preds, y, uplift)._bldr, auucType);
    }

    public AUUC(AUUCBuilder bldr, AUUCType auucType) {
        this(bldr, true, auucType);
    }
    
    public AUUC(double[] customThresholds, Vec preds, Vec y, Vec uplift, AUUCType auucType, int nbins, double[] probs) {
        this(new AUUCImpl(customThresholds, nbins, probs).doAll(preds, y, uplift)._bldr, auucType);
    }

    public AUUC(AUUCBuilder bldr, boolean trueProbabilities, AUUCType auucType) {
        _auucType = auucType;
        _auucTypeIndx = getIndexByAUUCType(_auucType);
        _nBins = bldr._nbinsUsed;
        if (_nBins > 0) {
            _n = bldr._n;
            _ths = Arrays.copyOf(bldr._thresholds, _nBins);
            _treatment = Arrays.copyOf(bldr._treatment, _nBins);
            _control = Arrays.copyOf(bldr._control, _nBins);
            _yTreatment = Arrays.copyOf(bldr._yTreatment, _nBins);
            _yControl = Arrays.copyOf(bldr._yControl, _nBins);
            _frequency = Arrays.copyOf(bldr._frequency, _nBins);
            _frequencyCumsum = Arrays.copyOf(bldr._frequency, _nBins);
            _uplift = new double[AUUCType.values().length][_nBins];
            _upliftRandom = new double[AUUCType.values().length][_nBins];
            _upliftNormalized = new double[AUUCType.values().length][_nBins];

            // Rollup counts
            long tmpt = 0, tmpc = 0, tmptp = 0, tmpcp = 0, tmpf = 0;
            for (int i = 0; i < _nBins; i++) {
                tmpt += _treatment[i];
                _treatment[i] = tmpt;
                tmpc += _control[i];
                _control[i] = tmpc;
                tmptp += _yTreatment[i];
                _yTreatment[i] = tmptp;
                tmpcp += _yControl[i];
                _yControl[i] = tmpcp;
                tmpf += _frequencyCumsum[i];
                _frequencyCumsum[i] = tmpf;
            }

            System.out.println(Arrays.toString(_treatment));
            System.out.println(Arrays.toString(_control));

            // these methods need to be call in this order
            setUplift();
            setUpliftRandom();
            setUpliftNormalized();

            if (trueProbabilities) {
                _auucs = computeAuucs();
                _auucsRandom = computeAuucsRandom();
                _aecu = computeAecu();
                _auucsNormalized = computeAuucsNormalized();
                _maxIdx = _auucType.maxCriterionIdx(this);
            } else {
                _maxIdx = 0;
            }
        } else {
            _maxIdx = -1;
            _n = 0;
            _ths = null;
            _treatment = null;
            _control = null;
            _yTreatment = null;
            _yControl = null;
            _frequency = null;
            _frequencyCumsum = null;
            _uplift = null;
            _upliftRandom = null;
            _upliftNormalized = null;
        }
    }
    
    public void setUplift(){
        for(int i=0; i < AUUCType.VALUES.length; i++) {
            for (int j = 0; j < _nBins; j++) {
                double value = AUUCType.VALUES[i].exec(this, j);
                _uplift[i][j] = value;
            }
        }
        for(int i=0; i < AUUCType.VALUES.length; i++) {
            if (_uplift[i].length == 1 && Double.isNaN(_uplift[i][0])) {
                _uplift[i][0] = 0;
            } else {
                if (!Double.isNaN(_uplift[i][_uplift[i].length-1])) {
                    ArrayUtils.interpolateLinear(_uplift[i]);
                }
            }
        }
    }
    
    public void setUpliftRandom(){
        for(int i=0; i<AUUCType.VALUES.length; i++) {
            int maxIndex = _nBins-1;
            double a = _uplift[i][maxIndex]/_frequencyCumsum[maxIndex];
            for (int j = 0; j < _nBins; j++) {
                _upliftRandom[i][j] = a * _frequencyCumsum[j];
            }
        }
    }

    public void setUpliftNormalized(){
        for(int i=0; i<AUUCType.VALUES.length; i++) {
            int maxIndex = _nBins - 1;
            int liftIndex = getIndexByAUUCType(AUUCType.lift);
            double a = i == liftIndex || _uplift[i][maxIndex] == 0 ? 1 : Math.abs(_uplift[i][maxIndex]);
            for (int j = 0; j < _nBins; j++) {
                _upliftNormalized[i][j] = _uplift[i][j] / a;
            }
        }
    }

    public AUUC() {
        _nBins = 0;
        _n = 0;
        _ths = new double[0];
        _treatment = _control = _yTreatment = _yControl = _frequency = _frequencyCumsum = new long[0];
        _auucs = new double[AUUCType.VALUES.length];
        Arrays.fill(_auucs, Double.NaN);
        _auucsNormalized = new double[AUUCType.VALUES.length];
        Arrays.fill(_auucsNormalized, Double.NaN);
        _auucsRandom = new double[AUUCType.VALUES.length];
        Arrays.fill(_auucsRandom, Double.NaN);
        _aecu = new double[AUUCType.VALUES.length];
        Arrays.fill(_aecu, Double.NaN);
        _maxIdx = -1;
        _auucType = AUUCType.AUTO;
        _auucTypeIndx = getIndexByAUUCType(_auucType);
        _uplift = new double[AUUCType.values().length][];
        _upliftNormalized = new double[AUUCType.values().length][];
        _upliftRandom = new double[AUUCType.values().length][];
    }

    public AUUC(double[] ths, long[] freq, double[] auuc, double[] auucNorm, double[] auucRand, double[] aecu,
                AUUCType auucType, double[][] uplift, double[][] upliftNorm, double[][] upliftRand) {
        _nBins = ths.length;
        _n = freq[freq.length-1];
        _ths = ths;
        _frequencyCumsum = freq;
        _treatment = _control = _yTreatment = _yControl = _frequency = new long[0];
        _auucs = auuc;
        _auucsNormalized = auucNorm;
        _auucsRandom = auucRand;
        _aecu = aecu;
        _maxIdx = -1;
        _auucType = auucType;
        _auucTypeIndx = getIndexByAUUCType(_auucType);
        _uplift = uplift;
        _upliftNormalized = upliftNorm;
        _upliftRandom = upliftRand;
    }
    
    public static double[] calculateProbs(int groups){
        if (groups < 1)  return  null;
        double[] probs = new double[groups];
        for (int i = 0; i < groups; ++i) {
            probs[i] = (groups - i - 1.) / groups; // This is 0.9, 0.8, 0.7, 0.6, ..., 0.1, 0 for 10 groups
        }
        return probs;
    }
    
    public static double[] calculateQuantileThresholds(double[] probs, Vec preds) {
        Frame fr = null;
        QuantileModel qm = null;
        double[] quantiles;
        try {
            QuantileModel.QuantileParameters qp = new QuantileModel.QuantileParameters();
            qp._seed = 42;
            fr = new Frame(Key.<Frame>make(), new String[]{"predictions"}, new Vec[]{preds});
            DKV.put(fr);
            qp._train = fr._key;
            qp._probs = probs;
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
        if(quantiles == null){
            quantiles = new double[]{0};
        } else if(Double.isNaN(quantiles[0])){
            quantiles[0] = 0;
        }
        return quantiles;
    }

    private double[] computeAuucs(){
        return computeAuucs(_uplift);
    }

    private double[] computeAuucsRandom(){
        return computeAuucs(_upliftRandom);
    }
    
    private double[] computeAuucsNormalized() {
        return computeAuucs(_upliftNormalized);
    }

    private double[] computeAuucs(double[][] uplift){
        AUUCType[] auucTypes = AUUCType.VALUES;
        double[] auucs = new double[auucTypes.length];
        for(int i = 0; i < auucTypes.length; i++ ) {
            if(_n == 0){
                auucs[i] = Double.NaN;
            } else {
                double area = 0;
                for (int j = 0; j < _nBins; j++) {
                    area += uplift[i][j] * frequency(j);
                }
                auucs[i] = area / (_n + 1);
            }
        }
        return auucs;
    }
    
    private double[] computeAecu(){
        double[] aecu = new double[_auucs.length];
        for(int i = 0; i < _auucs.length; i++){
            aecu[i] = auuc(i) - auucRandom(i);
        }
        return aecu;
    }
    
    public double auucByType(AUUCType type){
        int idx = getIndexByAUUCType(type);
        return auuc(idx);
    }
    
    public double auucRandomByType(AUUCType type){
        int idx = getIndexByAUUCType(type);
        return auucRandom(idx);
    }
    
    public double aecuByType(AUUCType type){
        int idx = getIndexByAUUCType(type);
        return aecu(idx);
    }

    public double auucNormalizedByType(AUUCType type){
        int idx = getIndexByAUUCType(type);
        return auucNormalized(idx);
    }

    public double auuc (int idx){
        return _n == 0 ||  idx < 0 ? Double.NaN : _auucs[idx]; 
    }
    
    public double auuc(){ return auuc(_auucTypeIndx); }

    public double auucRandom(int idx){
        return _n == 0 ||  idx < 0 ? Double.NaN : _auucsRandom[idx];
    }

    public double auucRandom(){ return auucRandom(_auucTypeIndx); }
    
    public double aecu(int idx) { return _n == 0 || idx < 0 ? Double.NaN : _aecu[idx];}
    
    public double qini(){ return aecuByType(AUUCType.qini);}

    public double auucNormalized(int idx){ return _n == 0 || idx < 0 ? Double.NaN : _auucsNormalized[idx]; }

    public double auucNormalized(){ return auucNormalized(_auucTypeIndx); }

    public static class AUUCImpl extends MRTask<AUUCImpl> {

        final int _nbins;
        final double[] _thresholds;
        final double[] _probs;
        AUUCBuilder _bldr;
        
        public AUUCImpl(double[] thresholds, int nbins, double[] probs) {
            _thresholds = thresholds; 
            _nbins = nbins;
            _probs = probs;
        }
        
        @Override public void map(Chunk ps, Chunk actuals, Chunk treatment) {
            AUUCBuilder bldr = _bldr = new AUUCBuilder(_nbins, _thresholds, _probs);
            for( int row = 0; row < ps._len; row++ )
                if( !ps.isNA(row) && !treatment.isNA(row) )
                    bldr.perRow(ps.atd(row),1, actuals.atd(row), (float) treatment.atd(row));
        }
        @Override public void reduce( AUUCImpl auuc ) { _bldr.reduce(auuc._bldr); }
    }

    /**
     * Builder to process input data to build histogram in parallel. This builder is used to calculate AUUC quickly.
     */
    public static class AUUCBuilder extends Iced {
        final int _nbins;
        final double[] _thresholds;      // thresholds
        final long[] _treatment;        // number of data from treatment group
        final long[] _control;          // number of data from control group
        final long[] _yTreatment;       // number of data from treatment group with prediction = 1 
        final long[] _yControl;         // number of data from control group with prediction = 1 
        final long[] _frequency;        // frequency of data in each bin
        double[] _probs;
        int _n;                         // number of data
        int _nbinsUsed;                     // number of used bins
        int _ssx;
        
        public AUUCBuilder(int nbins, double[] thresholds, double[] probs) {
            _probs = probs;
            _nbins = nbins;
            _nbinsUsed = thresholds != null ? thresholds.length : 0;
            int l = nbins * 2; // maximal possible builder arrays length
            _thresholds = new double[l];
            if (thresholds != null) {
                System.arraycopy(thresholds, 0, _thresholds, 0, thresholds.length);
            }
            _probs = new double[l];
            System.arraycopy(probs, 0, _probs, 0, probs.length);
            System.arraycopy(probs, 0, _probs, probs.length-1, probs.length);
            _treatment = new long[l];
            _control = new long[l];
            _yTreatment = new long[l];
            _yControl = new long[l];
            _frequency = new long[l];
            _ssx = -1;
        }

        public void perRow(double pred, double w, double y, float treatment) {
            if (w == 0 || _thresholds == null) {return;}
            for(int t = 0; t < _thresholds.length; t++) {
                if (pred >= _thresholds[t] && (t == 0 || pred <_thresholds[t-1])) {
                    _n++;
                    _frequency[t]++;
                    if(treatment == 1){
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

        public void reduce(AUUCBuilder bldr) {
            if(bldr._nbinsUsed == 0) {return;}
            if(_nbinsUsed == 0 || _thresholds == bldr._thresholds){
                reduceSameOrNullThresholds(bldr);
            } else {
                reduceDifferentThresholds(bldr);
            }
        }

        /**
         *  Merge sort the 2 sorted lists into the double-sized arrays.  The tail
         *  half of the double-sized array is unused, but the front half is
         *  probably a source.  Merge into the back.
         * @param bldr AUUC builder to reduce
         */
        public void reduceDifferentThresholds(AUUCBuilder bldr){
            int x = _nbinsUsed -1;
            int y = bldr._nbinsUsed -1;
            while( x+y+1 >= 0 ) {
                boolean self_is_larger = y < 0 || (x >= 0 && _thresholds[x] >= bldr._thresholds[y]);
                AUUCBuilder b = self_is_larger ? this : bldr;
                int idx = self_is_larger ?   x  :   y ;
                _thresholds[x+y+1] = b._thresholds[idx];
                _treatment[x+y+1] = b._treatment[idx];
                _control[x+y+1] = b._control[idx];
                _yTreatment[x+y+1] = b._yTreatment[idx];
                _yControl[x+y+1] = b._yControl[idx];
                _frequency[x+y+1] = b._frequency[idx];
                _probs[x+y+1] = b._probs[idx];
                if( self_is_larger ) x--; else y--;
            }
            _n += bldr._n;
            _nbinsUsed += bldr._nbinsUsed;
            _ssx = -1;

            // Merge elements with least squared-error increase until we get fewer
            // than _nBins and no duplicates.  May require many merges.
            while( _nbinsUsed > _nbins || dups() )
                mergeOneBin();
        }
        
        public void reduceSameOrNullThresholds(AUUCBuilder bldr){
            _n += bldr._n;
            if(_nbinsUsed == 0) {
                ArrayUtils.add(_thresholds, bldr._thresholds);
                _nbinsUsed = bldr._nbinsUsed;
            }
            ArrayUtils.add(_treatment, bldr._treatment);
            ArrayUtils.add(_control, bldr._control);
            ArrayUtils.add(_yTreatment, bldr._yTreatment);
            ArrayUtils.add(_yControl, bldr._yControl);
            ArrayUtils.add(_frequency, bldr._frequency);
        }

        static double combineCenters(double ths1, double ths0, double probs, long nrows) {
            //double center = (ths0 * n0 + ths1 * n1) / (n0 + n1);
            double center = computeLinearInterpolation(ths1, ths0, nrows, probs);
            if (Double.isNaN(center) || Double.isInfinite(center)) {
                // use a simple average as a fallback
                return (ths0 + ths1) / 2;
            }
            return center;
        }

        private void mergeOneBin( ) {
            // Too many bins; must merge bins.  Merge into bins with least total
            // squared error.  Horrible slowness linear arraycopy.
            int ssx = findSmallest();

            // Merge two bins.  Classic bins merging by averaging the histogram
            // centers based on counts.
            _thresholds[ssx] = combineCenters(_thresholds[ssx], _thresholds[ssx+1], _probs[ssx], _n);
            _treatment[ssx] += _treatment[ssx+1];
            _control[ssx] += _control[ssx+1];
            _yTreatment[ssx] += _yTreatment[ssx+1];
            _yControl[ssx] += _yControl[ssx+1];
            _frequency[ssx] += _frequency[ssx+1];
            int n = _nbinsUsed == 2 ? _nbinsUsed - ssx -1 : _nbinsUsed - ssx -2;
            // Slide over to crush the removed bin at index (ssx+1)
            System.arraycopy(_thresholds,ssx+2,_thresholds,ssx+1,n);
            System.arraycopy(_treatment,ssx+2,_treatment,ssx+1,n);
            System.arraycopy(_control,ssx+2,_control,ssx+1,n);
            System.arraycopy(_yTreatment,ssx+2,_yTreatment,ssx+1,n);
            System.arraycopy(_yControl,ssx+2,_yControl,ssx+1,n);
            System.arraycopy(_frequency,ssx+2,_frequency,ssx+1,n);
            _nbinsUsed--;
            _ssx = -1;
        }

        /**
         *  Find the pair of bins that when combined give the smallest difference in thresholds
         * @return index of the bin where the threshold difference is the smallest
         */
        private int findSmallest() {
            if( _ssx == -1 ) {
                _ssx = findSmallestImpl();
                assert _ssx != -1 : toDebugString();
            }
            return _ssx;
        }

        private int findSmallestImpl() {
            if (_nbinsUsed == 1)
                return 0;
            int minI = 0;
            long n = _nbinsUsed;
            double minDist = _thresholds[1] - _thresholds[0];
            for (int i = 1; i < n - 1; i++) {
                double dist = _thresholds[i + 1] - _thresholds[i];
                if (dist < minDist) {
                    minDist = dist;
                    minI = i;
                }
            }
            return minI;
        }

        private boolean dups() {
            long n = _nbinsUsed;
            for( int i=0; i<n-1; i++ ) {
                double derr = computeDeltaError(_thresholds[i + 1], _frequency[i + 1], _thresholds[i], _frequency[i]);
                if (derr == 0) {
                    _ssx = i;
                    return true;
                }
            }
            return false;
        }

        /**
         *  If thresholds vary by less than a float ULP, treat them as the same.
         *  Parallel equation drawn from:
         *  http://en.wikipedia.org/wiki/Algorithms_for_calculating_variance#Online_algorithm
         * @return delta error from two thresholds
         */
        private double computeDeltaError(double ths1, double n1, double ths0, double n0 ) {
            double delta = (float)ths1-(float)ths0;
            if (delta == 0)
                return 0;
            return delta*delta*n0*n1 / (n0+n1);
        }

        private static double computeLinearInterpolation(double ths1, double ths0, double nrows, double prob) {
            double delta = ths1 - ths0;
            if (delta == 0)
                return 0;
            double row = (nrows-1) * prob;
            double plo = (row+0)/(nrows-1); // Note that row numbers are inclusive on the end point, means we need a -1
            double phi = (row+1)/(nrows-1);
            return ths0 + delta *(prob-plo)/(phi-plo);
        }
        
        private String toDebugString() {
            return  "n =" +_n +
                    "; nbins = " + _nbins +
                    "; nbinsUsed = " + _nbinsUsed +
                    "; ths = " + Arrays.toString(_thresholds) +
                    "; treatment = " + Arrays.toString(_treatment) +
                    "; contribution = " + Arrays.toString(_control) +
                    "; yTreatment = " + Arrays.toString(_yTreatment) +
                    "; yContribution = " + Arrays.toString(_yControl) +
                    "; frequency = " + Arrays.toString(_frequency);
        }
    }

    /** AUUC type enum
     *
     *  This is an Enum class, with an exec() function to compute the criteria
     *  from the basic parts, and from an AUUC at a given threshold index.
     */
    public enum AUUCType {
        AUTO() { 
            @Override 
            double exec(long treatment, long control, long yTreatment, long yControl) {
                return qini.exec(treatment, control, yTreatment, yControl);
            }
        },
        qini() { 
            @Override 
            double exec(long treatment, long control, long yTreatment, long yControl) {
                double norm = control > 0 ? treatment / (double)control : 1;
                return yTreatment - yControl * norm;
            }
        },
        lift() {
            @Override 
            double exec(long treatment, long control, long yTreatment, long yControl) {
                if (treatment > 0 && control > 0) {
                    return yTreatment / (double) treatment - yControl / (double) control;
                } else if (treatment < 0 && control > 0) {
                    return - (yControl / (double) control);
                } else if (treatment > 0 && control < 0) {
                    return yTreatment / (double) treatment;
                } else {
                    return Double.NaN;
                }
            }
        },
        gain() {
            @Override 
            double exec(long treatment, long control, long yTreatment, long yControl) {
                return lift.exec(treatment, control, yTreatment, yControl) * (double)(treatment + control);}
        };
        
        /** @param treatment
         *  @param control
         *  @param yTreatment
         *  @param yControl
         *  @return metric value */
        abstract double exec(long treatment, long control, long yTreatment, long yControl );
        public double exec(AUUC auc, int idx) { return exec(auc.treatment(idx),auc.control(idx),auc.yTreatment(idx),auc.yControl(idx)); }

        public static final AUUCType[] VALUES = values();

        public static final AUUCType[] VALUES_WITHOUT_AUTO = ArrayUtils.remove(values().clone(), ArrayUtils.find(AUUCType.values(), AUTO));

        public static String nameAuto(){
            return qini.name();
        }

        /** Convert a criterion into a threshold index that maximizes the criterion
         *  @return Threshold index that maximizes the criterion
         */
        public int maxCriterionIdx(AUUC auuc) {
            double md = -Double.MAX_VALUE;
            int mx = -1;
            for( int i=0; i<auuc._nBins; i++) {
                double d = exec(auuc,i);
                if( d > md ) {
                    md = d;
                    mx = i;
                }
            }
            return mx;
        }
    }
}



