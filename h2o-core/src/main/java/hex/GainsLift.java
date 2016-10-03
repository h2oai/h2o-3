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

public class GainsLift extends Iced {
  private double[] _quantiles;

  //INPUT
  public int _groups = -1;
  public Vec _labels;
  public Vec _preds; //of length N, n_i = N/GROUPS
  public Vec _weights;

  //OUTPUT
  public double[] response_rates; // p_i = e_i/n_i
  public double avg_response_rate; // P
  public long[] events; // e_i
  public long[] observations; // n_i
  TwoDimTable table;

  public GainsLift(Vec preds, Vec labels) {
    this(preds, labels, null);
  }
  public GainsLift(Vec preds, Vec labels, Vec weights) {
    _preds = preds;
    _labels = labels;
    _weights = weights;
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

    // The vectors are from different groups => align them, but properly delete it after computation
    if (!_labels.group().equals(_preds.group())) {
      _preds = _labels.align(_preds);
      Scope.track(_preds);
      if (_weights !=null) {
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
        qm = job != null && !job.isDone() ? new Quantile(qp, job).trainModelNested() : new Quantile(qp).trainModel().get();
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
      GainsLiftBuilder gt = new GainsLiftBuilder(_quantiles);
      gt = (_weights != null) ? gt.doAll(_labels, _preds, _weights) : gt.doAll(_labels, _preds);
      response_rates = gt.response_rates();
      avg_response_rate = gt.avg_response_rate();
      events = gt.events();
      observations = gt.observations();
    } finally {       // Delete adaptation vectors
      Scope.exit();
    }
  }

  @Override public String toString() {
    TwoDimTable t = createTwoDimTable();
    return t==null ? "" : t.toString();
  }

  public TwoDimTable createTwoDimTable() {
    if (response_rates == null || Double.isNaN(avg_response_rate)) return null;
    TwoDimTable table = new TwoDimTable(
            "Gains/Lift Table",
            "Avg response rate: " + PrettyPrint.formatPct(avg_response_rate),
            new String[events.length],
            new String[]{"Group", "Cumulative Data Fraction", "Lower Threshold", "Lift", "Cumulative Lift", "Response Rate", "Cumulative Response Rate", "Capture Rate", "Cumulative Capture Rate", "Gain", "Cumulative Gain"},
            new String[]{"int", "double", "double", "double", "double", "double", "double", "double", "double", "double", "double"},
            new String[]{"%d", "%.8f", "%5f", "%5f", "%5f", "%5f", "%5f", "%5f", "%5f", "%5f", "%5f"},
            "");
    long sum_e_i = 0;
    long sum_n_i = 0;
    double P = avg_response_rate; // E/N
    long N = ArrayUtils.sum(observations);
    long E = Math.round(N * P);
    for (int i = 0; i < events.length; ++i) {
      long e_i = events[i];
      long n_i = observations[i];
      double p_i = response_rates[i];
      sum_e_i += e_i;
      sum_n_i += n_i;
      double lift=p_i/P; //can be NaN if P==0
      double sum_lift=(double)sum_e_i/sum_n_i/P; //can be NaN if P==0
      table.set(i,0,i+1); //group
      table.set(i,1,(double)sum_n_i/N); //cumulative_data_fraction
      table.set(i,2,_quantiles[i]); //lower_threshold
      table.set(i,3,lift); //lift
      table.set(i,4,sum_lift); //cumulative_lift
      table.set(i,5,p_i); //response_rate
      table.set(i,6,(double)sum_e_i/sum_n_i); //cumulative_response_rate
      table.set(i,7,(double)e_i/E); //capture_rate
      table.set(i,8,(double)sum_e_i/E); //cumulative_capture_rate
      table.set(i,9,100*(lift-1)); //gain
      table.set(i,10,100*(sum_lift-1)); //cumulative gain
      if (i== events.length-1) {
        assert(sum_n_i == N) : "Cumulative data fraction must be 1.0, but is " + (double)sum_n_i/N;
        assert(sum_e_i == E) : "Cumulative capture rate must be 1.0, but is " + (double)sum_e_i/E;
        if (!Double.isNaN(sum_lift)) assert(Math.abs(sum_lift - 1.0) < 1e-8) : "Cumulative lift must be 1.0, but is " + sum_lift;
        assert(Math.abs((double)sum_e_i/sum_n_i - avg_response_rate) < 1e-8) : "Cumulative response rate must be " + avg_response_rate + ", but is " + (double)sum_e_i/sum_n_i;
      }
    }
    return this.table = table;
  }

  // Compute Gains table via MRTask
  public static class GainsLiftBuilder extends MRTask<GainsLiftBuilder> {
    /* @OUT response_rates */
    public final double[] response_rates() { return _response_rates; }
    public final double avg_response_rate() { return _avg_response_rate; }
    public final long[] events(){ return _events; }
    public final long[] observations(){ return _observations; }

    /* @IN quantiles/thresholds */
    final private double[] _thresh;

    private long[] _events;
    private long[] _observations;
    private long _avg_response;
    private double _avg_response_rate;
    private double[] _response_rates;

    public GainsLiftBuilder(double[] thresh) {
      _thresh = thresh.clone();
    }

    @Override public void map( Chunk ca, Chunk cp) { map(ca,cp,null); }
    @Override public void map( Chunk ca, Chunk cp, Chunk cw) {
      _events = new long[_thresh.length];
      _observations = new long[_thresh.length];
      _avg_response = 0;
      final int len = Math.min(ca._len, cp._len);
      for( int i=0; i < len; i++ ) {
        if (ca.isNA(i)) continue;
        final int a = (int)ca.at8(i);
        if (a != 0 && a != 1) throw new IllegalArgumentException("Invalid values in actualLabels: must be binary (0 or 1).");
        if (cp.isNA(i)) continue;
        final double pr = cp.atd(i);
        final double w = cw!=null?cw.atd(i):1;
        perRow(pr, a, w);
      }
    }

    public void perRow(double pr, int a, double w) {
      if (w==0) return;
      assert (!Double.isNaN(pr));
      assert (!Double.isNaN(a));
      assert (!Double.isNaN(w));
      //for-loop is faster than binary search for small number of thresholds
      for( int t=0; t < _thresh.length; t++ ) {
        if (pr >= _thresh[t] && (t==0 || pr <_thresh[t-1])) {
          _observations[t]+=w;
          if (a == 1) _events[t]+=w;
          break;
        }
      }
      if (a == 1) _avg_response+=w;
    }

    @Override public void reduce(GainsLiftBuilder other) {
      ArrayUtils.add(_events, other._events);
      ArrayUtils.add(_observations, other._observations);
      _avg_response += other._avg_response;
    }

    @Override public void postGlobal(){
      _response_rates = new double[_thresh.length];
      for (int i=0; i<_response_rates.length; ++i)
        _response_rates[i] = _observations[i] == 0 ? 0 : (double) _events[i] / _observations[i];
      _avg_response_rate = (double)_avg_response / ArrayUtils.sum(_observations);
    }
  }
}
