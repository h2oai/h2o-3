package hex;

import water.Iced;
import water.MRTask;
import water.Scope;
import water.fvec.Chunk;
import water.fvec.ChunkAry;
import water.fvec.VecAry;

public class MeanResidualDeviance extends Iced {

  //INPUT
  public VecAry _vals;

  public Distribution _dist;

  //OUTPUT
  public double meanResidualDeviance;

  public MeanResidualDeviance(Distribution dist, VecAry vals /* preds, actuals, weights */) {
    _vals = vals;
    _dist = dist;
  }

  private void init() throws IllegalArgumentException {
    if( _vals ==null )
      throw new IllegalArgumentException("Missing actual targets or predicted values!");

    if (!_vals.isNumeric(1))
      throw new IllegalArgumentException("Actual target column must be numeric!");
    if (_vals.isCategorical(0))
      throw new IllegalArgumentException("Predicted targets cannot be class labels, expect continuous values.");
    if (_vals.numCols() == 3 && !_vals.isNumeric(2))
      throw new IllegalArgumentException("Observation weights must be numeric.");
  }

  public MeanResidualDeviance exec() {
    Scope.enter();
    init();
    try {
      MeanResidualBuilder gt = new MeanResidualBuilder(_dist).doAll(_vals);
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

    @Override public void map(ChunkAry cs /*aactual predicted */ ) {
      _mean_residual_deviance=0;
      _wcount=0;
      for( int i=0; i < cs._len; i++ ) {
        if (cs.isNA(i,0)) continue;
        if (cs.isNA(i,1)) continue;
        final double a = cs.atd(i,0);
        final double pr = cs.atd(i,1);
        final double w = cs._numCols == 3?cs.atd(i,2):1;
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
