package hex.rulefit;

import hex.*;
import hex.glm.GLMModel;
import hex.tree.SharedTreeModel;
import hex.util.LinearAlgebraUtils;
import water.*;
import water.fvec.Frame;
import water.udf.CFuncRef;
import water.util.TwoDimTable;


public class RuleFitModel extends Model<RuleFitModel, RuleFitModel.RuleFitParameters, RuleFitModel.RuleFitOutput> {
    public enum Algorithm {DRF, GBM, AUTO}

    public enum ModelType {RULES, RULES_AND_LINEAR, LINEAR}

    @Override
    public ToEigenVec getToEigenVec() {
        return LinearAlgebraUtils.toEigen;
    }

    SharedTreeModel[] treeModels;

    GLMModel glmModel;

    public static class RuleFitParameters extends Model.Parameters {
        public String algoName() {
            return "RuleFit";
        }

        public String fullName() {
            return "RuleFit";
        }

        public String javaName() {
            return RuleFitModel.class.getName();
        }

        @Override
        public long progressUnits() {
            return RuleFit.WORK_TOTAL;
        }

        // the algorithm to use to generate rules. Options are "DRF" (default), "GBM"
        public Algorithm _algorithm = Algorithm.AUTO;

        // minimum length of rules. Defaults to 1.
        public int _min_rule_length = 1;

        // maximum length of rules. Defaults to 10.
        public int _max_rule_length = 10;

        // the maximum number of rules to return. Defaults to -1 which means the number of rules is selected 
        // by diminishing returns in model deviance.
        public int _max_num_rules = -1;

        // specifies type of base learners in the ensemble. Options are RULES_AND_LINEAR (initial ensemble includes both rules and linear terms, default), RULES (prediction rules only), LINEAR (linear terms only)
        public ModelType _model_type = ModelType.RULES_AND_LINEAR;
    }

    public static class RuleFitOutput extends Model.Output {

        // a set of rules and coefficients

        public double[] _intercept;

        public TwoDimTable _rule_importance = null;

        Key[] treeModelsKeys = null;

        Key glmModelKey = null;

        //  feature interactions ...

        public RuleFitOutput(RuleFit b) {
            super(b);
        }
    }

    public RuleFitModel(Key<RuleFitModel> selfKey, RuleFitParameters parms, RuleFitOutput output, SharedTreeModel[] treeModels, GLMModel glmModel) {
        super(selfKey, parms, output);
        this.treeModels = treeModels;
        this.glmModel = glmModel;
    }

    @Override
    public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
        assert domain == null;
        switch (_output.getModelCategory()) {
            case Binomial:
                return new ModelMetricsBinomial.MetricBuilderBinomial(domain);
            case Multinomial:
                return new ModelMetricsMultinomial.MetricBuilderMultinomial(_output.nclasses(), domain);
            case Regression:
                return new ModelMetricsRegression.MetricBuilderRegression();
            default:
                throw H2O.unimpl("Invalid ModelCategory " + _output.getModelCategory());
        }
    }

    @Override
    protected double[] score0(double data[], double preds[]) {
        throw new UnsupportedOperationException("RuleFitModel doesn't support scoring on raw data. Use score() instead.");
    }

    @Override
    public Frame score(Frame fr, String destination_key, Job j, boolean computeMetrics, CFuncRef customMetricFunc) throws IllegalArgumentException {
        Frame pathsFrame = new Frame(Key.make("paths_frame" + destination_key));
        Frame paths = null;
        Key[] keys = new Key[_output.treeModelsKeys.length];
        if (ModelType.RULES_AND_LINEAR.equals(this._parms._model_type) || ModelType.RULES.equals(this._parms._model_type)) {
            for (int i = 0; i < _output.treeModelsKeys.length; i++) {
                SharedTreeModel treeModel = DKV.getGet(_output.treeModelsKeys[i]);
                paths = treeModel.scoreLeafNodeAssignment(fr, Model.LeafNodeAssignment.LeafNodeAssignmentType.Path, Key.make("path_" + i + destination_key));
                paths.setNames(RuleFitUtils.getPathNames(i, paths.numCols(), paths.names()));
                pathsFrame.add(paths);
                keys[i] = paths._key;
            }
        }
        if (ModelType.RULES_AND_LINEAR.equals(this._parms._model_type) || ModelType.LINEAR.equals(this._parms._model_type)) {
            Frame adaptFrm = new Frame(fr.deepCopy(null));
            adaptFrm.setNames(RuleFitUtils.getLinearNames(adaptFrm.numCols(), adaptFrm.names()));
            pathsFrame.add(adaptFrm);
        }
        GLMModel glmModel = DKV.getGet(_output.glmModelKey);
        Frame destination = glmModel.score(pathsFrame, destination_key, null, true);

        updateModelMetrics(glmModel, fr);
        
        pathsFrame.remove();
        if (paths != null) {
            paths.remove();
        }
        for (int i = 0; i < _output.treeModelsKeys.length; i++) {
            DKV.remove(keys[i]);
        }
        return destination;
    }

    @Override
    protected Futures remove_impl(Futures fs, boolean cascade) {
        super.remove_impl(fs, cascade);
        if(cascade) {
            for (SharedTreeModel treeModel : treeModels) {
                treeModel.remove(fs);
            }

            glmModel.remove(fs);
        }
        
        return fs;
    }

    void updateModelMetrics( GLMModel glmModel, Frame fr){
        this._output._validation_metrics = glmModel._output._validation_metrics;
        this._output._training_metrics = glmModel._output._training_metrics;
        this._output._cross_validation_metrics = glmModel._output._cross_validation_metrics;
        this._output._cross_validation_metrics_summary = glmModel._output._cross_validation_metrics_summary;

        for (Key<ModelMetrics> modelMetricsKey : glmModel._output.getModelMetrics()) {
            this.addModelMetrics(modelMetricsKey.get().deepCloneWithDifferentModelAndFrame(this, fr));
        }
    }
}
