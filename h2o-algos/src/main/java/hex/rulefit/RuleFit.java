package hex.rulefit;

import hex.*;
import hex.genmodel.utils.DistributionFamily;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.tree.SharedTreeModel;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;

import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.util.TwoDimTable;

import java.util.*;
import java.util.stream.Collectors;


/**
 * Rule Fit<br>
 * http://statweb.stanford.edu/~jhf/ftp/RuleFit.pdf
 * https://github.com/h2oai/h2o-tutorials/blob/8df6b492afa172095e2595922f0b67f8d715d1e0/best-practices/explainable-models/rulefit.py
 */
public class RuleFit extends ModelBuilder<RuleFitModel, RuleFitModel.RuleFitParameters, RuleFitModel.RuleFitOutput> {

    protected static final long WORK_TOTAL = 1000000;

    private SharedTreeModel.SharedTreeParameters treeParameters = null;

    private GLMModel.GLMParameters glmParameters = null;

    @Override
    public ModelCategory[] can_build() {
        return new ModelCategory[]{
                ModelCategory.Regression,
                ModelCategory.Binomial,
                ModelCategory.Multinomial
        };
    }

    @Override
    public boolean isSupervised() {
        return true;
    }

    /**
     * Start the KMeans training Job on an F/J thread.
     */
    @Override
    protected RuleFitDriver trainModelImpl() {
        return new RuleFitDriver();
    }

    // Called from an http request
    public RuleFit(RuleFitModel.RuleFitParameters parms) {
        super(parms);
        init(false);
    }

    public RuleFit(boolean startup_once) {
        super(new RuleFitModel.RuleFitParameters(), startup_once);
    }

    @Override
    public void init(boolean expensive) {
        super.init(expensive);
        if (expensive) {
            if (_parms._fold_column != null) {
                _train.remove(_parms._fold_column);
            }
            if (_parms._algorithm == RuleFitModel.Algorithm.AUTO) {
                _parms._algorithm = RuleFitModel.Algorithm.DRF;
            }

            initTreeParameters();
            initGLMParameters();
        }
        //   if (_train == null) return;
        // if (expensive && error_count() == 0) checkMemoryFootPrint();
    }

    private void initTreeParameters() {
        if (_parms._algorithm == RuleFitModel.Algorithm.GBM) {
            treeParameters = new GBMModel.GBMParameters();
        } else if (_parms._algorithm == RuleFitModel.Algorithm.DRF) {
            treeParameters = new DRFModel.DRFParameters();
        } else {
            throw new RuntimeException("Unsupported algorithm for tree building: " + _parms._algorithm);
        }
        treeParameters._response_column = _parms._response_column;
        treeParameters._train = _parms._train;
        treeParameters._ignored_columns = _parms._ignored_columns;
        treeParameters._seed = _parms._seed;
        treeParameters._weights_column = _parms._weights_column;
        treeParameters._distribution = _parms._distribution;
        treeParameters._ntrees = _parms._rule_generation_ntrees;
    }

