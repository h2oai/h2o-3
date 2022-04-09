package hex.rulefit;

import hex.*;
import hex.genmodel.utils.DistributionFamily;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.tree.SharedTree;
import hex.tree.SharedTreeModel;
import hex.tree.TreeStats;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;

import org.apache.log4j.Logger;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.TwoDimTable;

import java.util.*;
import java.util.stream.Collectors;

import static hex.genmodel.utils.ArrayUtils.difference;
import static hex.genmodel.utils.ArrayUtils.signum;
import static hex.rulefit.RuleFitUtils.sortRules;
import static hex.rulefit.RuleFitUtils.deduplicateRules;
import static hex.util.DistributionUtils.distributionToFamily;


/**
 * Rule Fit<br>
 * http://statweb.stanford.edu/~jhf/ftp/RuleFit.pdf
 * https://github.com/h2oai/h2o-tutorials/blob/8df6b492afa172095e2595922f0b67f8d715d1e0/best-practices/explainable-models/rulefit.py
 */
public class RuleFit extends ModelBuilder<RuleFitModel, RuleFitModel.RuleFitParameters, RuleFitModel.RuleFitOutput> {

    private static final Logger LOG = Logger.getLogger(RuleFit.class);
    
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
            _parms.validate(this);
            if (_parms._fold_column != null) {
                _train.remove(_parms._fold_column);
            }
            if (_parms._algorithm == RuleFitModel.Algorithm.AUTO) {
                _parms._algorithm = RuleFitModel.Algorithm.DRF;
            }

