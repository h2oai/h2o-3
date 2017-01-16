package hex;

import water.Iced;
import water.MRTask;
import water.Scope;
import water.fvec.Chunk;
import water.fvec.Vec;

public class MeanResidualDeviance extends Iced {

  //INPUT
  public Vec _actuals;
  public Vec _preds;
  public Vec _weights;
  public Distribution _dist;

  //OUTPUT
  public double meanResidualDeviance;

  public MeanResidualDeviance(Distribution dist, Vec preds, Vec actuals, Vec weights) {
    _preds = preds;
    _actuals = actuals;
    _weights = weights;
    _dist = dist;
  }

  private void init() throws IllegalArgumentException {
    if( _actuals ==null || _preds ==null )
      throw new IllegalArgumentException("Missing actual targets or predicted values!");
    if (_actuals.length() != _preds.length())
      throw new IllegalArgumentException("Both arguments must have the same length ("+ _actuals.length()+"!="+ _preds.length()+")!");
    if (!_actuals.isNumeric())
      throw new IllegalArgumentException("Actual target column must be numeric!");
    if (_preds.isCategorical())
      throw new IllegalArgumentException("Predicted targets cannot be class labels, expect continuous values.");
    if (_weights != null && !_weights.isNumeric())
      throw new IllegalArgumentException("Observation weights must be numeric.");

    // The vectors are from different groups => align them, but properly delete it after computation
    if (!_actuals.group().equals(_preds.group())) {
      _preds = _actuals.align(_preds);
      Scope.track(_preds);
      if (_weights !=null) {
        _weights = _actuals.align(_weights);
        Scope.track(_weights);
      }
    }
  }

  public MeanResidualDeviance exec() {
    Scope.enter();
    init();
    try {
      MeanResidualBuilder gt = new MeanResidualBuilder(_dist);
      gt = (_weights != null) ? gt.doAll(_actuals, _preds, _weights) : gt.doAll(_actuals, _preds);
      meanResidualDeviance=gt._mean_residual_deviance;
    } finally {
      Scope.exit();
    }
    return this;
  }

  // Compute Mean Residual Deviance table via MRTask
  public static class MeanResidualBuilder extends MRTask<MeanResidualBuilder> {
    public double _mean_residual_deviance;
    private double _wcount;
    private Distribution _dist;

    MeanResidualBuilder(Distribution dist) { _dist = dist; }

    @Override public void map(Chunk ca, Chunk cp) { map(ca, cp, (Chunk)null); }
    @Override public void map(Chunk ca, Chunk cp, Chunk cw) {
      _mean_residual_deviance=0;
      _wcount=0;
      final int len = Math.min(ca._len, cp._len);
      for( int i=0; i < len; i++ ) {
        if (ca.isNA(i)) continue;
        if (cp.isNA(i)) continue;
        final double a = ca.atd(i);
        final double pr = cp.atd(i);
        final double w = cw!=null?cw.atd(i):1;
        perRow(pr, a, w);
      }
    }

    public void perRow(double pr, double a, double w) {
      if (w==0) return;
      assert (!Double.isNaN(pr));
      assert (!Double.isNaN(a));
      assert (!Double.isNaN(w));
      _mean_residual_deviance+=_dist.deviance(w,a,pr);
      _wcount+=w;
    }

    @Override public void reduce(MeanResidualBuilder other) {
      _mean_residual_deviance += other._mean_residual_deviance;
      _wcount += other._wcount;
    }

    @Override public void postGlobal(){
      _mean_residual_deviance/=_wcount;
    }
  }
}
