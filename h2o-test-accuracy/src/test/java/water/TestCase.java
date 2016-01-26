package water;

import hex.*;
import hex.deeplearning.DeepLearning;
import hex.deeplearning.DeepLearningModel;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import water.fvec.FVecTest;
import water.fvec.Frame;
import water.parser.ParseDataset;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class TestCase {

  private int testCaseId;
  private String algo;
  private String algoParameters;
  private boolean tuned;
  private boolean regression;
  private int trainingDataSetId;
  private int testingDataSetId;

  private Model.Parameters params;
  private DataSet trainingDataSet;
  private DataSet testingDataSet;

  public TestCase(int testCaseId, String algo, String algoParameters, boolean tuned, boolean regression, int
    trainingDataSetId, int testingDataSetId) throws Exception {
    this.testCaseId = testCaseId;
    this.algo = algo;
    this.algoParameters = algoParameters;
    this.tuned = tuned;
    this.regression = regression;
    this.trainingDataSetId = trainingDataSetId;
    this.testingDataSetId = testingDataSetId;

    trainingDataSet = new DataSet(this.trainingDataSetId);
    testingDataSet = new DataSet(this.testingDataSetId);
  }

  public int getTestCaseId() { return testCaseId; }

  public TestCaseResult execute() throws Exception, AssertionError {
    loadTestCaseDataSets();

    makeModelParameters();

    Model.Output modelOutput = null;

    DRF drfJob = null;
    DRFModel drfModel = null;
    GLM glmJob = null;
    GLMModel glmModel = null;
    GBM gbmJob = null;
    GBMModel gbmModel = null;
    DeepLearning dlJob = null;
    DeepLearningModel dlModel = null;

    double modelStartTime = 0, modelStopTime = 0;

    try {
      Scope.enter();
      switch (algo) {
        case "drf":
          drfJob = new DRF((DRFModel.DRFParameters) params);
          AccuracyTestingSuite.summaryLog.println("Training DRF model.");
          modelStartTime = System.currentTimeMillis();
          drfModel = drfJob.trainModel().get();
          modelStopTime = System.currentTimeMillis();
          modelOutput = drfModel._output;
          break;
        case "glm":
          glmJob = new GLM((GLMModel.GLMParameters) params,Key.<GLMModel>make("GLMModel"));
          AccuracyTestingSuite.summaryLog.println("Training GLM model.");
          modelStartTime = System.currentTimeMillis();
          glmModel = glmJob.trainModel().get();
          modelStopTime = System.currentTimeMillis();
          modelOutput = glmModel._output;
          break;
        case "gbm":
          gbmJob = new GBM((GBMModel.GBMParameters) params);
          AccuracyTestingSuite.summaryLog.println("Training GBM model.");
          modelStartTime = System.currentTimeMillis();
          gbmModel = gbmJob.trainModel().get();
          modelStopTime = System.currentTimeMillis();
          modelOutput = gbmModel._output;
          break;
        case "dl":
          dlJob = new DeepLearning((DeepLearningModel.DeepLearningParameters) params);
          AccuracyTestingSuite.summaryLog.println("Training DL model.");
          modelStartTime = System.currentTimeMillis();
          dlModel = dlJob.trainModel().get();
          modelStopTime = System.currentTimeMillis();
          modelOutput = dlModel._output;
          break;
      }
    } catch (Exception e) {
      throw new Exception(e);
    } finally {
      if (drfModel != null) { drfModel.delete(); }
      if (glmModel != null) { glmModel.delete(); }
      if (gbmModel != null) { gbmModel.delete(); }
      if (dlModel != null)  { dlModel.delete(); }
      Scope.exit();
    }

    removeTestCaseDataSetFrames();

    return new TestCaseResult(testCaseId, getMetrics(modelOutput._training_metrics),
      getMetrics(modelOutput._validation_metrics), modelStopTime - modelStartTime);
  }

  private void loadTestCaseDataSets() throws IOException{
    trainingDataSet.load(regression);
    testingDataSet.load(regression);
  }

  private void removeTestCaseDataSetFrames() {
    trainingDataSet.removeFrame();
    testingDataSet.removeFrame();
  }

  private void makeModelParameters() throws Exception{
    switch (algo) {
      case "drf":
        params = makeDrfModelParameters();
        break;
      case "glm":
        params = makeGlmModelParameters();
        break;
      case "dl":
        params = makeDlModelParameters();
        break;
      case "gbm":
        params = makeGbmModelParameters();
        break;
      default:
        throw new Exception("No algo: " + algo);
    }
  }

  private HashMap<String,Double> getMetrics(ModelMetrics mm) {
    HashMap<String,Double> mmMap = new HashMap<String,Double>();
    // Supervised metrics
    mmMap.put("MSE",mm.mse());
    mmMap.put("R2",((ModelMetricsSupervised) mm).r2());
    // Regression metrics
    if(mm instanceof ModelMetricsRegression) {
      mmMap.put("MeanResidualDeviance",((ModelMetricsRegression) mm)._mean_residual_deviance);
    }
    // Binomial metrics
    if(mm instanceof ModelMetricsBinomial) {
      mmMap.put("AUC",((ModelMetricsBinomial) mm).auc());
      mmMap.put("Gini",((ModelMetricsBinomial) mm)._auc._gini);
      mmMap.put("Logloss",((ModelMetricsBinomial) mm).logloss());
      mmMap.put("F1",((ModelMetricsBinomial) mm).cm().F1());
      mmMap.put("F2",((ModelMetricsBinomial) mm).cm().F2());
      mmMap.put("F0point5",((ModelMetricsBinomial) mm).cm().F0point5());
      mmMap.put("Accuracy",((ModelMetricsBinomial) mm).cm().accuracy());
      mmMap.put("Error",((ModelMetricsBinomial) mm).cm().err());
      mmMap.put("Precision",((ModelMetricsBinomial) mm).cm().precision());
      mmMap.put("Recall",((ModelMetricsBinomial) mm).cm().recall());
      mmMap.put("MCC",((ModelMetricsBinomial) mm).cm().mcc());
      mmMap.put("MaxPerClassError",((ModelMetricsBinomial) mm).cm().max_per_class_error());
    }
    // GLM-specific metrics
    if(mm instanceof ModelMetricsRegressionGLM) {
      mmMap.put("ResidualDeviance",((ModelMetricsRegressionGLM) mm)._resDev);
      mmMap.put("ResidualDegreesOfFreedom",(double)((ModelMetricsRegressionGLM) mm)._residualDegressOfFreedom);
      mmMap.put("NullDeviance",((ModelMetricsRegressionGLM) mm)._nullDev);
      mmMap.put("NullDegreesOfFreedom",(double)((ModelMetricsRegressionGLM) mm)._nullDegressOfFreedom);
      mmMap.put("AIC",((ModelMetricsRegressionGLM) mm)._AIC);
    }
    if(mm instanceof ModelMetricsBinomialGLM) {
      mmMap.put("ResidualDeviance",((ModelMetricsBinomialGLM) mm)._resDev);
      mmMap.put("ResidualDegreesOfFreedom",(double)((ModelMetricsBinomialGLM) mm)._residualDegressOfFreedom);
      mmMap.put("NullDeviance",((ModelMetricsBinomialGLM) mm)._nullDev);
      mmMap.put("NullDegreesOfFreedom",(double)((ModelMetricsBinomialGLM) mm)._nullDegressOfFreedom);
      mmMap.put("AIC",((ModelMetricsBinomialGLM) mm)._AIC);
    }
    return mmMap;
  }

  private GLMModel.GLMParameters makeGlmModelParameters() throws Exception {
    AccuracyTestingSuite.summaryLog.println("Making GLM model parameters.");
    GLMModel.GLMParameters glmParams = new GLMModel.GLMParameters();
    String[] glmAlgoParamsMap = new String[]{
      "gaussian",
      "binomial",
      "poisson",
      "gamma",
      "tweedie",
      "auto",
      "irlsm",
      "lbfgs",
      "coordinate_descent_naive",
      "coordinate_descent",
      "_nfolds",
      "_fold_column",
      "_ignore_const_cols",
      "_offset_column",
      "_weights_column",
      "_alpha",
      "_lambda",
      "_lambda_search",
      "_standardize",
      "_non_negative",
      "betaConstraints",
      "lowerBound",
      "upperBound",
      "",
      "_intercept",
      "_prior",
      "_max_active_predictors"
    };

    String[] tokens = algoParameters.trim().split(";", -1);
    assert tokens.length == 27;

    // _distribution
    if      (tokens[0].equals("x")) { glmParams._family = GLMModel.GLMParameters.Family.gaussian; }
    else if (tokens[1].equals("x")) { glmParams._family = GLMModel.GLMParameters.Family.binomial; }
    else if (tokens[2].equals("x")) { glmParams._family = GLMModel.GLMParameters.Family.poisson; }
    else if (tokens[3].equals("x")) { glmParams._family = GLMModel.GLMParameters.Family.gamma; }
    else if (tokens[4].equals("x")) { glmParams._family = GLMModel.GLMParameters.Family.tweedie; }

    // _solver
    if      (tokens[5].equals("x")) { glmParams._solver = GLMModel.GLMParameters.Solver.AUTO; }
    else if (tokens[6].equals("x")) { glmParams._solver = GLMModel.GLMParameters.Solver.IRLSM; }
    else if (tokens[7].equals("x")) { glmParams._solver = GLMModel.GLMParameters.Solver.L_BFGS; }
    else if (tokens[8].equals("x")) { glmParams._solver = GLMModel.GLMParameters.Solver.COORDINATE_DESCENT_NAIVE; }
    else if (tokens[9].equals("x")) { glmParams._solver = GLMModel.GLMParameters.Solver.COORDINATE_DESCENT; }

    for (int i = 10; i < tokens.length; i++) {
      if (tokens[i].isEmpty() || i == 20 || i == 21 || i == 22 || i == 23) { continue; } // skip _beta_constraints
      switch (glmAlgoParamsMap[i]) {
        case "_nfolds":                    glmParams._nfolds = Integer.parseInt(tokens[i]);
          break;
        case "_fold_column":               glmParams._fold_column = tokens[i];
          break;
        case "_ignore_const_cols":         glmParams._ignore_const_cols = true;
          break;
        case "_offset_column":             glmParams._offset_column = tokens[i];
          break;
        case "_weights_column":            glmParams._weights_column = tokens[i];
          break;
        case "_alpha":                     glmParams._alpha = new double[]{Double.parseDouble(tokens[i])};
          break;
        case "_lambda":                    glmParams._lambda = new double[]{Double.parseDouble(tokens[i])};
          break;
        case "_lambda_search":             glmParams._lambda_search = true;
          break;
        case "_standardize":               glmParams._standardize = true;
          break;
        case "_non_negative":              glmParams._non_negative = true;
          break;
        case "_intercept":                 glmParams._intercept = true;
          break;
        case "_prior":                     glmParams._prior = Double.parseDouble(tokens[i]);
          break;
        case "_max_active_predictors":     glmParams._max_active_predictors = Integer.parseInt(tokens[i]);
          break;
        default:
          throw new Exception(glmAlgoParamsMap[i] + " parameter is not supported for glm test cases");
      }
    }
    // _train, _valid, _response
    glmParams._train = trainingDataSet.getFrame()._key;
    glmParams._valid = testingDataSet.getFrame()._key;
    glmParams._response_column = trainingDataSet.getFrame()._names[trainingDataSet.getResponseColumn()];

    // beta constraints
    if (tokens[20].equals("x")) {
      double lowerBound = Double.parseDouble(tokens[21]);
      double upperBound = Double.parseDouble(tokens[22]);
      glmParams._beta_constraints = makeBetaConstraints(lowerBound, upperBound);
    }
    return glmParams;
  }

  private Key<Frame> makeBetaConstraints(double lowerBound, double upperBound) {
    Frame trainingFrame = trainingDataSet.getFrame();
    int responseColumn = trainingDataSet.getResponseColumn();
    String betaConstraintsString = "names, lower_bounds, upper_bounds\n";
    List<String> predictorNames = Arrays.asList(trainingFrame._names);
    for (String name : predictorNames) {
      // ignore the response column and any constant column in bc.
      // we only want predictors
      if (!name.equals(trainingFrame._names[responseColumn]) && !trainingFrame.vec(name).isConst()) {
        // need coefficient names for each level of a categorical column
        if (trainingFrame.vec(name).isCategorical()) {
          for (String level : trainingFrame.vec(name).domain()) {
            betaConstraintsString += String.format("%s.%s,%s,%s\n", name, level, lowerBound, upperBound);
          }
        }
        else { // numeric columns only need one coefficient name
          betaConstraintsString += String.format("%s,%s,%s\n", name, lowerBound, upperBound);
        }
      }
    }
    Key betaConsKey = Key.make("beta_constraints");
    FVecTest.makeByteVec(betaConsKey, betaConstraintsString);
    return ParseDataset.parse(Key.make("beta_constraints.hex"), betaConsKey)._key;
  }

  private GBMModel.GBMParameters makeGbmModelParameters() throws Exception {
    AccuracyTestingSuite.summaryLog.println("Making GBM model parameters.");
    GBMModel.GBMParameters gbmParams = new GBMModel.GBMParameters();
    String[] gbmAlgoParamsMap = new String[]{
      "auto",
      "gaussian",
      "binomial",
      "multinomial",
      "poisson",
      "gamma",
      "tweedie",
      "_nfolds",
      "_fold_column",
      "_ignore_const_cols",
      "_offset_column",
      "_weights_column",
      "_ntrees",
      "_max_depth",
      "_min_rows",
      "_nbins",
      "_nbins_cats",
      "_learn_rate",
      "_score_each_iteration",
      "_balance_classes",
      "_max_confusion_matrix_size",
      "_max_hit_ratio_k",
      "_r2_stopping",
      "_build_tree_one_node",
      "_class_sampling_factors",
      "_sample_rate",
      "_col_sample_rate"
    };

    String[] tokens = algoParameters.trim().split(";", -1);
    assert tokens.length == 27;

    // _distribution
    if      (tokens[0].equals("x")) { gbmParams._distribution = Distribution.Family.AUTO; }
    else if (tokens[1].equals("x")) { gbmParams._distribution = Distribution.Family.gaussian; }
    else if (tokens[2].equals("x")) { gbmParams._distribution = Distribution.Family.bernoulli; }
    else if (tokens[3].equals("x")) { gbmParams._distribution = Distribution.Family.multinomial; }
    else if (tokens[4].equals("x")) { gbmParams._distribution = Distribution.Family.poisson; }
    else if (tokens[5].equals("x")) { gbmParams._distribution = Distribution.Family.gamma; }
    else if (tokens[6].equals("x")) { gbmParams._distribution = Distribution.Family.tweedie; }

    for (int i = 7; i < tokens.length; i++) {
      if (tokens[i].isEmpty()) { continue; }
      switch (gbmAlgoParamsMap[i]) {
        case "_nfolds":                    gbmParams._nfolds = Integer.parseInt(tokens[i]);
          break;
        case "_fold_column":               gbmParams._fold_column = tokens[i];
          break;
        case "_ignore_const_cols":         gbmParams._ignore_const_cols = true;
          break;
        case "_offset_column":             gbmParams._offset_column = tokens[i];
          break;
        case "_weights_column":            gbmParams._weights_column = tokens[i];
          break;
        case "_ntrees":                    gbmParams._ntrees = Integer.parseInt(tokens[i]);
          break;
        case "_max_depth":                 gbmParams._max_depth = Integer.parseInt(tokens[i]);
          break;
        case "_min_rows":                  gbmParams._min_rows = Double.parseDouble(tokens[i]);
          break;
        case "_nbins":                     gbmParams._nbins = Integer.parseInt(tokens[i]);
          break;
        case "_nbins_cats":                gbmParams._nbins_cats = Integer.parseInt(tokens[i]);
          break;
        case "_learn_rate":                gbmParams._learn_rate = Float.parseFloat(tokens[i]);
          break;
        case "_score_each_iteration":      gbmParams._score_each_iteration = true;
          break;
        case "_balance_classes":           gbmParams._balance_classes = true;
          break;
        case "_max_confusion_matrix_size": gbmParams._max_confusion_matrix_size = Integer.parseInt(tokens[i]);
          break;
        case "_r2_stopping":               gbmParams._r2_stopping = Double.parseDouble(tokens[i]);
          break;
        case "_build_tree_one_node":       gbmParams._build_tree_one_node = true;
          break;
        case "_sample_rate":               gbmParams._sample_rate = Float.parseFloat(tokens[i]);
          break;
        case "_col_sample_rate":           gbmParams._col_sample_rate = Float.parseFloat(tokens[i]);
          break;
        default:
          throw new Exception(gbmAlgoParamsMap[i] + " parameter is not supported for gbm test cases");
      }
    }
    // _train, _valid, _response
    gbmParams._train = trainingDataSet.getFrame()._key;
    gbmParams._valid = testingDataSet.getFrame()._key;
    gbmParams._response_column = trainingDataSet.getFrame()._names[trainingDataSet.getResponseColumn()];
    return gbmParams;
  }

  private DeepLearningModel.Parameters makeDlModelParameters() throws Exception {
    AccuracyTestingSuite.summaryLog.println("Making DL model parameters.");
    DeepLearningModel.DeepLearningParameters dlParams = new DeepLearningModel.DeepLearningParameters();
    String[] dlAlgoParamsMap = new String[]{
      "auto",
      "bernoulli",
      "multinomial",
      "gaussian",
      "poisson",
      "gamma",
      "tweedie",
      "_nfolds",
      "tanh",
      "tanhwithdropout",
      "rectifier",
      "rectifierwithdropout",
      "maxout",
      "maxoutwithdropout",
      "_hidden", // TODO: only 2 hidden layers supported at this time
      "_epochs",
      "_variable_importances",
      "_fold_column",
      "_offset_column",
      "_weights_column",
      "_balance_classes",
      "_max_confusion_matrix_size",
      "_max_hit_ratio_k",
      "_check_point",
      "_use_all_factor_levels",
      "_train_samples_per_iteration",
      "_adaptive_rate",
      "_input_dropout_ratio",
      "_l1",
      "_l2",
      "automatic",
      "crossentropy",
      "quadratic",
      "huber",
      "absolute",
      "",
      "_score_interval",
      "_score_training_samples",
      "_score_duty_cycle",
      "_replicate_training_data",
      "_autoencoder",
      "_class_sampling_factors",
      "_target_ratio_comm_to_comp",
      "_seed",
      "_rho",
      "_epsilon",
      "_max_w2",
      "_initial_weight_distribution",
      "_regression_stop",
      "_diagnostics",
      "_fast_mode",
      "_force_load_balance",
      "_single_node_mode",
      "_shuffle_training_data",
      "_missing_values_handling",
      "_quiet_mode",
      "_sparse",
      "_col_major",
      "_average_activation",
      "_sparsity_beta",
      "_max_categorical_features",
      "_reproducible",
      "_export_weights_and_biases"
    };

    String[] tokens = algoParameters.trim().split(";", -1);
    assert tokens.length == 63;

    // _distribution
    if      (tokens[0].equals("x")) { dlParams._distribution = Distribution.Family.AUTO; }
    else if (tokens[1].equals("x")) { dlParams._distribution = Distribution.Family.bernoulli; }
    else if (tokens[2].equals("x")) { dlParams._distribution = Distribution.Family.multinomial; }
    else if (tokens[3].equals("x")) { dlParams._distribution = Distribution.Family.gaussian; }
    else if (tokens[4].equals("x")) { dlParams._distribution = Distribution.Family.poisson; }
    else if (tokens[5].equals("x")) { dlParams._distribution = Distribution.Family.gamma; }
    else if (tokens[6].equals("x")) { dlParams._distribution = Distribution.Family.tweedie; }

    // _activation
    if      (tokens[8].equals("x"))  { dlParams._activation = DeepLearningModel.DeepLearningParameters.Activation.Tanh; }
    else if (tokens[9].equals("x"))  { dlParams._activation = DeepLearningModel.DeepLearningParameters.Activation.TanhWithDropout; }
    else if (tokens[10].equals("x")) { dlParams._activation = DeepLearningModel.DeepLearningParameters.Activation.Rectifier; }
    else if (tokens[11].equals("x")) { dlParams._activation = DeepLearningModel.DeepLearningParameters.Activation.RectifierWithDropout; }
    else if (tokens[12].equals("x")) { dlParams._activation = DeepLearningModel.DeepLearningParameters.Activation.Maxout; }
    else if (tokens[13].equals("x")) { dlParams._activation = DeepLearningModel.DeepLearningParameters.Activation.MaxoutWithDropout; }

    // _loss
    if      (tokens[30].equals("x")) { dlParams._loss = DeepLearningModel.DeepLearningParameters.Loss.Automatic; }
    else if (tokens[31].equals("x")) { dlParams._loss = DeepLearningModel.DeepLearningParameters.Loss.CrossEntropy; }
    else if (tokens[32].equals("x")) { dlParams._loss = DeepLearningModel.DeepLearningParameters.Loss.Quadratic; }
    else if (tokens[33].equals("x")) { dlParams._loss = DeepLearningModel.DeepLearningParameters.Loss.Huber; }
    else if (tokens[34].equals("x")) { dlParams._loss = DeepLearningModel.DeepLearningParameters.Loss.Absolute; }

    for (int i = 7; i < tokens.length; i++) {
      if (tokens[i].isEmpty() || i == 8 || i == 9 || i == 10 || i == 11 || i == 12 || i == 13 || i == 30 || i == 31
        || i == 32 || i == 33 || i == 34 || i == 35) { continue; } // skip _activation, _loss
      switch (dlAlgoParamsMap[i]) {
        case "_hidden":
          String[] hidden = tokens[i].trim().split(":", -1);
          dlParams._hidden = new int[]{ Integer.parseInt(hidden[0]), Integer.parseInt(hidden[0])};
          break;
        case "_epochs":                      dlParams._epochs = Double.parseDouble(tokens[i]);
          break;
        case "_variable_importances":        dlParams._variable_importances = true;
          break;
        case "_fold_column":                 dlParams._fold_column = tokens[i];
          break;
        case "_weights_column":              dlParams._weights_column = tokens[i];
          break;
        case "_balance_classes":             dlParams._balance_classes = true;
          break;
        case "_max_confusion_matrix_size":   dlParams._max_confusion_matrix_size = Integer.parseInt(tokens[i]);
          break;
        case "_use_all_factor_levels":       dlParams._use_all_factor_levels = true;
          break;
        case "_train_samples_per_iteration": dlParams._train_samples_per_iteration = Long.parseLong(tokens[i]);
          break;
        case "_adaptive_rate":               dlParams._adaptive_rate = true;
          break;
        case "_input_dropout_ratio":         dlParams._input_dropout_ratio = Double.parseDouble(tokens[i]);
          break;
        case "_l1":                          dlParams._l1 = Double.parseDouble(tokens[i]);
          break;
        case "_l2":                          dlParams._l2 = Double.parseDouble(tokens[i]);
          break;
        case "_score_interval":              dlParams._score_interval = Double.parseDouble(tokens[i]);
          break;
        case "_score_training_samples":      dlParams._score_training_samples = Long.parseLong(tokens[i]);
          break;
        case "_score_duty_cycle":            dlParams._score_duty_cycle = Double.parseDouble(tokens[i]);
          break;
        case "_replicate_training_data":     dlParams._replicate_training_data = true;
          break;
        case "_autoencoder":                 dlParams._autoencoder = true;
          break;
        case "_target_ratio_comm_to_comp":   dlParams._target_ratio_comm_to_comp = Double.parseDouble(tokens[i]);
          break;
        case "_seed":                        dlParams._seed = Long.parseLong(tokens[i]);
          break;
        case "_rho":                         dlParams._rho = Double.parseDouble(tokens[i]);
          break;
        case "_epsilon":                     dlParams._epsilon = Double.parseDouble(tokens[i]);
          break;
        case "_max_w2":                      dlParams._max_w2 = Float.parseFloat(tokens[i]);
          break;
        case "_regression_stop":             dlParams._regression_stop = Double.parseDouble(tokens[i]);
          break;
        case "_diagnostics":                 dlParams._diagnostics = true;
          break;
        case "_fast_mode":                   dlParams._fast_mode = true;
          break;
        case "_force_load_balance":          dlParams._force_load_balance = true;
          break;
        case "_single_node_mode":            dlParams._single_node_mode = true;
          break;
        case "_shuffle_training_data":       dlParams._shuffle_training_data = true;
          break;
        case "_quiet_mode":                  dlParams._quiet_mode = true;
          break;
        case "_sparse":                      dlParams._sparse = true;
          break;
        case "_col_major":                   dlParams._col_major = true;
          break;
        case "_average_activation":          dlParams._average_activation = Double.parseDouble(tokens[i]);
          break;
        case "_sparsity_beta":               dlParams._sparsity_beta = Double.parseDouble(tokens[i]);
          break;
        case "_max_categorical_features":    dlParams._max_categorical_features = Integer.parseInt(tokens[i]);
          break;
        case "_reproducible":                dlParams._reproducible = true;
          break;
        case "_export_weights_and_biases":   dlParams._export_weights_and_biases = true;
          break;
        default:
          throw new Exception(dlAlgoParamsMap[i] + " parameter is not supported for dl test cases");
      }
    }
    // _train, _valid, _response
    dlParams._train = trainingDataSet.getFrame()._key;
    dlParams._valid = testingDataSet.getFrame()._key;
    dlParams._response_column = trainingDataSet.getFrame()._names[trainingDataSet.getResponseColumn()];

    return dlParams;
  }

  private DRFModel.DRFParameters makeDrfModelParameters() throws Exception {
    AccuracyTestingSuite.summaryLog.println("Making DRF model parameters.");
    DRFModel.DRFParameters drfParams = new DRFModel.DRFParameters();
    String[] drfAlgoParamsMap = new String[]{
      "auto",
      "gaussian",
      "binomial",
      "multinomial",
      "poisson",
      "gamma",
      "tweedie",
      "_nfolds",
      "_fold_column",
      "_ignore_const_cols",
      "_offset_column",
      "_weights_column",
      "_ntrees",
      "_max_depth",
      "_min_rows",
      "_nbins",
      "_nbins_cats",
      "_score_each_iteration",
      "_balance_classes",
      "_max_confusion_matrix_size",
      "_max_hit_ratio_k",
      "_r2_stopping",
      "_build_tree_one_node",
      "_class_sampling_factors",
      "_binomial_double_trees",
      "_checkpoint",
      "_nbins_top_level"
    };

    String[] tokens = algoParameters.trim().split(";", -1);
    assert tokens.length == 27;

    // _distribution
    if      (tokens[0].equals("x")) { drfParams._distribution = Distribution.Family.AUTO; }
    else if (tokens[1].equals("x")) { drfParams._distribution = Distribution.Family.gaussian; }
    else if (tokens[2].equals("x")) { drfParams._distribution = Distribution.Family.bernoulli; }
    else if (tokens[3].equals("x")) { drfParams._distribution = Distribution.Family.multinomial; }
    else if (tokens[4].equals("x")) { drfParams._distribution = Distribution.Family.poisson; }
    else if (tokens[5].equals("x")) { drfParams._distribution = Distribution.Family.gamma; }
    else if (tokens[6].equals("x")) { drfParams._distribution = Distribution.Family.tweedie; }

    for (int i = 7; i < tokens.length; i++) {
      if (tokens[i].isEmpty()) { continue; }
      switch (drfAlgoParamsMap[i]) {
        case "_nfolds":                    drfParams._nfolds = Integer.parseInt(tokens[i]);
          break;
        case "_fold_column":               drfParams._fold_column = tokens[i];
          break;
        case "_ignore_const_cols":         drfParams._ignore_const_cols = true;
          break;
        case "_offset_column":             drfParams._offset_column = tokens[i];
          break;
        case "_weights_column":            drfParams._weights_column = tokens[i];
          break;
        case "_ntrees":                    drfParams._ntrees = Integer.parseInt(tokens[i]);
          break;
        case "_max_depth":                 drfParams._max_depth = Integer.parseInt(tokens[i]);
          break;
        case "_min_rows":                  drfParams._min_rows = Double.parseDouble(tokens[i]);
          break;
        case "_nbins":                     drfParams._nbins = Integer.parseInt(tokens[i]);
          break;
        case "_nbins_cats":                drfParams._nbins_cats = Integer.parseInt(tokens[i]);
          break;
        case "_score_each_iteration":      drfParams._score_each_iteration = true;
          break;
        case "_balance_classes":           drfParams._balance_classes = true;
          break;
        case "_max_confusion_matrix_size": drfParams._max_confusion_matrix_size = Integer.parseInt(tokens[i]);
          break;
        case "_r2_stopping":               drfParams._r2_stopping = Double.parseDouble(tokens[i]);
          break;
        case "_build_tree_one_node":       drfParams._build_tree_one_node = true;
          break;
        case "_binomial_double_trees":     drfParams._binomial_double_trees = true;
          break;
        case "_nbins_top_level":           drfParams._nbins_top_level = Integer.parseInt(tokens[i]);
          break;
        default:
          throw new Exception(drfAlgoParamsMap[i] + " parameter is not supported for gbm test cases");
      }
    }
    // _train, _valid, _response
    drfParams._train = trainingDataSet.getFrame()._key;
    drfParams._valid = testingDataSet.getFrame()._key;
    drfParams._response_column = trainingDataSet.getFrame()._names[trainingDataSet.getResponseColumn()];
    return drfParams;
  }
}

