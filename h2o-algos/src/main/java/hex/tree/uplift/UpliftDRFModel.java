package hex.tree.uplift;

import hex.AUUC;
import hex.ModelCategory;
import hex.tree.SharedTreeModel;
import hex.tree.SharedTreeModelWithContributions;
import hex.tree.drf.DRFModel;
import hex.util.EffectiveParametersUtils;
import water.Key;
import water.fvec.NewChunk;
import water.util.SBPrintStream;

public class UpliftDRFModel extends SharedTreeModelWithContributions<UpliftDRFModel, UpliftDRFModel.UpliftDRFParameters, UpliftDRFModel.UpliftDRFOutput> {

    public static class UpliftDRFParameters extends DRFModel.DRFParameters {
        public String algoName() { return "UpliftDRF"; }
        public String fullName() { return "Uplift Distributed Random Forest"; }
        public String javaName() { return UpliftDRFModel.class.getName(); }
        public boolean _binomial_double_trees = false;
        

        public enum UpliftMetricType { AUTO, KL, ChiSquared, Euclidean }
        public UpliftMetricType _uplift_metric;
        public AUUC.AUUCType _auuc_type;

        public int _mtries = -1; //number of columns to use per split. default depeonds on the algorithm and problem (classification/regression)

        public UpliftDRFParameters() {
            super();
            // Set DRF-specific defaults (can differ from SharedTreeModel's defaults)
            _max_depth = 20;
            _min_rows = 1;
        }
    }

    public static class UpliftDRFOutput extends SharedTreeModelWithContributions.SharedTreeOutput {
        public UpliftDRFOutput( UpliftDRF b) { super(b); }

        @Override
        public ModelCategory getModelCategory() {
            return ModelCategory.BinomialUplift;
        }
    }

    public UpliftDRFModel(Key<UpliftDRFModel> selfKey, UpliftDRFParameters parms, UpliftDRFOutput output ) {
        super(selfKey, parms, output);
    }

    @Override
    public void initActualParamValues() {
        super.initActualParamValues();
        EffectiveParametersUtils.initFoldAssignment(_parms);
        EffectiveParametersUtils.initHistogramType(_parms);
        EffectiveParametersUtils.initCategoricalEncoding(_parms, Parameters.CategoricalEncodingScheme.Enum);
        EffectiveParametersUtils.initUpliftMetric(_parms);
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
        return null;
    }

    @Override protected boolean binomialOpt() { return !_parms._binomial_double_trees; }
    
    /** Bulk scoring API for one row.  Chunks are all compatible with the model,
     *  and expect the last Chunks are for the final distribution and prediction.
     *  Default method is to just load the data into the tmp array, then call
     *  subclass scoring logic. */
    @Override protected double[] score0(double[] data, double[] preds, double offset, int ntrees) {
        super.score0(data, preds, offset, ntrees);
        int N = _output._ntrees;
        preds[1] /= N;
        preds[2] /= N;
        preds[0] = preds[1] - preds[2];
        return preds;
    }

    @Override protected void toJavaUnifyPreds(SBPrintStream body) {
        body.ip("preds[1] /= " + _output._ntrees + ";").nl();
        body.ip("preds[2] /= " + _output._ntrees + ";").nl();
        if (_parms._balance_classes)
            body.ip("hex.genmodel.GenModel.correctProbabilities(preds, PRIOR_CLASS_DISTRIB, MODEL_CLASS_DISTRIB);").nl();
        body.ip("preds[0] = preds[1] - preds[2];").nl();
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
    public UpliftDrfMojoWriter getMojo() {
        return new UpliftDrfMojoWriter(this);
    }

}
