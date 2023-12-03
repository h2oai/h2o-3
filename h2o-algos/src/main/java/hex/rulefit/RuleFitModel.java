package hex.rulefit;

import hex.*;
import hex.glm.GLMModel;
import hex.util.LinearAlgebraUtils;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.udf.CFuncRef;
import water.util.TwoDimTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static hex.rulefit.RuleFitUtils.deduplicateRules;
import static hex.rulefit.RuleFitUtils.sortRules;

public class RuleFitModel extends Model<RuleFitModel, RuleFitModel.RuleFitParameters, RuleFitModel.RuleFitOutput> implements SignificantRulesCollector{
    public enum Algorithm {DRF, GBM, AUTO}

    public enum ModelType {RULES, RULES_AND_LINEAR, LINEAR}

    @Override
    public ToEigenVec getToEigenVec() {
        return LinearAlgebraUtils.toEigen;
    }

    GLMModel glmModel;

    RuleEnsemble ruleEnsemble;
    
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

        // minimum length of rules. Defaults to 3.
        public int _min_rule_length = 3;

        // maximum length of rules. Defaults to 3.
        public int _max_rule_length = 3;

        // the maximum number of rules to return. Defaults to -1 which means the number of rules is selected 
        // by diminishing returns in model deviance.
        public int _max_num_rules = -1;

        // specifies type of base learners in the ensemble. Options are RULES_AND_LINEAR (initial ensemble includes both rules and linear terms, default), RULES (prediction rules only), LINEAR (linear terms only)
        public ModelType _model_type = ModelType.RULES_AND_LINEAR;
        
        // specifies the number of trees to build in the tree model. Defaults to 50.
        public int _rule_generation_ntrees = 50;
        
        // whether to remove rules which are identical to an earlier rule. Defaults to true.
        public boolean _remove_duplicates = true;
        
        // lambda for lasso
        public double[] _lambda;
        

        public void validate(RuleFit rfit) {
            if (rfit._parms._min_rule_length > rfit._parms._max_rule_length) {
                rfit.error("min_rule_length", "min_rule_length cannot be greater than max_rule_length. Current values:  min_rule_length = " + rfit._parms._min_rule_length
                        + ", max_rule_length = " + rfit._parms._max_rule_length + ".");
            }
        }
    }

    public static class RuleFitOutput extends Model.Output {

        // a set of rules and coefficients

        public double[] _intercept;
        
        String[] _linear_names;

        public TwoDimTable _rule_importance = null;

        Key glmModelKey = null;

        String[] _dataFromRulesCodes;

        //  feature interactions ...

        public RuleFitOutput(RuleFit b) {
            super(b);
        }
    }

    public RuleFitModel(Key<RuleFitModel> selfKey, RuleFitParameters parms, RuleFitOutput output, GLMModel glmModel, RuleEnsemble ruleEnsemble) {
        super(selfKey, parms, output);
        this.glmModel = glmModel;
        this.ruleEnsemble = ruleEnsemble;
    }

    @Override
    public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
        assert domain == null;
        switch (_output.getModelCategory()) {
            case Binomial:
                return new ModelMetricsBinomial.MetricBuilderBinomial(domain);
            case Multinomial:
                return new ModelMetricsMultinomial.MetricBuilderMultinomial(_output.nclasses(), domain, _parms._auc_type);
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
        Frame adaptFrm = new Frame(fr);
        adaptTestForTrain(adaptFrm, true, false);

        try (Scope.Safe safe = Scope.safe(adaptFrm)) {
            Frame linearTest = new Frame();
            if (ModelType.RULES_AND_LINEAR.equals(this._parms._model_type) || ModelType.RULES.equals(this._parms._model_type)) {
                linearTest.add(ruleEnsemble.createGLMTrainFrame(adaptFrm, _parms._max_rule_length - _parms._min_rule_length + 1, _parms._rule_generation_ntrees, this._output.classNames(), _parms._weights_column, false));
            }
            if (ModelType.RULES_AND_LINEAR.equals(this._parms._model_type) || ModelType.LINEAR.equals(this._parms._model_type)) {
                linearTest.add(RuleFitUtils.getLinearNames(adaptFrm.numCols(), adaptFrm.names()), adaptFrm.vecs());
            } else {
                linearTest.add(RuleFitUtils.getLinearNames(1, new String[] {this._parms._response_column})[0], adaptFrm.vec(this._parms._response_column));
            }

            Frame scored = glmModel.score(linearTest, destination_key, null, true);
            updateModelMetrics(glmModel, fr);
            return Scope.untrack(scored);
        }
    }

    @Override
    protected Futures remove_impl(Futures fs, boolean cascade) {
        super.remove_impl(fs, cascade);
        if(cascade) {
            glmModel.remove(fs);
        }
        
        return fs;
    }

    void updateModelMetrics(GLMModel glmModel, Frame fr){
        for (Key<ModelMetrics> modelMetricsKey : glmModel._output.getModelMetrics()) {
            // what is null here was already added to RF model from GLM submodel during hex.rulefit.RuleFit.RuleFitDriver.fillModelMetrics
            if (modelMetricsKey.get() != null)
                this.addModelMetrics(modelMetricsKey.get().deepCloneWithDifferentModelAndFrame(this, fr));
        }
    }

    @Override
    public RuleFitMojoWriter getMojo() {
        return new RuleFitMojoWriter(this);
    }

    @Override
    public boolean haveMojo() {
        return true;
    }
    
    public Frame predictRules(Frame frame, String[] ruleIds) {
        Frame adaptFrm = new Frame(frame);
        adaptTestForTrain(adaptFrm, true, false);
        List<String> linVarNames = Arrays.asList(glmModel.names()).stream().filter(name -> name.startsWith("linear.")).collect(Collectors.toList());
        
        List<Rule> rules = new ArrayList<>();
        List<String> linearRules = new ArrayList<>();
        for (int i = 0; i < ruleIds.length; i++) {
            if (ruleIds[i].startsWith("linear.") && isLinearVar(ruleIds[i], linVarNames)) {
                linearRules.add(ruleIds[i]);
            } else {
                rules.add(ruleEnsemble.getRuleByVarName(RuleFitUtils.readRuleId(ruleIds[i])));
            }
        }
        RuleEnsemble subEnsemble = new RuleEnsemble(rules.toArray(new Rule[0]));
        Frame result = subEnsemble.transform(adaptFrm);
        // linear rules apply to all the rows
        for (int i = 0; i < linearRules.size(); i++) {
            result.add(linearRules.get(i), Vec.makeOne(frame.numRows()));
        }
        
        result = new Frame(Key.make(), result.names(), result.vecs());
        DKV.put(result);
        return result;
    }
    
    private boolean isLinearVar(String potentialLinVarId, List<String> linVarNames) {
        for (String linVarName : linVarNames) {
            if (potentialLinVarId.startsWith(linVarName))
                return true;
        }
        return false;
    }

    @Override
    public TwoDimTable getRuleImportanceTable() {
        return RuleFitUtils.convertRulesToTable(sortRules(deduplicateRules(RuleFitUtils.getRules(glmModel.coefficients(),
                ruleEnsemble, this._output.classNames(), this._output.nclasses()), _parms._remove_duplicates)), this._output.isClassifier() && this._output.nclasses() > 2, true);
    }
}
