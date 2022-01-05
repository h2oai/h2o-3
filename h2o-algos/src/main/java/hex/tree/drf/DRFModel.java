package hex.tree.drf;

import hex.genmodel.utils.DistributionFamily;
import hex.tree.*;
import hex.util.EffectiveParametersUtils;
import water.Key;
import water.fvec.NewChunk;
import water.util.MathUtils;

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

    @Override
    public DistributionFamily[] supportedDistributions() {
      return new DistributionFamily[] {
              DistributionFamily.AUTO,
              DistributionFamily.bernoulli,
              DistributionFamily.multinomial,
              DistributionFamily.gaussian,
      };
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
                float featurePlusBiasRatio = (float)1 / (_output.nfeatures() + 1); // + 1 for bias term
                nc[i].addNum(featurePlusBiasRatio - (contribs[i] / _output._ntrees));
            }
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
