package hex;

import hex.quantile.Quantile;
import hex.quantile.QuantileModel;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.PrettyPrint;
import water.util.TwoDimTable;

import java.util.Arrays;
import java.util.Iterator;
import java.util.TreeSet;

public class GainsUplift extends Iced {
    private double[] _quantiles;

    //INPUT
    public int _groups = 10;
    public Vec _labels;
    public Vec _preds; //of length N, n_i = N/GROUPS
    public Vec _weights;
    public Vec _uplift;

    //OUTPUT
    public long[] _nct1;
    public long[] _nct0;
    public long[] _ny1ct1;
    public long[] _ny1ct0;
    public double[] _qiniUplift;
    public double[] _liftUplift;
    public double[] _gainUplift;
    TwoDimTable table;

    public GainsUplift(Vec preds, Vec labels, Vec uplift) {
        this(preds, labels, null, uplift);
    }
    public GainsUplift(Vec preds, Vec labels, Vec weights, Vec uplift) {
        _preds = preds;
        _labels = labels;
        _weights = weights;
        _uplift = uplift;
    }

    private void init(Job job) throws IllegalArgumentException {
        _labels = _labels.toCategoricalVec();
        if( _labels ==null || _preds ==null )
            throw new IllegalArgumentException("Missing actualLabels or predictedProbs!");
        if (_labels.length() != _preds.length())
            throw new IllegalArgumentException("Both arguments must have the same length ("+ _labels.length()+"!="+ _preds.length()+")!");
        if (!_labels.isInt())
            throw new IllegalArgumentException("Actual column must be integer class labels!");
        if (_labels.cardinality() != -1 && _labels.cardinality() != 2)
            throw new IllegalArgumentException("Actual column must contain binary class labels, but found cardinality " + _labels.cardinality() + "!");
        if (_preds.isCategorical())
            throw new IllegalArgumentException("Predicted probabilities cannot be class labels, expect probabilities.");
        if (_weights != null && !_weights.isNumeric())
            throw new IllegalArgumentException("Observation weights must be numeric.");
        if (_uplift == null || !_uplift.isCategorical() || _uplift.cardinality() != 2)
            throw new IllegalArgumentException("Uplift values must be defined and must be categorical with cardinality size 2.");

        // The vectors are from different groups => align them, but properly delete it after computation
        if (!_labels.group().equals(_preds.group())) {
            _preds = _labels.align(_preds);
            Scope.track(_preds);
            _uplift = _labels.align(_uplift);
            Scope.track(_uplift);
            if (_weights != null) {
                _weights = _labels.align(_weights);
                Scope.track(_weights);
            }
        }

        boolean fast = false;
        if (fast) {
            // FAST VERSION: single-pass, only works with the specific pre-computed quantiles from rollupstats
            assert(_groups == 10);
            assert(Arrays.equals(Vec.PERCENTILES,
                    //             0      1    2    3    4     5        6          7    8   9   10          11    12   13   14    15, 16
                    new double[]{0.001, 0.01, 0.1, 0.2, 0.25, 0.3,    1.0 / 3.0, 0.4, 0.5, 0.6, 2.0 / 3.0, 0.7, 0.75, 0.8, 0.9, 0.99, 0.999}));
            //HACK: hardcoded quantiles for simplicity (0.9,0.8,...,0.1,0)
            double[] rq = _preds.pctiles(); //might do a full pass over the Vec
            _quantiles = new double[]{
                    rq[14], rq[13], rq[11], rq[9], rq[8], rq[7], rq[5], rq[3], rq[2], 0 /*ignored*/
            };
        } else {
            // ACCURATE VERSION: multi-pass
            Frame fr = null;
            QuantileModel qm = null;
            try {
                QuantileModel.QuantileParameters qp = new QuantileModel.QuantileParameters();
                if (_weights==null) {
                    fr = new Frame(Key.<Frame>make(), new String[]{"predictions"}, new Vec[]{_preds});
                } else {
                    fr = new Frame(Key.<Frame>make(), new String[]{"predictions", "weights"}, new Vec[]{_preds, _weights});
                    qp._weights_column = "weights";
                }
                DKV.put(fr);
                qp._train = fr._key;
                if (_groups > 0) {
                    qp._probs = new double[_groups];
                    for (int i = 0; i < _groups; ++i) {
                        qp._probs[i] = (_groups - i - 1.) / _groups; // This is 0.9, 0.8, 0.7, 0.6, ..., 0.1, 0 for 10 groups
                    }
                } else {
                    qp._probs = new double[]{0.99, 0.98, 0.97, 0.96, 0.95, 0.9, 0.85, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3, 0.2, 0.1, 0};
                }
                qm = job != null && !job.isDone() ? new Quantile(qp, job).trainModelNested(null) : new Quantile(qp).trainModel().get();
                _quantiles = qm._output._quantiles[0];
                // find uniques (is there a more elegant way?)
                TreeSet<Double> hs = new TreeSet<>();
                for (double d : _quantiles) hs.add(d);
                _quantiles = new double[hs.size()];
                Iterator<Double> it = hs.descendingIterator();
                int i = 0;
                while (it.hasNext()) _quantiles[i++] = it.next();
            } finally {
                if (qm!=null) qm.remove();
                if (fr!=null) DKV.remove(fr._key);
            }
        }
    }