    private void initGLMParameters() {
        if (_parms._distribution == DistributionFamily.AUTO) {
            if (_nclass < 2) { // regression
                glmParameters = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.gaussian);
            } else if (_nclass == 2) { // binomial classification
                glmParameters = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.binomial);
            } else { // multinomial classification
                glmParameters = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.multinomial);
            }
        } else {
            switch (_parms._distribution) {
                case bernoulli:
                    glmParameters = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.binomial);
                    break;
                case quasibinomial:
                    glmParameters = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.quasibinomial);
                    break;
                case multinomial:
                    glmParameters = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.multinomial);
                    break;
                case ordinal:
                    glmParameters = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.ordinal);
                    break;
                case gaussian:
                    glmParameters = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.gaussian);
                    break;
                case poisson:
                    glmParameters = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.poisson);
                    break;
                case gamma:
                    glmParameters = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.gamma);
                    break;
                case tweedie:
                    glmParameters = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.tweedie);
                    break;
                case fractionalbinomial:
                    glmParameters = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.fractionalbinomial);
                    break;
                case negativebinomial:
                    glmParameters = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.negativebinomial);
                    break;
                default:
                    error("_distribution", "Distribution not supported.");
            }
        }
        if (RuleFitModel.ModelType.RULES_AND_LINEAR.equals(_parms._model_type) && _parms._ignored_columns != null) {
            glmParameters._ignored_columns = _parms._ignored_columns;
        }
        glmParameters._response_column = _parms._response_column;
        glmParameters._seed = _parms._seed;
        // alpha ignored - set to 1 by rulefit (Lasso)
        glmParameters._alpha = new double[]{1};
        glmParameters._weights_column = _parms._weights_column;
    }


    private final class RuleFitDriver extends Driver {

        // Main worker thread
        @Override
        public void computeImpl() {
            RuleFitModel model = null;
            GLMModel glmModel;
            List<Rule> rulesList;
            RuleEnsemble ruleEnsemble = null;
            init(true);
            if (error_count() > 0)
                throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(RuleFit.this);

            try {
                // 1. Rule generation

                // get paths from tree models
                Frame pathsFrame = new Frame(Key.make("paths_frame" + _result));
                int[] depths = range(_parms._min_rule_length, _parms._max_rule_length + 1);
                List<SharedTreeModel> treeModels = new ArrayList<>();

                pathsFrame.add(_parms._response_column, _response.makeCopy());
                if (_parms._weights_column != null) {
                    pathsFrame.add(_parms._weights_column, _weights.makeCopy());
                }
                // prepare rules
                if (RuleFitModel.ModelType.RULES_AND_LINEAR.equals(_parms._model_type) || RuleFitModel.ModelType.RULES.equals(_parms._model_type)) {
                    rulesList = new ArrayList<>();
                    for (int modelId = 0; modelId < depths.length; modelId++) {
                        SharedTreeModel treeModel = trainTreeModel(_parms._algorithm, depths[modelId]);
                        rulesList.addAll(Rule.extractRulesListFromModel(treeModel, modelId));
                        treeModel.delete();
                    }
                    ruleEnsemble = new RuleEnsemble(rulesList.toArray(new Rule[] {}));
                    
                    pathsFrame.add(ruleEnsemble.createGLMTrainFrame(_train, depths.length, treeParameters._ntrees));
                }
                // prepare linear terms
                if (RuleFitModel.ModelType.RULES_AND_LINEAR.equals(_parms._model_type) || RuleFitModel.ModelType.LINEAR.equals(_parms._model_type)) {
                    Frame adaptFrm = new Frame(_train.deepCopy(null));
                    adaptFrm.remove(_parms._response_column);
                    adaptFrm.setNames(RuleFitUtils.getLinearNames(adaptFrm.numCols(), adaptFrm.names()));
                    pathsFrame.add(adaptFrm);
                }
                DKV.put(pathsFrame);

                // 2. Sparse linear model with Lasso
                glmParameters._train = pathsFrame._key;
                if (_parms._max_num_rules > 0) {
                    glmParameters._max_active_predictors = _parms._max_num_rules + 1;
                    if (_parms._distribution != DistributionFamily.multinomial) {
                        glmParameters._solver = GLMModel.GLMParameters.Solver.COORDINATE_DESCENT;
                    }
                } else {
                    glmParameters._lambda = getOptimalLambda();
                }

                GLM job = new GLM(glmParameters);
                glmModel = job.trainModel().get();
                DKV.put(glmModel);

                model = new RuleFitModel(dest(), _parms, new RuleFitModel.RuleFitOutput(RuleFit.this), glmModel, ruleEnsemble);

                model._output.treeModelsKeys = new Key[treeModels.size()];
                for (int modelId = 0; modelId < treeModels.size(); modelId++) {
                    model._output.treeModelsKeys[modelId] = treeModels.get(modelId)._key;
                }
                model._output.glmModelKey = glmModel._key;

                // 3. Step 3 (optional): Feature importance
                model._output._intercept = getIntercept(glmModel);

                // TODO: add here coverage_count and coverage percent
                model._output._rule_importance = convertRulesToTable(getRules(glmModel.coefficients(), ruleEnsemble));
                
                pathsFrame.remove();

                model.delete_and_lock(_job);
                model.update(_job);
            } finally {
                if (model != null) model.unlock(_job);
            }
        }

        int[] range(int min, int max) {
            int[] array = new int[max - min + 1];
            for (int i = min, j = 0; i <= max; i++, j++) {
                array[j] = i;
            }
            return array;
        }

        SharedTreeModel trainTreeModel(RuleFitModel.Algorithm algorithm, int maxDepth) {
            SharedTreeModel treeModel;
            treeParameters._max_depth = maxDepth;

            if (algorithm.equals(RuleFitModel.Algorithm.DRF)) {
                DRF job = new DRF((DRFModel.DRFParameters) treeParameters);
                treeModel = job.trainModel().get();

            } else if (algorithm.equals(RuleFitModel.Algorithm.GBM)) {
                GBM job = new GBM((GBMModel.GBMParameters) treeParameters);
                treeModel = job.trainModel().get();
            } else {
                // TODO XGB
                throw new RuntimeException("Unsupported algorithm for tree building: " + _parms._algorithm);
            }

            return treeModel;
        }

        double[] getOptimalLambda() {
            glmParameters._lambda_search = true;

            GLM job = new GLM(glmParameters);
            GLMModel lambdaModel = job.trainModel().get();
            glmParameters._lambda_search = false;

            GLMModel.RegularizationPath regularizationPath = lambdaModel.getRegularizationPath();
            double[] deviance = regularizationPath._explained_deviance_train;
            double[] lambdas = regularizationPath._lambdas;
            int bestLambdaIndex;

           if (deviance.length < 5) {
               bestLambdaIndex = deviance.length - 1;
           } else {
               bestLambdaIndex = getBestLambdaIndex(deviance, lambdas);
           }

            lambdaModel.remove();
            return new double[]{lambdas[bestLambdaIndex]};
        }

        int getBestLambdaIndex(double[] deviance, double[] lambdas) {
            double[] leftUpPoint = new double[] {deviance[0], lambdas[0]};
            double[] rightLowPoint = new double[] {deviance[deviance.length - 1], lambdas[lambdas.length - 1]};
            double[] leftActualPoint = new double[2];
            double[] rightActualPoint = new double[2];
            double leftVolume, rightVolume;
            
            int leftActualId = 0;
            int rightActualId = deviance.length - 1;
            while (leftActualId < deviance.length && rightActualId < deviance.length) {
                 if (leftActualId >= rightActualId) // volumes overlap
                    break;
                 
                // leftVolume
                leftActualPoint[0] = deviance[leftActualId];
                leftActualPoint[1] = lambdas[leftActualId];
                leftVolume = (leftUpPoint[1] - leftActualPoint[1]) * (leftActualPoint[0] - leftUpPoint[0]);
                
                // rightVolume
                rightActualPoint[0] = deviance[rightActualId];
                rightActualPoint[1] = lambdas[rightActualId];
                rightVolume = (rightActualPoint[1] - rightLowPoint[1]) * (rightLowPoint[0] - rightActualPoint[0]);
                
                if (Math.abs(leftVolume) > Math.abs(rightVolume)) {
                    rightActualId--; // add point to rightvolume
                } else {
                    leftActualId++; // add point to leftvolume
                }
            }
            return rightActualId;
        }

        double[] getIntercept(GLMModel glmModel) {
            HashMap<String, Double> glmCoefficients = glmModel.coefficients();
            for (Map.Entry<String, Double> coefficient : glmCoefficients.entrySet()) {
                if ("Intercept".equals(coefficient.getKey()))
                    return new double[]{coefficient.getValue()};
            }
            return new double[]{};
        }


        Rule[] getRules(HashMap<String, Double> glmCoefficients, RuleEnsemble ruleEnsemble) {
            // extract variable-coefficient map (filter out intercept and zero betas)
            Map<String, Double> filteredRules = glmCoefficients.entrySet()
                    .stream()
                    .filter(e -> !"Intercept".equals(e.getKey()) && 0 != e.getValue())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            
            List<Rule> rules = new ArrayList<>();
            Rule rule;
            for (Map.Entry<String, Double> entry : filteredRules.entrySet()) {
                if (!entry.getKey().startsWith("linear.")) {
                    rule = ruleEnsemble.getRuleByVarName(entry.getKey().substring(entry.getKey().lastIndexOf(".") + 1));
                } else {
                    rule = new Rule(null, entry.getValue(), entry.getKey());
                }
                rule.setCoefficient(entry.getValue());
                rules.add(rule);
            }
            Comparator<Rule> ruleAbsCoefficientComparator = Comparator.comparingDouble(Rule::getAbsCoefficient).reversed();
            rules.sort(ruleAbsCoefficientComparator);
            
            return rules.toArray(new Rule[] {});
        }
    }

    private TwoDimTable convertRulesToTable(Rule[] rules) {
        List<String> colHeaders = new ArrayList<>();
        List<String> colTypes = new ArrayList<>();
        List<String> colFormat = new ArrayList<>();

        colHeaders.add("variable");
        colTypes.add("string");
        colFormat.add("%s");
        colHeaders.add("coefficient");
        colTypes.add("double");
        colFormat.add("%.5f");
        colHeaders.add("rule");
        colTypes.add("string");
        colFormat.add("%s");

        final int rows = rules.length;
        TwoDimTable table = new TwoDimTable("Rule Importance", null, new String[rows],
                colHeaders.toArray(new String[0]), colTypes.toArray(new String[0]), colFormat.toArray(new String[0]), "");

        for (int row = 0; row < rows; row++) {
            int col = 0;
            table.set(row, col++, (rules[row]).varName);
            table.set(row, col++, (rules[row]).coefficient);
            table.set(row, col, (rules[row]).languageRule);
        }

        return table;
    }
}


