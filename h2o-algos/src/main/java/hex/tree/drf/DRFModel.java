package hex.tree.drf;

import hex.tree.SharedTreeModel;
import water.Key;
import water.util.MathUtils;
import water.util.SB;

public class DRFModel extends SharedTreeModel<DRFModel,DRFModel.DRFParameters,DRFModel.DRFOutput> {

  public static class DRFParameters extends SharedTreeModel.SharedTreeParameters {
    public int _mtries = -1;
    public float _sample_rate = 0.632f;
    public boolean _binomial_double_trees = false;
    public DRFParameters() {
      super();
      // Set DRF-specific defaults (can differ from SharedTreeModel's defaults)
      _ntrees = 50;
      _max_depth = 20;
      _min_rows = 1;
    }
  }

  public static class DRFOutput extends SharedTreeModel.SharedTreeOutput {
    public DRFOutput( DRF b, double mse_train, double mse_valid ) { super(b,mse_train,mse_valid); }
  }

  public DRFModel(Key selfKey, DRFParameters parms, DRFOutput output ) { super(selfKey,parms,output); }

  @Override protected boolean binomialOpt() { return !_parms._binomial_double_trees; }

  /** Bulk scoring API for one row.  Chunks are all compatible with the model,
   *  and expect the last Chunks are for the final distribution and prediction.
   *  Default method is to just load the data into the tmp array, then call
   *  subclass scoring logic. */
  @Override protected double[] score0(double data[], double preds[], double weight, double offset) {
    super.score0(data, preds, weight, offset);
    int N = _parms._ntrees;
    if (_output.nclasses() == 1) { // regression - compute avg over all trees
      preds[0] /= N;
    } else { // classification
      if (_output.nclasses() == 2 && binomialOpt()) {
        preds[1] /= N; //average probability
        preds[2] = 1. - preds[1];
      } else {
        double sum = MathUtils.sum(preds);
        if (sum > 0) MathUtils.div(preds, sum);
      }
    }
    return preds;
  }

  @Override protected void toJavaUnifyPreds(SB body, SB file) {
    if (_output.nclasses() == 1) { // Regression
      body.ip("preds[0] /= " + _output._ntrees + ";").nl();
    } else { // Classification
      if( _output.nclasses()==2 && binomialOpt()) { // Kept the initial prediction for binomial
        body.ip("preds[1] /= " + _output._ntrees + ";").nl();
        body.ip("preds[2] = 1.0 - preds[1];").nl();
      } else {
        body.ip("double sum = 0;").nl();
        body.ip("for(int i=1; i<preds.length; i++) { sum += preds[i]; }").nl();
        body.ip("if (sum>0) for(int i=1; i<preds.length; i++) { preds[i] /= sum; }").nl();
      }
      if (_parms._balance_classes)
        body.ip("hex.genmodel.GenModel.correctProbabilities(preds, PRIOR_CLASS_DISTRIB, MODEL_CLASS_DISTRIB);").nl();
      body.ip("preds[0] = hex.genmodel.GenModel.getPrediction(preds, data, " + defaultThreshold() + " );").nl();
    }
  }

}