    public void exec() {
        exec(null);
    }
    public void exec(Job job) {
        Scope.enter();
        init(job); //check parameters and obtain _quantiles from _preds
        try {
            GainsUpliftBuilder gt = new GainsUpliftBuilder(_quantiles);
            gt = (_weights != null) ? gt.doAll(_labels, _preds, _weights, _uplift) : gt.doAll(_labels, _preds, _uplift);
            _nct1 = gt.nct1();
            _nct0 = gt.nct0();
            _ny1ct1 = gt.ny1ct1();
            _ny1ct0 = gt.ny1ct0();
            int n = _nct1.length;
            _qiniUplift = new double[n];
            _liftUplift = new double[n];
            _gainUplift = new double[n];
            for(int i=0; i<_nct1.length; i++){
                _qiniUplift[i] = AUUC.AUUCType.qini.exec(_nct1[i], _nct0[i], _ny1ct1[i], _ny1ct0[i]);
                _liftUplift[i] = AUUC.AUUCType.lift.exec(_nct1[i], _nct0[i], _ny1ct1[i], _ny1ct0[i]);
                _gainUplift[i] = AUUC.AUUCType.gain.exec(_nct1[i], _nct0[i], _ny1ct1[i], _ny1ct0[i]); 
            }
        } finally {      
            Scope.exit();
        }
    }

    @Override public String toString() {
        TwoDimTable t = createTwoDimTable();
        return t==null ? "" : t.toString();
    }

    public TwoDimTable createTwoDimTable() {
        TwoDimTable table = new TwoDimTable(
                "GainsUplift Table",
                "",
                new String[_nct1.length],
                new String[]{"Group", "Data Fraction", "nct1", "nct0", "ny1ct1", "ny1ct0", "relative ny1ct1", "relative ny1ct1", "qini", "lift", "gain"},
                new String[]{"int", "long", "long", "long", "long", "long", "double", "double", "double", "double", "double"},
                new String[]{"%d", "%5f", "%5f", "%5f", "%5f", "%5f", "%5f", "%5f", "%5f", "%5f", "%5f"},
                null);
        for (int i = 0; i < _nct1.length; ++i) {
            long nct1i = _nct1[i];
            long nct0i = _nct0[i];
            long ny1ct1i = _ny1ct1[i];
            long ny1ct0i = _ny1ct0[i];
            double sum = nct0i+nct1i;
            double ny1ct1iR = (double)ny1ct1i/nct1i;
            double ny1ct0iR = (double)ny1ct0i/nct0i;
            table.set(i,0,i+1); //group
            table.set(i,1, sum); // data fraction
            table.set(i,2, nct1i); 
            table.set(i,3, nct0i); 
            table.set(i,4, ny1ct1i); 
            table.set(i,5, ny1ct0i); 
            table.set(i,6, ny1ct1iR); 
            table.set(i,7,ny1ct0iR);
            table.set(i,8, _qiniUplift[i]);
            table.set(i,9, _gainUplift[i]);
            table.set(i,10, _liftUplift[i]);
        }
        return this.table = table;
    }

    // Compute Gains table via MRTask
    public static class GainsUpliftBuilder extends MRTask<GainsUpliftBuilder> {
        /* @OUT response_rates */
        public final long[] nct1() { return _nct1; }
        public final long[] nct0(){ return _nct0; }
        public final long[] ny1ct1(){ return _ny1ct1; }
        public final long[] ny1ct0() { return _ny1ct0; }

        /* @IN quantiles/thresholds */
        final private double[] _thresh;

        private long[] _nct1;
        private long[] _nct0;
        private long[] _ny1ct1;
        private long[] _ny1ct0;

        public GainsUpliftBuilder(double[] thresh) {
            _thresh = thresh.clone();
            _nct1 = new long[_thresh.length];
            _nct0 = new long[_thresh.length];
            _ny1ct1 = new long[_thresh.length];
            _ny1ct0 = new long[_thresh.length];
        }

        @Override public void map( Chunk ca, Chunk cp, Chunk cu) { map(ca, cp, (Chunk)null, cu); }
        @Override public void map( Chunk ca, Chunk cp, Chunk cw, Chunk cu) {
            _nct1 = new long[_thresh.length];
            _nct0 = new long[_thresh.length];
            _ny1ct1 = new long[_thresh.length];
            _ny1ct0 = new long[_thresh.length];
            final int len = Math.min(ca._len, cp._len);
            for( int i=0; i < len; i++ ) {
                if (ca.isNA(i)) continue;
                final int a = (int)ca.at8(i);
                if (a != 0 && a != 1) throw new IllegalArgumentException("Invalid values in actualLabels: must be binary (0 or 1).");
                if (cp.isNA(i)) continue;
                final double pr = cp.atd(i);
                final double u = cu.atd(i);
                if (u != 0 && u != 1) throw new IllegalArgumentException("Invalid values in treatment column: must be binary (0 or 1).");
                // weights column is not supported right now
                final double w = 1;
                perRow(pr, a, w, u);
            }
        }

        public void perRow(double pr, int a, double w, double u) {
            if (w==0) return;
            assert (!Double.isNaN(pr));
            assert (!Double.isNaN(a));
            assert (!Double.isNaN(w));
            assert (!Double.isNaN(u));
            //for-loop is faster than binary search for small number of thresholds
            for( int t=0; t < _thresh.length; t++ ) {
                if (pr >= _thresh[t] && (t==0 || pr <_thresh[t-1])) {
                    if(u == 1) {
                        _nct1[t]++;
                        if (a == 1) {
                            _ny1ct1[t]++;
                        }
                    }
                    else {
                        _nct0[t]++;
                        if (a == 1) {
                            _ny1ct0[t]++;
                        }
                    }
                    break;
                }
            }
        }

        @Override public void reduce(GainsUpliftBuilder other) {
            ArrayUtils.add(_nct1, other._nct1);
            ArrayUtils.add(_nct0, other._nct0);
            ArrayUtils.add(_ny1ct1, other._ny1ct1);
            ArrayUtils.add(_ny1ct0, other._ny1ct0);
        }
    }
}
