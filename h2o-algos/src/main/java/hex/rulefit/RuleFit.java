package hex.rulefit;

import hex.*;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.schemas.TreeV3;
import hex.tree.SharedTreeModel;
import hex.tree.TreeHandler;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;

import water.*;
import water.api.schemas3.KeyV3;
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
            // try to set _nfolds to 5

            initTreeParameters();
            initGLMParameters();
        }
        //   if (_train == null) return;
        // if (expensive && error_count() == 0) checkMemoryFootPrint();
    }

    private void initTreeParameters() {
        if (_parms._tree_params == null) {
            if (_parms._algorithm == RuleFitModel.Algorithm.GBM) {
                _parms._tree_params = new GBMModel.GBMParameters();
            } else if (_parms._algorithm == RuleFitModel.Algorithm.DRF) {
                _parms._tree_params = new DRFModel.DRFParameters();
            } else {
                throw new RuntimeException("Unsupported algorithm for tree building: " + _parms._algorithm);
            }
            _parms._tree_params._response_column = _parms._response_column;
            _parms._tree_params._train = _parms._train;
        } else {
            // max_depth provided in tree_params - min_rule_len and max_rule_len will be ignored
            _parms._min_rule_length = _parms._tree_params._max_depth;
            _parms._max_rule_length = _parms._tree_params._max_depth;
        }
        _parms._tree_params._seed = _parms._seed;
    }

    private void initGLMParameters() {
        if (_parms._glm_params == null) {
            if (_nclass < 2) { // regression
                _parms._glm_params = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.gaussian);
            } else if (_nclass == 2) { // binomial classification
                _parms._glm_params = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.binomial);
            } else { // multinomial classification
                // TODO: multinomial cases not supported yet
                throw new UnsupportedOperationException("Multinomial cases are not supported yet for RuleFit.");
            }
            _parms._glm_params._response_column = _parms._response_column;
        } else {
            // lambda_ ignored by rulefit
            _parms._glm_params._lambda = null;
        }
        _parms._glm_params._seed = _parms._seed;
        // alpha ignored - set to 1 by rulefit (Lasso)
        _parms._glm_params._alpha = new double[]{1};

    }


    private final class RuleFitDriver extends Driver {

        // Main worker thread
        @Override
        public void computeImpl() {
            RuleFitModel model = null;
            GLMModel glmModel;
            init(true);
            if (error_count() > 0)
                throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(RuleFit.this);

            try {
                // 1. Rule generation

                // get paths from random forrest models
                Frame pathsFrame = new Frame(Key.make("paths_frame" + _result));
                int[] depths = range(_parms._min_rule_length, _parms._max_rule_length + 1);
                List<SharedTreeModel> treeModels = new ArrayList<SharedTreeModel>();

                pathsFrame.add(_parms._response_column, _response.makeCopy());
                // TODO: move this to some distributed task
                Frame paths = null;
                Key[] keys = new Key[depths.length];
                for (int modelId = 0; modelId < depths.length; modelId++) {
                    SharedTreeModel treeModel = trainTreeModel(_parms._algorithm, depths[modelId]);
                    treeModels.add(treeModel);

                    paths = treeModel.scoreLeafNodeAssignment(_train, Model.LeafNodeAssignment.LeafNodeAssignmentType.Path, Key.make("path_" + modelId + _result));
                    paths.setNames(RuleFitUtils.getPathNames(modelId, paths.numCols(), paths.names()));
                    pathsFrame.add(paths);

                    keys[modelId] = paths._key;
                    DKV.put(paths);
                    DKV.put(treeModel);
                }
                DKV.put(pathsFrame);

                // 2. Sparse linear model with Lasso
                _parms._glm_params._train = pathsFrame._key;
                if (_parms._max_num_rules != null) {
                    _parms._glm_params._max_active_predictors = _parms._max_num_rules + 1;
                    _parms._glm_params._solver = GLMModel.GLMParameters.Solver.COORDINATE_DESCENT;
                } else {
                    _parms._glm_params._lambda = getOptimalLambda();
                    _parms._glm_params._nfolds = _parms._nfolds;
                }

                GLM job = new GLM(_parms._glm_params);
                glmModel = job.trainModel().get();
                DKV.put(glmModel);

                SharedTreeModel[] treeModelsArray = new SharedTreeModel[treeModels.size()];
                for (int i = 0; i < treeModels.size(); i++) {
                    treeModelsArray[i] = treeModels.get(i);
                }

                model = new RuleFitModel(dest(), _parms, new RuleFitModel.RuleFitOutput(RuleFit.this), treeModelsArray, glmModel);

                model._output.treeModelsKeys = new Key[treeModels.size()];
                for (int modelId = 0; modelId < treeModels.size(); modelId++) {
                    model._output.treeModelsKeys[modelId] = treeModels.get(modelId)._key;
                }
                model._output.glmModelKey = glmModel._key;

                // 3. Step 3 (optional): Feature importance
                model._output._intercept = getIntercept(glmModel);

                // TODO: add here coverage_count and coverage percent
                model._output._rule_importance = convertRulesToTable(getRules(glmModel._parms._family, glmModel.coefficients(), treeModels, _parms._algorithm));

                for (Key key : keys) {
                    DKV.remove(key);
                }
                pathsFrame.remove();

                if (paths != null) {
                    paths.remove();
                }

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
            _parms._tree_params._max_depth = maxDepth;

            if (algorithm.equals(RuleFitModel.Algorithm.DRF)) {
                DRF job = new DRF((DRFModel.DRFParameters) _parms._tree_params);
                treeModel = job.trainModel().get();

            } else if (algorithm.equals(RuleFitModel.Algorithm.GBM)) {
                GBM job = new GBM((GBMModel.GBMParameters) _parms._tree_params);
                treeModel = job.trainModel().get();
            } else {
                // TODO XGB
                throw new RuntimeException("Unsupported algorithm for tree building: " + _parms._algorithm);
            }

            return treeModel;
        }

        double[] getOptimalLambda() {
            _parms._glm_params._lambda_search = true;

            GLM job = new GLM(_parms._glm_params);
            GLMModel lambdaModel = job.trainModel().get();
            _parms._glm_params._lambda_search = false;

            // get the best GLM lambda by choosing one diminishing returns on explained deviance
            GLMModel.RegularizationPath regularizationPath = lambdaModel.getRegularizationPath();
            double[] deviance = regularizationPath._explained_deviance_train;
            double[] lambdas = regularizationPath._lambdas;
            int bestLambdaIndex;

            if (deviance.length < 5) {
                bestLambdaIndex = deviance.length - 1;
            } else {
                bestLambdaIndex = getBestLambdaIndex(deviance);
            }

            lambdaModel.remove();
            return new double[]{lambdas[bestLambdaIndex]};
        }

        double[] differenceArray(double[] array) {
            double[] differenceArray = new double[array.length - 1];
            for (int i = 0; i < array.length - 1; i++) {
                differenceArray[i] = array[i + 1] - array[i];
            }
            return differenceArray;
        }

        double[] signArray(double[] array) {
            double[] signArray = new double[array.length];
            for (int i = 0; i < array.length; i++) {
                if (array[i] > 0)
                    signArray[i] = 1;
                else if (array[i] < 0)
                    signArray[i] = -1;
                else
                    signArray[i] = 0;
            }
            return signArray;
        }

        int getBestLambdaIndex(double[] deviance) {
            int bestLambdaIndex = deviance.length - 1;
            if (deviance.length >= 5) {
                double[] array = differenceArray(signArray(differenceArray(differenceArray(deviance))));
                for (int i = 0; i < array.length; i++) {
                    if (array[i] != 0 && i > 0) {
                        bestLambdaIndex = 3 * i;
                        break;
                    }
                }
            }
            return bestLambdaIndex;
        }

        double[] getIntercept(GLMModel glmModel) {
            GLMModel.GLMParameters.Family family = glmModel._parms._family;
            // get paths
            HashMap<String, Double> glmCoefficients = glmModel.coefficients();
            if (GLMModel.GLMParameters.Family.multinomial.equals(family)) {
                // TODO multinomial here
                return new double[]{};
            } else {
                for (Map.Entry<String, Double> coefficient : glmCoefficients.entrySet()) {
                    if ("Intercept".equals(coefficient.getKey()))
                        return new double[]{coefficient.getValue()};
                }
            }
            return new double[]{};
        }


        List getRules(GLMModel.GLMParameters.Family family, HashMap<String, Double> glmCoefficients, List<SharedTreeModel> treeModels, RuleFitModel.Algorithm algorithm) {
            // extract variable-coefficient map
            Map<String, Double> filteredRules = glmCoefficients.entrySet()
                    .stream()
                    .filter(e -> !"Intercept".equals(e.getKey()) && 0 != e.getValue())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            final TreeHandler treeHandler = new TreeHandler();
            List<Rule> rules = new ArrayList<>();
            TreeV3 args;
            TreeV3 tree;

            for (Map.Entry<String, Double> entry : filteredRules.entrySet()) {
                Rule rule = mapColumnName(entry.getKey(), family);

                args = new TreeV3();
                args.model = new KeyV3.ModelKeyV3(treeModels.get(rule.modelIdx)._key);
                args.tree_class = rule.treeClass;
                args.tree_number = rule.treeNum;

                tree = treeHandler.getTree(3, args);
                rule.languageRule = treeTraverser(tree, rule.path);
                rule.coefficient = entry.getValue();

                rules.add(rule);
            }

            Comparator<Rule> ruleAbsCoefficientComparator = Comparator.comparingDouble(Rule::getAbsCoefficient).reversed();
            rules.sort(ruleAbsCoefficientComparator);

            return rules;
        }


        Rule mapColumnName(String columnName, GLMModel.GLMParameters.Family family) {
            Rule rule;
            if (GLMModel.GLMParameters.Family.binomial.equals(family)) {
                String[] extractedFields = columnName.replace("tree_", "").replace("T", "").replace("C", "").split("\\.");
                rule = new Rule(columnName, Integer.parseInt(extractedFields[0]), Integer.parseInt(extractedFields[1]) - 1, String.valueOf(Integer.parseInt(extractedFields[2]) - 1), extractedFields[3]);
            } else {
                String[] extractedFields = columnName.replace("tree_", "").replace("T", "").split("\\.");
                rule = new Rule(columnName, Integer.parseInt(extractedFields[0]), Integer.parseInt(extractedFields[1]) - 1, null, extractedFields[2]);
            }
            return rule;
        }

        // Traverse the tree to get the rules for a specific split_path
        String treeTraverser(TreeV3 tree, String splitPath) {
            int node = tree.root_node_id;
            String languageRule;

            int[] normalized_left_child = new int[tree.left_children.length];
            int[] normalized_right_child = new int[tree.right_children.length];

            int nodeId = 0;
            for (int i = 0; i < tree.left_children.length; i++) {
                if (tree.left_children[i] != -1) {
                    nodeId++;
                    normalized_left_child[i] = nodeId;
                } else {
                    normalized_left_child[i] = -1;
                }
                if (tree.right_children[i] != -1) {
                    nodeId++;
                    normalized_right_child[i] = nodeId;
                } else {
                    normalized_right_child[i] = -1;
                }
            }

            for (int i = 0; i < splitPath.length(); i++) {
                char currChar = splitPath.charAt(i);
                if ('R' == currChar) {
                    node = normalized_right_child[node];
                }
                if ('L' == currChar) {
                    node = normalized_left_child[node];
                }
            }

            languageRule = tree.decision_paths[node];

            return formattedRule(languageRule);
        }
    }

    private TwoDimTable convertRulesToTable(List rules) {
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

        final int rows = rules.size();
        TwoDimTable table = new TwoDimTable("Rule Importance", null, new String[rows],
                colHeaders.toArray(new String[0]), colTypes.toArray(new String[0]), colFormat.toArray(new String[0]), "");

        for (int row = 0; row < rows; row++) {
            int col = 0;
            table.set(row, col++, ((Rule) rules.get(row)).variable);
            table.set(row, col++, ((Rule) rules.get(row)).coefficient);
            table.set(row, col, ((Rule) rules.get(row)).languageRule);
        }

        return table;
    }

    private String formattedRule(String rule) {
        rule = rule.substring(rule.indexOf('\n') + 1); // remove line with Prediction value + dart leading to it 
        rule = rule.replace("^\n" + "|\n" + "|\n" + "|\n", "");
        rule = rule.replace("\n" + "^\n" + "|\n" + "|\n" + "|\n", " AND "); // replace parent to children darts by AND
        rule = rule.replace("If ", "");
        rule = rule.replace("  ]", "]");
        rule = rule.replace("( ", "(");
        rule = rule.replace("\n", "");
        return rule;
    }

    class Rule {
        public String variable;
        public int modelIdx;
        public int treeNum;
        public String treeClass;
        public String path;
        public String languageRule;
        public double coefficient;

        public Rule(String variable, int modelIdx, int treeNum, String treeClass, String path) {
            this.variable = variable;
            this.modelIdx = modelIdx;
            this.treeNum = treeNum;
            this.treeClass = treeClass;
            this.path = path;
            this.languageRule = null;
        }

        double getAbsCoefficient() {
            return Math.abs(coefficient);
        }
    }
}


