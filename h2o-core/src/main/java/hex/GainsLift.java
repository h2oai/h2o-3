package hex;

import hex.quantile.Quantile;
import hex.quantile.QuantileModel;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;
import water.util.MathUtils;
import water.util.PrettyPrint;
import water.util.TwoDimTable;

import java.util.Arrays;

public class GainsLift extends Iced {
  private static int GROUPS = 10;

  //INPUT
  public Vec labels;
  public Vec preds;
  public Vec weight;

  //OUTPUT
  public float[] response_rates;
  public float avg_response_rate;
  public long[] positive_responses;
  TwoDimTable table;

  protected void init() throws IllegalArgumentException {
    if( labels ==null || preds ==null )
      throw new IllegalArgumentException("Missing actualLabels or predictedProbs!");
    if (labels.length() != preds.length())
      throw new IllegalArgumentException("Both arguments must have the same length ("+ labels.length()+"!="+ preds.length()+")!");
    if (!labels.isInt())
      throw new IllegalArgumentException("Actual column must be integer class labels!");
    if (labels.cardinality() != -1 && labels.cardinality() != 2)
      throw new IllegalArgumentException("Actual column must contain binary class labels, but found cardinality " + labels.cardinality() + "!");
    if (preds.isCategorical())
      throw new IllegalArgumentException("predictedProbs cannot be class labels, expect probabilities.");
    if (weight != null && !weight.isNumeric())
      throw new IllegalArgumentException("weight must be numeric.");
  }

  public GainsLift() {}

  protected void exec() {
    init();
    Scope.enter();
    Frame frame = null;
    try {
      labels = labels.toCategoricalVec();
      frame = new Frame(Key.make(), new String[]{"predicted"}, new Vec[]{preds});
      DKV.put(frame);
      // The vectors are from different groups => align them, but properly delete it after computation
      if (!labels.group().equals(preds.group())) {
        preds = labels.align(preds);
        Scope.track(preds._key);
        if (weight!=null) {
          weight = labels.align(weight);
          Scope.track(weight._key);
        }
      }
      //get specific pre-computed quantiles (deciles) from rollupstats
      //First check that rollups still uses the convention we rely on (hardcoded indexing below)
      assert(Arrays.equals(Vec.PERCENTILES,
              //             0      1    2    3    4     5        6          7    8   9   10          11    12   13   14    15, 16
              new double[]{0.001, 0.01, 0.1, 0.2, 0.25, 0.3,    1.0 / 3.0, 0.4, 0.5, 0.6, 2.0 / 3.0, 0.7, 0.75, 0.8, 0.9, 0.99, 0.999}));
      //HACK: hardcoded quantiles for simplicity (0.9,0.8,...,0.1,0)
      double[] rq = preds.pctiles();
      double[] quantiles = new double[]{
              rq[14], rq[13], rq[11], rq[9], rq[8], rq[7], rq[5], rq[3], rq[2], 0 /*ignored*/
      };
      GainsTask gt = new GainsTask(quantiles, labels.length());
      if (weight != null)
        gt.doAll(labels, preds, weight);
      else
        gt.doAll(labels, preds);

      response_rates = gt.response_rates();
      avg_response_rate = gt.avg_response_rate();
      positive_responses = gt.responses();
    } finally {       // Delete adaptation vectors
      if (frame != null) DKV.remove(frame._key); //just remove the header
      Scope.exit();
    }
    Log.info(createTwoDimTable());
  }

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
  private static class GainsTask extends MRTask<GainsTask> {
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

    GainsTask(double[] thresh, long count) {
      _thresh = thresh.clone();
      _count = count;
    }

    @Override public void map( Chunk ca, Chunk cp ) {
      _responses = new long[_thresh.length];
      _avg_response = 0;
      final int len = Math.min(ca._len, cp._len);
      for( int i=0; i < len; i++ ) {
        if (ca.isNA(i)) continue;
        final int a = (int)ca.at8(i);
        if (a != 0 && a != 1) throw new IllegalArgumentException("Invalid values in actualLabels: must be binary (0 or 1).");
        if (cp.isNA(i)) continue;
        final double pr = cp.atd(i);
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
    }

    @Override public void reduce( GainsTask other ) {
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
