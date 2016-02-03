package hex.tree.gbm;

import hex.Distribution;
import hex.tree.SharedTreeModel;
import water.Key;
import water.util.SBPrintStream;

public class GBMModel extends SharedTreeModel<GBMModel,GBMModel.GBMParameters,GBMModel.GBMOutput> {

  public static class GBMParameters extends SharedTreeModel.SharedTreeParameters {
    public float _learn_rate;
    public float _col_sample_rate;

    public GBMParameters() {
      super();
      _learn_rate = 0.1f;
      _col_sample_rate = 1.0f;
      _sample_rate = 1.0f;
      _ntrees = 50;
      _max_depth = 5;
    }

    public String algoName() { return "GBM"; }
    public String fullName() { return "Gradient Boosting Method"; }
    public String javaName() { return GBMModel.class.getName(); }
  }

  public static class GBMOutput extends SharedTreeModel.SharedTreeOutput {
    public GBMOutput( GBM b, double mse_train, double mse_valid ) { super(b,mse_train,mse_valid); }
  }

  public GBMModel(Key selfKey, GBMParameters parms, GBMOutput output ) { super(selfKey,parms,output); }

  /** Bulk scoring API for one row.  Chunks are all compatible with the model,
   *  and expect the last Chunks are for the final distribution and prediction.
   *  Default method is to just load the data into the tmp array, then call
   *  subclass scoring logic. */
  @Override protected double[] score0(double data[/*ncols*/], double preds[/*nclasses+1*/], double weight, double offset) {
    super.score0(data, preds, weight, offset);    // These are f_k(x) in Algorithm 10.4
    if (_parms._distribution == Distribution.Family.bernoulli) {
      double f = preds[1] + _output._init_f + offset; //Note: class 1 probability stored in preds[1] (since we have only one tree)
      preds[2] = new Distribution(_parms).linkInv(f);
      preds[1] = 1.0 - preds[2];
    } else if (_parms._distribution == Distribution.Family.multinomial) { // Kept the initial prediction for binomial
      if (_output.nclasses() == 2) { //1-tree optimization for binomial
        preds[1] += _output._init_f + offset; //offset is not yet allowed, but added here to be future-proof
        preds[2] = -preds[1];
      }
      hex.genmodel.GenModel.GBM_rescale(preds);
    } else { //Regression
      double f = preds[0] + _output._init_f + offset;
      preds[0] = new Distribution(_parms).linkInv(f);
    }
    return preds;
  }

  // Note: POJO scoring code doesn't support per-row offsets (the scoring API would need to be changed to pass in offsets)
  @Override protected void toJavaUnifyPreds(SBPrintStream body) {
    // Preds are filled in from the trees, but need to be adjusted according to
    // the loss function.
    if( _parms._distribution == Distribution.Family.bernoulli ) {
      body.ip("preds[2] = preds[1] + ").p(_output._init_f).p(";").nl();
      body.ip("preds[2] = " + new Distribution(_parms).linkInvString("preds[2]") + ";").nl();
      body.ip("preds[1] = 1.0-preds[2];").nl();
      if (_parms._balance_classes)
        body.ip("hex.genmodel.GenModel.correctProbabilities(preds, PRIOR_CLASS_DISTRIB, MODEL_CLASS_DISTRIB);").nl();
      body.ip("preds[0] = hex.genmodel.GenModel.getPrediction(preds, PRIOR_CLASS_DISTRIB, data, " + defaultThreshold() + ");").nl();
      return;
    }
    if( _output.nclasses() == 1 ) { // Regression
      body.ip("preds[0] += ").p(_output._init_f).p(";").nl();
      body.ip("preds[0] = " + new Distribution(_parms).linkInvString("preds[0]") + ";").nl();
      return;
    }
    if( _output.nclasses()==2 ) { // Kept the initial prediction for binomial
      body.ip("preds[1] += ").p(_output._init_f).p(";").nl();
      body.ip("preds[2] = - preds[1];").nl();
    }
    body.ip("hex.genmodel.GenModel.GBM_rescale(preds);").nl();
    if (_parms._balance_classes)
      body.ip("hex.genmodel.GenModel.correctProbabilities(preds, PRIOR_CLASS_DISTRIB, MODEL_CLASS_DISTRIB);").nl();
    body.ip("preds[0] = hex.genmodel.GenModel.getPrediction(preds, PRIOR_CLASS_DISTRIB, data, " + defaultThreshold() + ");").nl();
  }
}
