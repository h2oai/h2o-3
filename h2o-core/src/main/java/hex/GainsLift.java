package hex;

import water.*;
import water.fvec.Chunk;
import water.fvec.Vec;
import water.util.MathUtils;
import water.util.PrettyPrint;
import water.util.TwoDimTable;

import java.util.Arrays;

public class GainsLift extends Iced {
  private static int GROUPS = 10;
  private double[] _quantiles;

  //INPUT
  public Vec _labels;
  public Vec _preds;
  public Vec _weights;

  //OUTPUT
  public float[] response_rates;
  public float avg_response_rate;
  public long[] positive_responses;
  TwoDimTable table;

  public GainsLift(Vec preds, Vec labels) {
    _preds = preds;
    _labels = labels;
  }

  private void init() throws IllegalArgumentException {
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
      throw new IllegalArgumentException("predictedProbs cannot be class labels, expect probabilities.");
    if (_weights != null && !_weights.isNumeric())
      throw new IllegalArgumentException("weight must be numeric.");

    // The vectors are from different groups => align them, but properly delete it after computation
    if (!_labels.group().equals(_preds.group())) {
      _preds = _labels.align(_preds);
      Scope.track(_preds._key);
      if (_weights !=null) {
        _weights = _labels.align(_weights);
        Scope.track(_weights._key);
      }
    }
    //get specific pre-computed quantiles (deciles) from rollupstats
    //First check that rollups still uses the convention we rely on (hardcoded indexing below)
    assert(Arrays.equals(Vec.PERCENTILES,
            //             0      1    2    3    4     5        6          7    8   9   10          11    12   13   14    15, 16
            new double[]{0.001, 0.01, 0.1, 0.2, 0.25, 0.3,    1.0 / 3.0, 0.4, 0.5, 0.6, 2.0 / 3.0, 0.7, 0.75, 0.8, 0.9, 0.99, 0.999}));
    //HACK: hardcoded quantiles for simplicity (0.9,0.8,...,0.1,0)
    double[] rq = _preds.pctiles(); //might do a full pass over the Vec
    _quantiles = new double[]{
            rq[14], rq[13], rq[11], rq[9], rq[8], rq[7], rq[5], rq[3], rq[2], 0 /*ignored*/
    };
  }

  public void exec() {
    Scope.enter();
    init(); //check parameters and obtain _quantiles from _preds
    try {
      GainsLiftBuilder gt = new GainsLiftBuilder(_quantiles, _labels.length());
      gt = (_weights != null) ? gt.doAll(_labels, _preds, _weights) : gt.doAll(_labels, _preds);
      response_rates = gt.response_rates();
      avg_response_rate = gt.avg_response_rate();
      positive_responses = gt.responses();
    } finally {       // Delete adaptation vectors
      Scope.exit();
    }
  }

  @Override public String toString() { return createTwoDimTable().toString(); }

  public TwoDimTable createTwoDimTable() {
    if (response_rates == null) return null;
    TwoDimTable table = new TwoDimTable(
            "Gains/Lift Table",
            "Avg response rate: " + PrettyPrint.formatPct(avg_response_rate),
            new String[GROUPS],
            new String[]{"Decile", "Response rate", "Lift", "Cumulative Lift"},
            new String[]{"int", "double", "double", "double"},
            new String[]{"%d", "%5f", "%5f", "%5f"},
            "");
    float cumulativelift = 0;
    for (int i = 0; i < GROUPS; ++i) {
      table.set(i,0,i+1);
      table.set(i,1,response_rates[i]);
      final float lift = response_rates[i]/ avg_response_rate;
      cumulativelift += lift/ GROUPS;
      table.set(i,2,lift);
      table.set(i,3,cumulativelift);
    }
    return this.table = table;
  }

  // Compute Gains table via MRTask
  static class GainsLiftBuilder extends MRTask<GainsLiftBuilder> {
    /* @OUT response_rates */
    public final float[] response_rates() { return _response_rates; }
    public final float avg_response_rate() { return _avg_response_rate; }
    public final long[] responses(){ return _responses; }

    /* @IN total count of events */ final private double[] _thresh;
    final private long _count;

    private long[] _responses;
    private long _avg_response;
    private float _avg_response_rate;
    private float[] _response_rates;

    GainsLiftBuilder(double[] thresh, long count) {
      _thresh = thresh.clone();
      _count = count;
    }

    @Override public void map( Chunk ca, Chunk cp, Chunk w) {
      throw H2O.unimpl("GainsLiftBuilder with weights not yet implemented - requires weighted quantiles as well.");
    }

    @Override public void map( Chunk ca, Chunk cp ) {
      final double w = 1.0;
      _responses = new long[_thresh.length];
      _avg_response = 0;
      final int len = Math.min(ca._len, cp._len);
      for( int i=0; i < len; i++ ) {
        if (ca.isNA(i)) continue;
        final int a = (int)ca.at8(i);
        if (a != 0 && a != 1) throw new IllegalArgumentException("Invalid values in actualLabels: must be binary (0 or 1).");
        if (cp.isNA(i)) continue;
        final double pr = cp.atd(i);
        perRow(pr, a, w);
      }
    }

    public void perRow(double pr, int a, double w) {
      if (w!=1.0) throw H2O.unimpl("GainsLiftBuilder perRow cannot handle weights != 1 for now");
      if (Double.isNaN(pr)) return;
      if (a != 0 && a != 1) throw new IllegalArgumentException("Invalid values in actualLabels: must be binary (0 or 1).");
      for( int t=0; t < _thresh.length; t++ ) {
        if (a==0) continue;
        if (t==0) {
          if (pr >= _thresh[t]) _responses[t]++;
        }
        else if (t==_thresh.length-1) { //bottom decile
          if (pr < _thresh[t-1]) _responses[t]++;
        }
        else { //in between
          if (pr >= _thresh[t] && pr < _thresh[t-1]) _responses[t]++;
        }
      }
      if (a == 1) _avg_response++;
    }

    @Override public void reduce(GainsLiftBuilder other) {
      for( int i=0; i<_responses.length; ++i) {
        _responses[i] += other._responses[i];
      }
      _avg_response += other._avg_response;
    }

    @Override public void postGlobal(){
      _response_rates = new float[_thresh.length];
      for (int i=0; i<_response_rates.length; ++i) {
        _response_rates[i] = (float) _responses[i];
      }
      MathUtils.div(_response_rates, (float) _count / _thresh.length);
      for (int i=0; i<_response_rates.length; ++i) {
        // spill over to next bucket - needed due to tie breaking in quantiles
        if(_response_rates[i] > 1 && i<_response_rates.length-1) {
          _response_rates[i+1] += (_response_rates[i]-1);
          _response_rates[i] -= (_response_rates[i]-1);
        }
      }
      _avg_response_rate = (float)_avg_response / _count;
    }
  }
}
