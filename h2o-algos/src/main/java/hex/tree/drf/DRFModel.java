package hex.tree.drf;

import hex.genmodel.utils.DistributionFamily;
import hex.tree.*;
import hex.util.EffectiveParametersUtils;
import water.Job;
import water.Key;
import water.MemoryManager;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.util.ArrayUtils;
import water.util.MathUtils;

import java.util.Arrays;

public class DRFModel extends SharedTreeModelWithContributions<DRFModel, DRFModel.DRFParameters, DRFModel.DRFOutput> {

  public static class DRFParameters extends SharedTreeModelWithContributions.SharedTreeParameters {
    public String algoName() { return "DRF"; }
    public String fullName() { return "Distributed Random Forest"; }
    public String javaName() { return DRFModel.class.getName(); }
    public boolean _binomial_double_trees = false;
    public int _mtries = -1; //number of columns to use per split. default depends on the algorithm and problem (classification/regression)

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
    EffectiveParametersUtils.initCalibrationMethod(_parms);
  }

  public void initActualParamValuesAfterOutputSetup(boolean isClassifier) {
    EffectiveParametersUtils.initStoppingMetric(_parms, isClassifier);
  }

  @Override
  public Frame scoreContributions(Frame frame, Key<Frame> destination_key, Job<Frame> j) {
    if (_parms._binomial_double_trees) {
      throw new UnsupportedOperationException(
              "Calculating contributions is currently not supported for model with binomial_double_trees parameter set.");
    }
    return super.scoreContributions(frame, destination_key, j);
  }

  @Override
  public Frame scoreContributions(Frame frame, Key<Frame> destination_key, Job<Frame> j, ContributionsOptions options) {
    if (_parms._binomial_double_trees) {
      throw new UnsupportedOperationException(
              "Calculating contributions is currently not supported for model with binomial_double_trees parameter set.");
    }
    return super.scoreContributions(frame, destination_key, j, options);
  }

  @Override
  protected SharedTreeModelWithContributions<DRFModel, DRFParameters, DRFOutput>.ScoreContributionsWithBackgroundTask getScoreContributionsWithBackgroundTask(SharedTreeModel model, Frame fr, Frame backgroundFrame, boolean expand, int[] catOffsets, ContributionsOptions options) {
    return new ScoreContributionsWithBackgroundTaskDRF(fr, backgroundFrame, options._outputPerReference,  this, expand, catOffsets);
  }

  @Override
  protected ScoreContributionsTask getScoreContributionsTask(SharedTreeModel model) {
    return new ScoreContributionsTaskDRF(this);    
  }

  @Override
  protected ScoreContributionsTask getScoreContributionsSoringTask(SharedTreeModel model, ContributionsOptions options) {
    return new ScoreContributionsSoringTaskDRF(this, options);
  }

  @Override public boolean binomialOpt() { return !_parms._binomial_double_trees; }

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

  @Override 
  public double score(double[] data) {
    double[] pred = score0(data, new double[_output.nclasses() + 1], 0, _output._ntrees);
    score0PostProcessSupervised(pred, data);
    return pred[0];
  }

