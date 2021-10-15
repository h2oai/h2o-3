package hex.rulefit;

import hex.*;
import hex.genmodel.utils.DistributionFamily;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.quantile.Quantile;
import hex.quantile.QuantileModel;
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
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.TwoDimTable;

import java.util.*;
import java.util.stream.Collectors;

import static hex.genmodel.utils.ArrayUtils.difference;
import static hex.genmodel.utils.ArrayUtils.signum;


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
            // todo expose learn rate:
  //          ((GBMModel.GBMParameters) treeParameters)._learn_rate = 0.01;
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
        // todo expose as a new rfit param
       // treeParameters._sample_rate = 0.5;
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
        glmParameters._response_column = "linear." + _parms._response_column;
        glmParameters._seed = _parms._seed;
        // alpha ignored - set to 1 by rulefit (Lasso)
        glmParameters._alpha = new double[]{1};
        if (_parms._weights_column != null) {
            glmParameters._weights_column = "linear." + _parms._weights_column;
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

                    linearTrain.add(ruleEnsemble.createGLMTrainFrame(_train, depths.length, treeParameters._ntrees, classNames));
                    if (_valid != null) linearValid.add(ruleEnsemble.createGLMTrainFrame(_valid, depths.length, treeParameters._ntrees, classNames));
                }

                // prepare linear terms
                Key normalizedTrainKey = Key.make();
                Key winsorizedTrainKey = Key.make();
                Key winsorizedValidKey = null, normalizedValidKey = null;
                if (RuleFitModel.ModelType.RULES_AND_LINEAR.equals(_parms._model_type) || RuleFitModel.ModelType.LINEAR.equals(_parms._model_type)) {
                    String[] names = _train._names;
                    Vec[] winsorizedVecs = _parms._winsorising_fraction > 0 ? winsorize(_train.vecs(names), winsorizedTrainKey) : _train.vecs(names);
                    Vec[] linearTermsVecs = _parms._normalize ? normalize(winsorizedVecs, normalizedTrainKey) : winsorizedVecs;
                    linearTrain.add(RuleFitUtils.getLinearNames(names.length, names), linearTermsVecs);
                    if (_valid != null) {
                        winsorizedTrainKey = Key.make();
                        normalizedValidKey = Key.make();
                        winsorizedVecs = _parms._winsorising_fraction > 0 ? winsorize(_train.vecs(names), winsorizedTrainKey) : _train.vecs(names);
                        linearTermsVecs = _parms._normalize ? normalize(winsorizedVecs, normalizedValidKey) : winsorizedVecs;
                        linearValid.add(RuleFitUtils.getLinearNames(names.length, names), linearTermsVecs);
                    }
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
                    glmParameters._lambda = getOptimalLambda();
                }

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
                if (normalizedTrainKey != null) DKV.remove(normalizedTrainKey);
                if (normalizedValidKey != null) DKV.remove(normalizedValidKey);
                if (winsorizedTrainKey != null) DKV.remove(winsorizedTrainKey);
                if (winsorizedValidKey != null) DKV.remove(winsorizedValidKey);
                if (linearValid != null) DKV.remove(linearValid._key);
                DKV.remove(trainAdapted._key);
                
                // 3. Step 3 (optional): Feature importance
                model._output._intercept = getIntercept(glmModel);

                // TODO: add here coverage_count and coverage percent
                model._output._rule_importance = convertRulesToTable(getRules(glmModel.coefficients(), ruleEnsemble, model._output.classNames()), isClassifier() && nclasses() > 2);
                
                model._output._model_summary = generateSummary(glmModel, ruleEnsemble != null ? ruleEnsemble.size() : 0, overallTreeStats, ntrees);
                
                model._output._dataFromRulesCodes = dataFromRulesCodes;
                
                fillModelMetrics(model, glmModel);

                model.delete_and_lock(_job);
                model.update(_job);
            } finally {
                if (model != null) model.unlock(_job);
            }
        }
        
        // used on original features, so that they are more robust against outliers, before training linear model
        private Vec[] winsorize(Vec[] vecs, Key key) {
            // https://arxiv.org/pdf/1707.07149.pdf, section 2.2, eq. (10)
            // winsorized_xj = min (d+_j max(d-_j, x_j))
            // d+  = d+ quantile of the data distribution of feature xj
            // d-  = d- quantile of the data distribution of feature xj
            // default d = 0.025 as a rule of thumb
            Frame quantileTrain = new Frame(key, vecs);
            DKV.put(quantileTrain);
            
            QuantileModel.QuantileParameters quantileParameters = new QuantileModel.QuantileParameters();
            quantileParameters._train = quantileTrain._key;
            quantileParameters._probs = new double[] {_parms._winsorising_fraction, 1 - _parms._winsorising_fraction};
            QuantileModel quantileModel = new Quantile(quantileParameters).trainModel().get();
            double[][] quantiles = quantileModel._output._quantiles;
            DKV.remove(quantileModel.getKey());

            Winsorizer winsorizeTask = new Winsorizer(quantiles);
            byte[] types = new byte[vecs.length];
            Arrays.fill(types, Vec.T_NUM);
            Frame winsorizedFrame = winsorizeTask.doAll(types, vecs).outputFrame(key, null, null);
            DKV.put(winsorizedFrame);
            
            int responseId = ArrayUtils.find(_train._names, _parms._response_column);
            Vec[] result = new Vec[vecs.length];
            for (int i = 0; i < vecs.length; i++) {
                if (vecs[i].isNumeric() && (vecs[i].domain() == null) && (i != responseId)) {
                    result[i] = winsorizedFrame.vec(i);
                } else {
                    result[i] = vecs[i];
                }
            }
            return result;
        }

        class Winsorizer extends MRTask<Winsorizer> {
            double[][] quantiles;

            Winsorizer(double[][] quantiles) {
                this.quantiles = quantiles;
            }

            @Override
            public void map(Chunk[] cs, NewChunk[] nc) {
                for (int i = 0; i < cs.length; i++) {
                    for (int j = 0; j < cs[i].len(); j++) {
                        if (cs[i].vec().isNumeric()) {
                            nc[i].addNum( Math.min(quantiles[i][1], Math.max(quantiles[i][0], cs[i].atd(j))));
                        } else {
                            nc[i].addNA();
                        }
                    }
                }
            }
        }
        
        private Vec[] normalize(Vec[] vecs, Key key) {
            // https://arxiv.org/pdf/1707.07149.pdf, section 2.2, eq. (11)
            // normalized_xj = 0.4 * [winsorized_]xj/std([winsorized_]xj)
            // 0.4 is the average standard deviation of rules with a uniform support distribution
            Normalizer normalizeTask = new Normalizer(Arrays.stream(vecs).mapToDouble(Vec::sigma).toArray());
            byte[] types = new byte[vecs.length];
            Arrays.fill(types, Vec.T_NUM);
            
            Frame normalizedFrame = normalizeTask.doAll(types, vecs).outputFrame(key, null, null);
            DKV.put(normalizedFrame);

            int responseId = ArrayUtils.find(_train._names, _parms._response_column);
            Vec[] result = new Vec[vecs.length];
            for (int i = 0; i < vecs.length; i++) {
                if (vecs[i].isNumeric() && (vecs[i].domain() == null) && (i != responseId) && !vecs[i].isConst()) {// constant vecs have sigma = 0 -> in normalization there would be division by zero -> rather leave original const vec
                    result[i] = normalizedFrame.vec(i);
                } else {
                    result[i] = vecs[i];
                }
            }
            return result;
        }

        class Normalizer extends MRTask<Normalizer> {
            double[] _sigmas;

            Normalizer(double[] sigmas) {
                _sigmas = sigmas;
            }

            @Override
            public void map(Chunk[] cs, NewChunk[] nc) {
                for (int i = 0; i < cs.length; i++) {
                    for (int j = 0; j < cs[i].len(); j++) {
                        if (cs[i].vec().isNumeric()) {
                            nc[i].addNum(0.4 * cs[i].atd(j) / _sigmas[i]);
                        } else {
                            nc[i].addNA();
                        }
                   }
                }
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
                }
                rule.setCoefficient(entry.getValue());
                rules.add(rule);
            }
            Comparator<Rule> ruleAbsCoefficientComparator = Comparator.comparingDouble(Rule::getAbsCoefficient).reversed();
            rules.sort(ruleAbsCoefficientComparator);
            
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