            initTreeParameters();
            initGLMParameters();
            ignoreBadColumns(separateFeatureVecs(), true);
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
            glmParameters = new GLMModel.GLMParameters(distributionToFamily(_parms._distribution));
        }
        if (RuleFitModel.ModelType.RULES_AND_LINEAR.equals(_parms._model_type) && _parms._ignored_columns != null) {
            glmParameters._ignored_columns = _parms._ignored_columns;
        }
        glmParameters._response_column = "linear." + _parms._response_column;
        glmParameters._seed = _parms._seed;
        // alpha ignored - set to 1 by rulefit (Lasso)
        glmParameters._alpha = new double[]{1};
        if (_parms._weights_column != null) {
            glmParameters._weights_column = "linear." + _parms._weights_column;
        }
        glmParameters._auc_type = _parms._auc_type;
        if (_parms._lambda != null) {
            glmParameters._lambda = _parms._lambda;
        }
    }


    private final class RuleFitDriver extends Driver {

        // Main worker thread
        @Override
        public void computeImpl() {
            String[] dataFromRulesCodes = null;
            RuleFitModel model = null;
            GLMModel glmModel;
            List<Rule> rulesList;
            RuleEnsemble ruleEnsemble = null;
            int ntrees = 0;
            TreeStats overallTreeStats = new TreeStats();
            String[] classNames = null;
            init(true);
            if (error_count() > 0)
                throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(RuleFit.this);

            try {
                // linearTrain = frame to be used as _train for GLM in 2., will be filled in 1.
                Frame linearTrain = new Frame(Key.make("paths_frame" + _result));
                Frame linearValid = (_valid != null ? new Frame(Key.make("valid_paths_frame" + _result)) : null);
                // store train frame without bad columns to pass it to tree model builders
                Frame trainAdapted = new Frame(_train);
                // 1. Rule generation
        
                // get paths from tree models
                int[] depths = range(_parms._min_rule_length, _parms._max_rule_length);
                
                // prepare rules
                if (RuleFitModel.ModelType.RULES_AND_LINEAR.equals(_parms._model_type) || RuleFitModel.ModelType.RULES.equals(_parms._model_type)) {
                    DKV.put(trainAdapted._key, trainAdapted);
                    treeParameters._train = trainAdapted._key;
                    long startAllTreesTime = System.nanoTime();
                    SharedTree<?, ?, ?>[] builders = ModelBuilderHelper.trainModelsParallel(
                            makeTreeModelBuilders(_parms._algorithm, depths), nTreeEnsemblesInParallel(depths.length));
                    rulesList = new ArrayList<>();
                    for (int modelId = 0; modelId < builders.length; modelId++) {
                        long startModelTime = System.nanoTime();
                        SharedTreeModel<?, ?, ?> treeModel = builders[modelId].get();
                        long endModelTime = System.nanoTime() - startModelTime;
                        LOG.info("Tree model n." + modelId + " trained in " + ((double)endModelTime) / 1E9 + "s.");
                        rulesList.addAll(Rule.extractRulesListFromModel(treeModel, modelId, nclasses()));
                        overallTreeStats.mergeWith(treeModel._output._treeStats);
                        ntrees += treeModel._output._ntrees;
                        if (classNames == null) {
                            classNames = treeModel._output.classNames();
                        }
                        treeModel.delete();
                    }
                    long endAllTreesTime = System.nanoTime() - startAllTreesTime;
                    LOG.info("All tree models trained in " + ((double)endAllTreesTime) / 1E9 + "s.");

                    LOG.info("Extracting rules from trees...");
                    ruleEnsemble = new RuleEnsemble(rulesList.toArray(new Rule[] {}));

                    linearTrain.add(ruleEnsemble.createGLMTrainFrame(_train, depths.length, treeParameters._ntrees, classNames, _parms._weights_column, true));
                    if (_valid != null) linearValid.add(ruleEnsemble.createGLMTrainFrame(_valid, depths.length, treeParameters._ntrees, classNames, _parms._weights_column, false));
                }

                // prepare linear terms
                if (RuleFitModel.ModelType.RULES_AND_LINEAR.equals(_parms._model_type) || RuleFitModel.ModelType.LINEAR.equals(_parms._model_type)) {
                    String[] names = _train._names;
                    linearTrain.add(RuleFitUtils.getLinearNames(names.length, names), _train.vecs(names));
                    if (_valid != null) linearValid.add(RuleFitUtils.getLinearNames(names.length, names), _valid.vecs(names));
                } else {
                    linearTrain.add(glmParameters._response_column, _train.vec(_parms._response_column));
                    if (_valid != null) linearValid.add(glmParameters._response_column, _valid.vec(_parms._response_column));
                    if (_parms._weights_column != null) {
                        linearTrain.add(glmParameters._weights_column, _train.vec(_parms._weights_column));
                        if (_valid != null) linearValid.add(glmParameters._weights_column, _valid.vec(_parms._weights_column));
                    }
                }
                dataFromRulesCodes = linearTrain.names();
                DKV.put(linearTrain);
                if (_valid != null) {
                    DKV.put(linearValid);
                    glmParameters._valid = linearValid._key;
                }

                // 2. Sparse linear model with Lasso
                glmParameters._train = linearTrain._key;
                if (_parms._max_num_rules > 0) {
                    glmParameters._max_active_predictors = _parms._max_num_rules + 1;
                    if (_parms._distribution != DistributionFamily.multinomial) {
                        glmParameters._solver = GLMModel.GLMParameters.Solver.COORDINATE_DESCENT;
                    }
                } else {
                    if (glmParameters._lambda != null)
                        glmParameters._lambda = getOptimalLambda();
                }

                LOG.info("Training GLM...");
                long startGLMTime = System.nanoTime();
                GLM job = new GLM(glmParameters);
                glmModel = job.trainModel().get();
                long endGLMTime = System.nanoTime() - startGLMTime;
                LOG.info("GLM trained in " + ((double)endGLMTime) / 1E9 + "s.");
                DKV.put(glmModel);
                
                model = new RuleFitModel(dest(), _parms, new RuleFitModel.RuleFitOutput(RuleFit.this), glmModel, ruleEnsemble);
                
                model._output.glmModelKey = glmModel._key;

                model._output._linear_names = linearTrain.names();

                DKV.remove(linearTrain._key);
                if (linearValid != null) DKV.remove(linearValid._key);
                DKV.remove(trainAdapted._key);
                
                // 3. Step 3 (optional): Feature importance
                model._output._intercept = getIntercept(glmModel);

                // TODO: add here coverage_count and coverage percent
                model._output._rule_importance = convertRulesToTable(sortRules(deduplicateRules(getRules(glmModel.coefficients(), 
                        ruleEnsemble, model._output.classNames()), _parms._remove_duplicates)), isClassifier() && nclasses() > 2);

                model._output._model_summary = generateSummary(glmModel, ruleEnsemble != null ? ruleEnsemble.size() : 0, overallTreeStats, ntrees);
                
                model._output._dataFromRulesCodes = dataFromRulesCodes;
                
                fillModelMetrics(model, glmModel);

                model.delete_and_lock(_job);
                model.update(_job);
            } finally {
                if (model != null) model.unlock(_job);
            }
        }
        
        void fillModelMetrics(RuleFitModel model, GLMModel glmModel) {
            model._output._validation_metrics = glmModel._output._validation_metrics;
            model._output._training_metrics = glmModel._output._training_metrics;
            model._output._cross_validation_metrics = glmModel._output._cross_validation_metrics;
            model._output._cross_validation_metrics_summary = glmModel._output._cross_validation_metrics_summary;

            Frame inputTrain = model._parms._train.get();
            for (Key<ModelMetrics> modelMetricsKey : glmModel._output.getModelMetrics()) {
                model.addModelMetrics(modelMetricsKey.get().deepCloneWithDifferentModelAndFrame(model, inputTrain));
            }
        }

        int[] range(int min, int max) {
            int[] array = new int[max - min + 1];
            for (int i = min, j = 0; i <= max; i++, j++) {
                array[j] = i;
            }
            return array;
        }

        SharedTree<?, ?, ?> makeTreeModelBuilder(RuleFitModel.Algorithm algorithm, int maxDepth) {
            SharedTreeModel.SharedTreeParameters p = (SharedTreeModel.SharedTreeParameters) treeParameters.clone();
            p._max_depth = maxDepth;

            final SharedTree<?, ?, ?> builder;
            if (algorithm.equals(RuleFitModel.Algorithm.DRF)) {
                builder = new DRF((DRFModel.DRFParameters) p);
            } else if (algorithm.equals(RuleFitModel.Algorithm.GBM)) {
                builder = new GBM((GBMModel.GBMParameters) p);
            } else {
                // TODO XGB
                throw new RuntimeException("Unsupported algorithm for tree building: " + _parms._algorithm);
            }
            
            return builder;
        }

        SharedTree<?, ?, ?>[] makeTreeModelBuilders(RuleFitModel.Algorithm algorithm, int[] depths) {
            SharedTree<?, ?, ?>[] builders = new SharedTree[depths.length];
            for (int i = 0; i < depths.length; i++) {
                builders[i] = makeTreeModelBuilder(algorithm, depths[i]);
            }
            return builders;
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
               bestLambdaIndex = getBestLambdaIndex(deviance);
               if (bestLambdaIndex >= lambdas.length) {
                   bestLambdaIndex = getBestLambdaIndexCornerCase(deviance, lambdas);
               }
           }

            lambdaModel.remove();
            return new double[]{lambdas[bestLambdaIndex]};
        }

        int getBestLambdaIndex(double[] deviance) {
            int bestLambdaIndex = deviance.length - 1;
            if (deviance.length >= 5) {
                double[] array = difference(signum(difference(difference(deviance))));
                for (int i = 0; i < array.length; i++) {
                    if (array[i] != 0 && i > 0) {
                        bestLambdaIndex = 3 * i;
                        break;
                    }
                }
            }
            return bestLambdaIndex;
        }

        int getBestLambdaIndexCornerCase(double[] deviance, double[] lambdas) {
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
            double[] intercept = nclasses() > 2 ? new double[nclasses()] : new double[1];
            int i = 0;
            for (Map.Entry<String, Double> coefficient : glmCoefficients.entrySet()) {
                if ("Intercept".equals(coefficient.getKey()) || coefficient.getKey().contains("Intercept_")) {
                    intercept[i] = coefficient.getValue();
                    i++;
                }
            }
            return intercept;
        }


        Rule[] getRules(HashMap<String, Double> glmCoefficients, RuleEnsemble ruleEnsemble, String[] classNames) {
            // extract variable-coefficient map (filter out intercept and zero betas)
            Map<String, Double> filteredRules = glmCoefficients.entrySet()
                    .stream()
                    .filter(e -> !("Intercept".equals(e.getKey()) || e.getKey().contains("Intercept_")) && 0 != e.getValue())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            
            List<Rule> rules = new ArrayList<>();
            Rule rule;
            for (Map.Entry<String, Double> entry : filteredRules.entrySet()) {
                if (!entry.getKey().startsWith("linear.")) {
                    rule = ruleEnsemble.getRuleByVarName(getVarName(entry.getKey(), classNames));
                } else {
                    rule = new Rule(null, entry.getValue(), entry.getKey());
                    // linear rule applies to all the rows
                    rule.support = 1.0;
                }
                rule.setCoefficient(entry.getValue());
                rules.add(rule);
            }
            
            return rules.toArray(new Rule[] {});
        }
    }
    
    private String getVarName(String ruleKey, String[] classNames) {
        if (nclasses() > 2) {
            ruleKey = removeClassNameSuffix(ruleKey, classNames);
        }
        return ruleKey.substring(ruleKey.lastIndexOf(".") + 1);
    }
    
    private String removeClassNameSuffix(String ruleKey, String[] classNames) {
        for (int i = 0; i < classNames.length; i++) {
            if (ruleKey.endsWith(classNames[i]))
                return ruleKey.substring(0, ruleKey.length() - classNames[i].length() - 1);
        }
        return ruleKey;
    }

    private TwoDimTable convertRulesToTable(Rule[] rules, boolean isMultinomial) {
        List<String> colHeaders = new ArrayList<>();
        List<String> colTypes = new ArrayList<>();
        List<String> colFormat = new ArrayList<>();

        colHeaders.add("variable");
        colTypes.add("string");
        colFormat.add("%s");
        if (isMultinomial) {
            colHeaders.add("class");
            colTypes.add("string");
            colFormat.add("%s");
        }
        colHeaders.add("coefficient");
        colTypes.add("double");
        colFormat.add("%.5f");
        colHeaders.add("support");
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
            String varname = (rules[row]).varName;
            table.set(row, col++, varname);
            if (isMultinomial) {
                String segments[] = varname.split("_");
                table.set(row, col++, segments[segments.length - 1]);
            }
            table.set(row, col++, (rules[row]).coefficient);
            table.set(row, col++, (rules[row]).support);
            table.set(row, col, (rules[row]).languageRule);
        }

        return table;
    }

    protected int nTreeEnsemblesInParallel(int numDepths) {
        if (_parms._algorithm == RuleFitModel.Algorithm.GBM) {
            return nModelsInParallel(numDepths, 2);
        } else {
            return nModelsInParallel(numDepths, 1);
        }
    }

    TwoDimTable generateSummary(GLMModel glmModel, int ruleEnsembleSize, TreeStats overallTreeStats, int ntrees) {
        List<String> colHeaders = new ArrayList<>();
        List<String> colTypes = new ArrayList<>();
        List<String> colFormats = new ArrayList<>();

        TwoDimTable glmModelSummary = glmModel._output._model_summary;
        String[] glmColHeaders = glmModelSummary.getColHeaders();
        String[] glmColTypes = glmModelSummary.getColTypes();
        String[] glmColFormats = glmModelSummary.getColFormats();
        // linear model info
        for (int i = 0; i < glmModelSummary.getColDim(); i++) {
            if (!"Training Frame".equals(glmColHeaders[i])) {
                colHeaders.add(glmColHeaders[i]);
                colTypes.add(glmColTypes[i]);
                colFormats.add(glmColFormats[i]);
            }
        }
        // rule ensemble info
        colHeaders.add("Rule Ensemble Size"); colTypes.add("long"); colFormats.add("%d");
        // trees info
        colHeaders.add("Number of Trees"); colTypes.add("long"); colFormats.add("%d");
        colHeaders.add("Number of Internal Trees"); colTypes.add("long"); colFormats.add("%d");
        colHeaders.add("Min. Depth"); colTypes.add("long"); colFormats.add("%d");
        colHeaders.add("Max. Depth"); colTypes.add("long"); colFormats.add("%d");
        colHeaders.add("Mean Depth"); colTypes.add("double"); colFormats.add("%.5f");
        colHeaders.add("Min. Leaves"); colTypes.add("long"); colFormats.add("%d");
        colHeaders.add("Max. Leaves"); colTypes.add("long"); colFormats.add("%d");
        colHeaders.add("Mean Leaves"); colTypes.add("double"); colFormats.add("%.5f");

        final int rows = 1;
        TwoDimTable summary = new TwoDimTable(
                "Rulefit Model Summary", null,
                new String[rows],
                colHeaders.toArray(new String[0]),
                colTypes.toArray(new String[0]),
                colFormats.toArray(new String[0]),
                "");
        int col = 0, row = 0;
        for (int i = 0; i < glmModelSummary.getColDim(); i++) {
            if (!"Training Frame".equals(glmColHeaders[i])) {
                summary.set(row, col++, glmModelSummary.get(row, i));
            }
        }
        summary.set(row, col++, ruleEnsembleSize);
        summary.set(row, col++, ntrees);
        summary.set(row, col++, overallTreeStats._num_trees); //internal number of trees (more for multinomial)
        summary.set(row, col++, overallTreeStats._min_depth);
        summary.set(row, col++, overallTreeStats._max_depth);
        summary.set(row, col++, overallTreeStats._mean_depth);
        summary.set(row, col++, overallTreeStats._min_leaves);
        summary.set(row, col++, overallTreeStats._max_leaves);
        summary.set(row, col++, overallTreeStats._mean_leaves);

        return summary;
    }
    
    @Override
    public boolean haveMojo() { return true; }
    
}


