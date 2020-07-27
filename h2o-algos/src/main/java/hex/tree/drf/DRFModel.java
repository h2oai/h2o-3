package hex.tree.drf;

import hex.tree.SharedTreeModel;
import hex.tree.SharedTreeModelWithContributions;
import hex.util.EffectiveParametersUtils;
import water.Key;
import water.fvec.NewChunk;
import water.util.MathUtils;
import water.util.SBPrintStream;

public class DRFModel extends SharedTreeModelWithContributions<DRFModel, DRFModel.DRFParameters, DRFModel.DRFOutput> {

  public static class DRFParameters extends SharedTreeModelWithContributions.SharedTreeParameters {
    public String algoName() { return "DRF"; }
    public String fullName() { return "Distributed Random Forest"; }
    public String javaName() { return DRFModel.class.getName(); }
    public boolean _binomial_double_trees = false;
    public int _mtries = -1; //number of columns to use per split. default depeonds on the algorithm and problem (classification/regression)

    public DRFParameters() {
      super();
      // Set DRF-specific defaults (can differ from SharedTreeModel's defaults)
      _max_depth = 20;
      _min_rows = 1;
    }
  }

  public static class DRFOutput extends SharedTreeModelWithContributions.SharedTreeOutput {
    public DRFOutput( DRF b) { super(b); }
  }

  public DRFModel(Key<DRFModel> selfKey, DRFParameters parms, DRFOutput output ) {
    super(selfKey, parms, output);
  }

  @Override
  public void initActualParamValues() {
    super.initActualParamValues();
    EffectiveParametersUtils.initFoldAssignment(_parms);
    EffectiveParametersUtils.initHistogramType(_parms);
    EffectiveParametersUtils.initCategoricalEncoding(_parms, Parameters.CategoricalEncodingScheme.Enum);
  }

  public void initActualParamValuesAfterOutputSetup(boolean isClassifier) {
    EffectiveParametersUtils.initStoppingMetric(_parms, isClassifier);
  }

  @Override
  protected ScoreContributionsTask getScoreContributionsTask(SharedTreeModel model) {
    return new ScoreContributionsTaskDRF(this);    
  }

  @Override protected boolean binomialOpt() { return !_parms._binomial_double_trees; }

  /** Bulk scoring API for one row.  Chunks are all compatible with the model,
   *  and expect the last Chunks are for the final distribution and prediction.
   *  Default method is to just load the data into the tmp array, then call
   *  subclass scoring logic. */
  @Override protected double[] score0(double[] data, double[] preds, double offset, int ntrees) {
    super.score0(data, preds, offset, ntrees);
    int N = _output._ntrees;
    if (_output.nclasses() == 1) { // regression - compute avg over all trees
      if (N>=1) preds[0] /= N;
    } else { // classification
      if (_output.nclasses() == 2 && binomialOpt()) {
        if (N>=1) {
          preds[1] /= N; //average probability
        }
        preds[2] = 1. - preds[1];
      } else {
        double sum = MathUtils.sum(preds);
        if (sum > 0) MathUtils.div(preds, sum);
      }
    }
    return preds;
  }
  
  @Override protected void toJavaUnifyPreds(SBPrintStream body) {
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
      body.ip("preds[0] = hex.genmodel.GenModel.getPrediction(preds, PRIOR_CLASS_DISTRIB, data, " + defaultThreshold() + ");").nl();
    }
  }

  public class ScoreContributionsTaskDRF extends ScoreContributionsTask {

    public ScoreContributionsTaskDRF(SharedTreeModel model) {
        super(model);
    }

    @Override
    public void addContribToNewChunk(float[] contribs, NewChunk[] nc) {
        for (int i = 0; i < nc.length; i++) {
            // Prediction of DRF tree ensemble is an average prediction of all trees. So, divide contribs by ntrees
            if (_output.nclasses() == 1) { //Regression
                nc[i].addNum(contribs[i] /_output._ntrees);
            } else { //Binomial
                float featurePlusBiasRatio = (float)1 / (_output.nfeatures() + 1); // + 1 for bias term
                nc[i].addNum(featurePlusBiasRatio - (contribs[i] / _output._ntrees));
            }
        }
    }
  }

  @Override
  public DrfMojoWriter getMojo() {
    return new DrfMojoWriter(this);
  }

}
