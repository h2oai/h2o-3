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

    public AUUC(Vec probs, Vec y, Vec uplift, AUUCType auucType, int nbins) {
        this(new AUUCImpl(calculateQuantileThresholds(nbins, probs)).doAll(probs, y, uplift)._bldr, auucType);
    }

    public AUUC(AUUCBuilder bldr, AUUCType auucType) {
        this(bldr, true, auucType);
    }

    public AUUC(AUUCBuilder2 bldr, AUUCType auucType) {
        this(bldr, true, auucType);
    }

    public AUUC(double[] customThresholds, Vec probs, Vec y, Vec uplift, AUUCType auucType) {
        this(new AUUCImpl(customThresholds).doAll(probs, y, uplift)._bldr, auucType);
    }

    public AUUC(AUUCBuilder bldr, boolean trueProbabilities, AUUCType auucType) {
        _auucType = auucType;
        _auucTypeIndx = getIndexByAUUCType(_auucType);
        _nBins = bldr._nBins;
        //assert _nBins >= 1 : "Must have >= 1 bins for AUUC calculation, but got " + _nBins;
        if (_nBins > 0) {
            assert trueProbabilities || bldr._thresholds[_nBins - 1] == 1 : "Bins need to contain pred = 1 when 0-1 probabilities are used";
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

    public AUUC(AUUCBuilder2 bldr, boolean trueProbabilities, AUUCType auucType) {
        _auucType = auucType;
        _auucTypeIndx = getIndexByAUUCType(_auucType);
        _nBins = bldr._n;
        //assert _nBins >= 1 : "Must have >= 1 bins for AUUC calculation, but got " + _nBins;
        if (_nBins > 0) {
            assert trueProbabilities || bldr._thresholds[_nBins - 1] == 1 : "Bins need to contain pred = 1 when 0-1 probabilities are used";
            _n = bldr._frequency[_nBins-1];
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
        for(int i=0; i<AUUCType.VALUES.length; i++) {
            for (int j = 0; j < _nBins; j++) {
                _uplift[i][j] = AUUCType.VALUES[i].exec(this, j);
            }
        }
        for(int i=0; i<AUUCType.VALUES.length; i++) {
            if (_uplift[i].length == 1 && Double.isNaN(_uplift[i][0])) {
                _uplift[i][0] = 0;
            } else {
                ArrayUtils.interpolateLinear(_uplift[i]);
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
    
    public static double[] calculateQuantileThresholds(int groups, Vec preds) {
        Frame fr = null;
        QuantileModel qm = null;
        double[] quantiles;
        try {
            QuantileModel.QuantileParameters qp = new QuantileModel.QuantileParameters();
            qp._seed = 42;
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
        final double[] _thresholds;
        AUUCBuilder _bldr;
        
        public AUUCImpl(double[] thresholds) {
            _thresholds = thresholds; 
        }
        
        @Override public void map(Chunk ps, Chunk actuals, Chunk treatment) {
            AUUCBuilder bldr = _bldr = new AUUCBuilder(_thresholds);
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
        final int _nBins;
        final double[]_thresholds;      // thresholds
        final long[] _treatment;        // number of data from treatment group
        final long[] _control;          // number of data from control group
        final long[] _yTreatment;       // number of data from treatment group with prediction = 1 
        final long[] _yControl;         // number of data from control group with prediction = 1 
        final long[] _frequency;        // frequency of data in each bin
        long _n;
        int _ssx;
        
        public AUUCBuilder(double[] thresholds) {
            int nBins = thresholds.length;
            _nBins = nBins;
            _thresholds = thresholds;      
            _treatment = new long[nBins];  
            _control = new long[nBins];   
            _yTreatment = new long[nBins]; 
            _yControl = new long[nBins];  
            _frequency = new long[nBins];
            _ssx = -1;
        }

        public void perRow(double pred, double w, double y, float treatment) {
            if (w == 0) {return;}
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
                    "; treatment = " + Arrays.toString(_treatment) +
                    "; contribution = " + Arrays.toString(_control) +
                    "; yTreatment = " + Arrays.toString(_yTreatment) +
                    "; yContribution = " + Arrays.toString(_yControl) +
                    "; frequency = " + Arrays.toString(_frequency);
        }
    }

    /**
     * Builder to process input data to build histogram in parallel. This builder is used to calculate AUUC quickly.
     */
    public static class AUUCBuilder2 extends Iced {
        final int _nBins;
        final double[]_thresholds;      // thresholds
        final long[] _treatment;        // number of data from treatment group
        final long[] _control;          // number of data from control group
        final long[] _yTreatment;       // number of data from treatment group with prediction = 1 
        final long[] _yControl;         // number of data from control group with prediction = 1 
        final long[] _frequency;        // frequency of data in each bin
        int _n;
        int _ssx;

        public AUUCBuilder2(int nBins) {
            _nBins = nBins;
            int doubleNBins = 2 * nBins;
            _thresholds = new double[doubleNBins];
            _treatment = new long[doubleNBins];
            _control = new long[doubleNBins];
            _yTreatment = new long[doubleNBins];
            _yControl = new long[doubleNBins];
            _frequency = new long[doubleNBins];
            _ssx = -1;
        }

        public void perRow(double pred, double w, double y, float treatment) {
            // Insert the prediction into the set of histograms in sorted order, as
            // if its a new histogram bin with 1 count.
            assert !Double.isNaN(pred);
            assert !Double.isNaN(w) && !Double.isInfinite(w);
            int idx = Arrays.binarySearch(_thresholds,0,_n,pred);
            if( idx >= 0 ) {          // Found already in histogram; merge results
                _frequency[idx]++;
                if(treatment == 1){
                    _treatment[idx]++;
                    if(y == 1){
                        _yTreatment[idx]++;
                    }
                } else {
                    _control[idx]++;
                    if(y == 1){
                        _yControl[idx]++;
                    }
                }
                _ssx = -1;              // Blows the known best merge
                return;
            }
            idx = -idx-1;             // Get index to insert at

            // If already full bins, try to instantly merge into an existing bin
            if (_n == _nBins &&
                    idx > 0 && idx < _n &&       // Give up for the corner cases
                    _thresholds[idx - 1] != _thresholds[idx])  // Histogram has duplicates (mergeOneBin will get rid of them)
            {       // Need to merge to shrink things
                final int ssx = find_smallest();
                double dssx = compute_delta_error(_thresholds[ssx+1], _frequency[ssx+1], _thresholds[ssx], _frequency[ssx]);
                // See if this point will fold into either the left or right bin
                // immediately.  This is the desired fast-path.
                double d0 = compute_delta_error(pred,w,_thresholds[idx-1],_frequency[idx-1]);
                double d1 = compute_delta_error(_thresholds[idx], _frequency[idx],pred,w);
                if (d0 < dssx || d1 < dssx) {
                    if (d0 <= d1) idx--; // Pick correct bin
                    if (ssx == idx-1 || ssx == idx)
                        _ssx = -1;         // We don't know the minimum anymore
                    double k = _frequency[idx];
                    _frequency[idx]++;
                    if(treatment == 1){
                        _treatment[idx]++;
                        if(y == 1){
                            _yTreatment[idx]++;
                        }
                    } else {
                        _control[idx]++;
                        if(y == 1){
                            _yControl[idx]++;
                        }
                    }
                    _thresholds[idx] = combineCenters(_thresholds[idx], k, pred, w);
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
            System.arraycopy(_thresholds,idx,_thresholds,idx+1,_n-idx);
            System.arraycopy(_treatment,idx,_treatment,idx+1,_n-idx);
            System.arraycopy(_control,idx,_control,idx+1,_n-idx);
            System.arraycopy(_yTreatment,idx,_yTreatment,idx+1,_n-idx);
            System.arraycopy(_yControl,idx,_yControl,idx+1,_n-idx);
            System.arraycopy(_frequency,idx,_frequency,idx+1,_n-idx);
            // Insert into the histogram
            _thresholds[idx] = pred;         // New histogram center
            _frequency[idx]++;
            if(treatment == 1) {
                _treatment[idx]++;
                if(y == 1){
                    _yTreatment[idx]++;
                }
            } else {
                _control[idx]++;
                if(y == 1){
                    _yControl[idx]++;
                }
            }
            _n++;
            if( _n > _nBins ) {       // Merge as needed back down to nBins
                mergeOneBin(); // Merge best pair of bins
            }
        }

        public void reduce(AUUC.AUUCBuilder2 bldr) {
            // Merge sort the 2 sorted lists into the double-sized arrays.  The tail
            // half of the double-sized array is unused, but the front half is
            // probably a source.  Merge into the back.
            int x = _n-1;
            int y = bldr._n-1;
            while( x+y+1 >= 0 ) {
                boolean self_is_larger = y < 0 || (x >= 0 && _thresholds[x] >= bldr._thresholds[y]);
                AUUC.AUUCBuilder2 b = self_is_larger ? this : bldr;
                int idx = self_is_larger ?   x  :   y ;
                _thresholds[x+y+1] = b._thresholds[idx];
                _treatment[x+y+1] = b._treatment[idx];
                _control[x+y+1] = b._control[idx];
                _yTreatment[x+y+1] = b._yTreatment[idx];
                _yControl[x+y+1] = b._yControl[idx];
                _frequency[x+y+1] = b._frequency[idx];
                if( self_is_larger ) x--; else y--;
            }
            _n += bldr._n;

            // Merge elements with least squared-error increase until we get fewer
            // than _nBins and no duplicates.  May require many merges.
            while( _n > _nBins || dups() )
                mergeOneBin();
        }

        static double combineCenters(double ths1, double n1, double ths0, double n0) {
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
            double k0 = _frequency[ssx];
            double k1 = _frequency[ssx+1];
            _thresholds[ssx] = combineCenters(_thresholds[ssx], k0, _thresholds[ssx+1], k1);
            _treatment[ssx] += _treatment[ssx+1];
            _control[ssx] += _control[ssx+1];
            _yTreatment[ssx] += _yTreatment[ssx+1];
            _yControl[ssx] += _yControl[ssx+1];
            _frequency[ssx] += _frequency[ssx+1];
            int n = _n;
            // Slide over to crush the removed bin at index (ssx+1)
            System.arraycopy(_thresholds,ssx+2,_thresholds,ssx+1,n-ssx-2);
            System.arraycopy(_treatment,ssx+2,_treatment,ssx+1,n-ssx-2);
            System.arraycopy(_control,ssx+2,_control,ssx+1,n-ssx-2);
            System.arraycopy(_yTreatment,ssx+2,_yTreatment,ssx+1,n-ssx-2);
            System.arraycopy(_yControl,ssx+2,_yControl,ssx+1,n-ssx-2);
            System.arraycopy(_frequency,ssx+2,_frequency,ssx+1,n-ssx-2);
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

        private int find_smallest_impl() {
            if (_n == 1)
                return 0;
            // we couldn't find any bins to merge based on SE (the math can be producing Double.Infinity or Double.NaN)
            // revert to using a simple distance of the bin centers
            int minI = 0;
            long n = _n;
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
            long n = _n;
            for( int i=0; i<n-1; i++ ) {
                double derr = compute_delta_error(_thresholds[i+1],_frequency[i+1],_thresholds[i],_frequency[i]);
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

        private String toDebugString() {
            return  "n =" +_n +
                    "; nBins = " + _nBins +
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
                double norm = treatment / (double)control;
                return yTreatment - yControl * norm;
            }
        },
        lift() {
            @Override 
            double exec(long treatment, long control, long yTreatment, long yControl) {
                return yTreatment / (double) treatment - yControl / (double)control;
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