  @Override
  protected SharedTreePojoWriter makeTreePojoWriter() {
    CompressedForest compressedForest = new CompressedForest(_output._treeKeys, _output._domains);
    CompressedForest.LocalCompressedForest localCompressedForest = compressedForest.fetch();
    return new DrfPojoWriter(this, localCompressedForest._trees);
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
              float featurePlusBiasRatio = (float)1 / (_output._varimp.numberOfUsedVariables() + 1); // + 1 for bias term
              nc[i].addNum(contribs[i] != 0 ? (featurePlusBiasRatio - (contribs[i] / _output._ntrees)) : 0);
            }
        }
    }
  }
  
  public class ScoreContributionsWithBackgroundTaskDRF extends ScoreContributionsWithBackgroundTask {

    public ScoreContributionsWithBackgroundTaskDRF(Frame fr, Frame backgroundFrame, boolean perReference, SharedTreeModel model, boolean expand, int[] catOffsets) {
      super(fr._key, backgroundFrame._key, perReference, model, expand, catOffsets, false);
    }

    @Override
    public void doModelSpecificComputation(double[] contribs) {
      // Prediction of DRF tree ensemble is an average prediction of all trees. So, divide contribs by ntrees
      if (_output.nclasses() == 1) { //Regression
        for (int i = 0; i < contribs.length; i++) {
          contribs[i] = contribs[i] / _output._ntrees;
        }
     } else { //Binomial
        /* Sum of contributions + biasTerm (contribs[contribs.length-1]) gives us prediction for P(Y==0) but the user is
        interested in knowing P(Y==1) = 1 - P(Y==0).
        
        Since SHAP should satisfy the dummy property - if a feature is not used it should not have any contribution, we 
        cannot just do 1/nfeatures - (contribs[i]/ntrees).
        
        In the contribs array we have contributions and BiasTerm the difference between the two is that BiasTerm should
        correspond to the prediction of the background data point, hence it doesn't support the dummy property and should
        be always involved in the conversion.
        
        Another property that should be satisfied is the following:
         Let's denote contribution of feature a of data point x on background data point b as contribution(a|x,b), then
         contribution(a|x,b) == - contribution(a|b,x). In other words, if contribution(a|x,b) shifts the response
         from f(b) to f(x), then contribution(a|b,x) should move the response by the same magnitude but in the opposite
         direction (from f(x) to f(b)).
         
        Let's derive (hopefully) the correct formula:
        $$P(Y=0|x) = 0.3$$
        $$P(Y=0|b) = 0.45$$
        
        $$ \sum\phi_i =P(Y=0|x) - P(Y=0|b) = -0.15$$
        
        Here ^^^ we call the $P(Y=0|b)$  "bias term" and here it should be obvious that it corresponds to the prediction of the background sample.
        If we rewrite it we can see that the sum "lifts" the prediction from the background sample prediction to the prediction for the point that we calculate the SHAP for.
        
        $$P(Y=0|x)  =  \sum\phi_i - P(Y=0|b)$$
        
        Now if we are interested in the contributions to P(Y=1|x) we can calculate those probabilities like:
        $$P(Y=1|x) = 1-P(Y=0|x) = 0.7$$
        $$P(Y=1|b) = 1- P(Y=0|b) = 0.55$$
        
        Now the important part comes in:
        $$P(Y=1|x) - P(Y=1|b) = 1- P(Y=0|x) - 1 + P(Y=0|b) = - (P(Y=0|x) - P(Y=0|b)) = - \sum\phi_i = 0.15$$
        
        So the contributions for $P(Y=1|x)$ sum up to the negative value of contributions for $P(Y=0|x)$.
        And the second important thing is that the bias term now should corresponds to $P(Y=1|b) = 1 - P(Y=0|b)$.
        So I think the only place where we should subtract from a constant is the bias term.
        
        More details can be found in this thread: https://github.com/h2oai/h2o-3/issues/15657#issuecomment-1652287487
        */
        for (int i = 0; i < contribs.length-1; i++) {
          contribs[i] = -(contribs[i] / _output._ntrees);
        }

        contribs[contribs.length-1] = 1 - (contribs[contribs.length-1]/_output._ntrees);
      }
    }
  }

  public class ScoreContributionsSoringTaskDRF extends ScoreContributionsSortingTask {

    public ScoreContributionsSoringTaskDRF(SharedTreeModel model, ContributionsOptions options) {
      super(model, options);
    }

    @Override
    public void doModelSpecificComputation(float[] contribs) {
      for (int i = 0; i < contribs.length; i++) {
        // Prediction of DRF tree ensemble is an average prediction of all trees. So, divide contribs by ntrees
        if (_output.nclasses() == 1) { //Regression
          contribs[i] = contribs[i] / _output._ntrees;
        } else { //Binomial
          float featurePlusBiasRatio = (float)1 / (_output.nfeatures() + 1); // + 1 for bias term
          contribs[i] = featurePlusBiasRatio - (contribs[i] / _output._ntrees);
        }
      }
    }
  }

  @Override
  public DrfMojoWriter getMojo() {
    return new DrfMojoWriter(this);
  }

}
