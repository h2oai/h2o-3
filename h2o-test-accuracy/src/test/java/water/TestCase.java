package water;

import hex.*;
import hex.deeplearning.DeepLearning;
import hex.deeplearning.DeepLearningModel;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.grid.Grid;
import hex.grid.GridSearch;
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
  private boolean grid;
  private String gridParameters;
  private String gridCriteria;
  private boolean regression;
  private int trainingDataSetId;
  private int testingDataSetId;
  private String testCaseDescription;

  private Model.Parameters params;
  private DataSet trainingDataSet;
  private DataSet testingDataSet;
  private HashMap<String, Object[]> hyperParms;

  public TestCase(int testCaseId, String algo, String algoParameters, boolean grid, String gridParameters,
                  String gridCriteria, boolean regression, int trainingDataSetId, int testingDataSetId,
                  String testCaseDescription) throws Exception {
    this.testCaseId = testCaseId;
    this.algo = algo;
    this.algoParameters = algoParameters;
    this.grid = grid;
    this.gridParameters = gridParameters;
    this.gridCriteria = gridCriteria;
    this.regression = regression;
    this.trainingDataSetId = trainingDataSetId;
    this.testingDataSetId = testingDataSetId;
    this.testCaseDescription = testCaseDescription;

    trainingDataSet = new DataSet(this.trainingDataSetId);
    testingDataSet = new DataSet(this.testingDataSetId);
  }

  public int getTestCaseId() {
    return testCaseId;
  }

  public TestCaseResult execute() throws Exception, AssertionError {
    loadTestCaseDataSets();
    makeModelParameters();

    double startTime = 0, stopTime = 0;
    if (!grid) {
      Model.Output modelOutput = null;
      DRF drfJob;
      DRFModel drfModel = null;
      GLM glmJob;
      GLMModel glmModel = null;
      GBM gbmJob;
      GBMModel gbmModel = null;
      DeepLearning dlJob;
      DeepLearningModel dlModel = null;

      try {
        switch (algo) {
          case "drf":
            drfJob = new DRF((DRFModel.DRFParameters) params);
            AccuracyTestingSuite.summaryLog.println("Training DRF model.");
            startTime = System.currentTimeMillis();
            drfModel = drfJob.trainModel().get();
            stopTime = System.currentTimeMillis();
            modelOutput = drfModel._output;
            break;
          case "glm":
            glmJob = new GLM((GLMModel.GLMParameters) params, Key.<GLMModel>make("GLMModel"));
            AccuracyTestingSuite.summaryLog.println("Training GLM model.");
            startTime = System.currentTimeMillis();
            glmModel = glmJob.trainModel().get();
            stopTime = System.currentTimeMillis();
            modelOutput = glmModel._output;
            break;
          case "gbm":
            gbmJob = new GBM((GBMModel.GBMParameters) params);
            AccuracyTestingSuite.summaryLog.println("Training GBM model.");
            startTime = System.currentTimeMillis();
            gbmModel = gbmJob.trainModel().get();
            stopTime = System.currentTimeMillis();
            modelOutput = gbmModel._output;
            break;
          case "dl":
            dlJob = new DeepLearning((DeepLearningModel.DeepLearningParameters) params);
            AccuracyTestingSuite.summaryLog.println("Training DL model.");
            startTime = System.currentTimeMillis();
            dlModel = dlJob.trainModel().get();
            stopTime = System.currentTimeMillis();
            modelOutput = dlModel._output;
            break;
        }
      } catch (Exception e) {
        throw new Exception(e);
      } finally {
        if (drfModel != null) {
          drfModel.delete();
        }
        if (glmModel != null) {
          glmModel.delete();
        }
        if (gbmModel != null) {
          gbmModel.delete();
        }
        if (dlModel != null) {
          dlModel.delete();
        }
      }
      removeTestCaseDataSetFrames();
      return new TestCaseResult(testCaseId, getMetrics(modelOutput._training_metrics),
              getMetrics(modelOutput._validation_metrics), stopTime - startTime);
    } else {
      assert !gridCriteria.equals("");
      makeGridParameters();
      Grid grid = null;
      Model bestModel = null;
      try {
        switch (algo) {  // TODO: Hack for PUBDEV-2812
          case "drf":
            new DRF(true);
            break;
          case "glm":
            new GLM(true);
            break;
          case "gbm":
            new GBM(true);
            break;
          case "dl":
            new DeepLearning(true);
            break;
        }
        startTime = System.currentTimeMillis();
        Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms);
        grid = gs.get();
        stopTime = System.currentTimeMillis();

        boolean higherIsBetter = higherIsBetter(gridCriteria);
        double bestScore = higherIsBetter ? -Double.MAX_VALUE : Double.MAX_VALUE;
        for (Model m : grid.getModels()) {
          double validationMetricScore = getMetrics(m._output._validation_metrics).get(gridCriteria);
          AccuracyTestingSuite.summaryLog.println(gridCriteria + " for model " + m._key.toString() + " is " +
                  validationMetricScore);
          if (higherIsBetter ? validationMetricScore > bestScore : validationMetricScore < bestScore) {
            bestScore = validationMetricScore;
            bestModel = m;
          }
        }
        AccuracyTestingSuite.summaryLog.println("Best model: " + bestModel._key.toString());
        AccuracyTestingSuite.summaryLog.println("Best model parameters: " + bestModel._parms.toJsonString());
      } catch (Exception e) {
        throw new Exception(e);
      } finally {
        if (grid != null) {
          grid.delete();
        }
      }
      removeTestCaseDataSetFrames();
      return new TestCaseResult(testCaseId, getMetrics(bestModel._output._training_metrics),
              getMetrics(bestModel._output._validation_metrics), stopTime - startTime);
    }
  }

  private void loadTestCaseDataSets() throws IOException {
    trainingDataSet.load(regression);
    testingDataSet.load(regression);
  }

  private void removeTestCaseDataSetFrames() {
    trainingDataSet.removeFrame();
    testingDataSet.removeFrame();
  }

  private void makeModelParameters() throws Exception {
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

  private void makeGridParameters() throws Exception {
    switch (algo) {
      case "drf":
        hyperParms = makeDrfGridParameters();
        break;
      case "glm":
        hyperParms = makeGlmGridParameters();
        break;
      case "dl":
        hyperParms = makeDlGridParameters();
        break;
      case "gbm":
        hyperParms = makeGbmGridParameters();
        break;
      default:
        throw new Exception("No algo: " + algo);
    }
  }

  private HashMap<String, Double> getMetrics(ModelMetrics mm) {
    HashMap<String, Double> mmMap = new HashMap<String, Double>();
    // Supervised metrics
    mmMap.put("MSE", mm.mse());
    mmMap.put("R2", ((ModelMetricsSupervised) mm).r2());
    // Regression metrics
    if (mm instanceof ModelMetricsRegression) {
      mmMap.put("MeanResidualDeviance", ((ModelMetricsRegression) mm)._mean_residual_deviance);
    }
    // Binomial metrics
    if (mm instanceof ModelMetricsBinomial) {
      mmMap.put("AUC", ((ModelMetricsBinomial) mm).auc());
      mmMap.put("Gini", ((ModelMetricsBinomial) mm)._auc._gini);
      mmMap.put("Logloss", ((ModelMetricsBinomial) mm).logloss());
      mmMap.put("F1", ((ModelMetricsBinomial) mm).cm().F1());
      mmMap.put("F2", ((ModelMetricsBinomial) mm).cm().F2());
      mmMap.put("F0point5", ((ModelMetricsBinomial) mm).cm().F0point5());
      mmMap.put("Accuracy", ((ModelMetricsBinomial) mm).cm().accuracy());
      mmMap.put("Error", ((ModelMetricsBinomial) mm).cm().err());
      mmMap.put("Precision", ((ModelMetricsBinomial) mm).cm().precision());
      mmMap.put("Recall", ((ModelMetricsBinomial) mm).cm().recall());
      mmMap.put("MCC", ((ModelMetricsBinomial) mm).cm().mcc());
      mmMap.put("MaxPerClassError", ((ModelMetricsBinomial) mm).cm().max_per_class_error());
    }
    // Multinomial metrics
    if (mm instanceof ModelMetricsMultinomial) {
      mmMap.put("Logloss", ((ModelMetricsMultinomial) mm).logloss());
      mmMap.put("Error", ((ModelMetricsMultinomial) mm).cm().err());
      mmMap.put("MaxPerClassError", ((ModelMetricsMultinomial) mm).cm().max_per_class_error());
      mmMap.put("Accuracy", ((ModelMetricsMultinomial) mm).cm().accuracy());
    }
    // GLM-specific metrics
    if (mm instanceof ModelMetricsRegressionGLM) {
      mmMap.put("ResidualDeviance", ((ModelMetricsRegressionGLM) mm)._resDev);
      mmMap.put("ResidualDegreesOfFreedom", (double) ((ModelMetricsRegressionGLM) mm)._residualDegressOfFreedom);
      mmMap.put("NullDeviance", ((ModelMetricsRegressionGLM) mm)._nullDev);
      mmMap.put("NullDegreesOfFreedom", (double) ((ModelMetricsRegressionGLM) mm)._nullDegressOfFreedom);
      mmMap.put("AIC", ((ModelMetricsRegressionGLM) mm)._AIC);
    }
    if (mm instanceof ModelMetricsBinomialGLM) {
      mmMap.put("ResidualDeviance", ((ModelMetricsBinomialGLM) mm)._resDev);
      mmMap.put("ResidualDegreesOfFreedom", (double) ((ModelMetricsBinomialGLM) mm)._residualDegressOfFreedom);
      mmMap.put("NullDeviance", ((ModelMetricsBinomialGLM) mm)._nullDev);
      mmMap.put("NullDegreesOfFreedom", (double) ((ModelMetricsBinomialGLM) mm)._nullDegressOfFreedom);
      mmMap.put("AIC", ((ModelMetricsBinomialGLM) mm)._AIC);
    }
    return mmMap;
  }

  private GLMModel.GLMParameters makeGlmModelParameters() throws Exception {
    AccuracyTestingSuite.summaryLog.println("Making GLM model parameters.");
    GLMModel.GLMParameters glmParams = new GLMModel.GLMParameters();
    String[] tokens = algoParameters.trim().split(";", -1);
    for (int i = 0; i < tokens.length; i++) {
      String parameterName = tokens[i].split("=", -1)[0];
      String parameterValue = tokens[i].split("=", -1)[1];
      switch (parameterName) {
        case "_distribution":
          switch (parameterValue) {
            case "AUTO":
              glmParams._distribution = Distribution.Family.AUTO;
              break;
            case "gaussian":
              glmParams._distribution = Distribution.Family.gaussian;
              break;
            case "bernoulli":
              glmParams._distribution = Distribution.Family.bernoulli;
              break;
            case "multinomial":
              glmParams._distribution = Distribution.Family.multinomial;
              break;
            case "poisson":
              glmParams._distribution = Distribution.Family.poisson;
              break;
            case "gamma":
              glmParams._distribution = Distribution.Family.gamma;
              break;
            case "tweedie":
              glmParams._distribution = Distribution.Family.tweedie;
              break;
            default:
              throw new Exception(parameterValue + " distribution is not supported for gbm test cases");
          }
          break;
        case "_solver":
          switch (parameterValue) {
            case "AUTO":
              glmParams._solver = GLMModel.GLMParameters.Solver.AUTO;
              break;
            case "irlsm":
              glmParams._solver = GLMModel.GLMParameters.Solver.IRLSM;
              break;
            case "lbfgs":
              glmParams._solver = GLMModel.GLMParameters.Solver.L_BFGS;
              break;
            case "coordinate_descent_naive":
              glmParams._solver = GLMModel.GLMParameters.Solver.COORDINATE_DESCENT_NAIVE;
              break;
            case "coordinate_descent":
              glmParams._solver = GLMModel.GLMParameters.Solver.COORDINATE_DESCENT;
              break;
            default:
              throw new Exception(parameterValue + " solver is not supported for gbm test cases");
          }
          break;
        case "_nfolds":
          glmParams._nfolds = Integer.parseInt(parameterValue);
          break;
        case "_fold_column":
          glmParams._fold_column = tokens[i];
          break;
        case "_ignore_const_cols":
          glmParams._ignore_const_cols = true;
          break;
        case "_offset_column":
          glmParams._offset_column = tokens[i];
          break;
        case "_weights_column":
          glmParams._weights_column = tokens[i];
          break;
        case "_alpha":
          glmParams._alpha = new double[]{Double.parseDouble(parameterValue)};
          break;
        case "_lambda":
          glmParams._lambda = new double[]{Double.parseDouble(parameterValue)};
          break;
        case "_lambda_search":
          glmParams._lambda_search = true;
          break;
        case "_standardize":
          glmParams._standardize = true;
          break;
        case "_non_negative":
          glmParams._non_negative = true;
          break;
        case "_intercept":
          glmParams._intercept = true;
          break;
        case "_prior":
          glmParams._prior = Double.parseDouble(parameterValue);
          break;
        case "_max_active_predictors":
          glmParams._max_active_predictors = Integer.parseInt(parameterValue);
          break;
        case "_beta_constraints":
          double lowerBound = Double.parseDouble(tokens[i].split("|")[0]);
          double upperBound = Double.parseDouble(tokens[i].split("|")[1]);
          glmParams._beta_constraints = makeBetaConstraints(lowerBound, upperBound);
          break;
        default:
          throw new Exception(parameterName + " parameter is not supported for glm test cases");
      }
    }
    // _train, _valid, _response
    glmParams._train = trainingDataSet.getFrame()._key;
    glmParams._valid = testingDataSet.getFrame()._key;
    glmParams._response_column = trainingDataSet.getFrame()._names[trainingDataSet.getResponseColumn()];
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
        } else { // numeric columns only need one coefficient name
          betaConstraintsString += String.format("%s,%s,%s\n", name, lowerBound, upperBound);
        }
      }
    }
    Key betaConsKey = Key.make("beta_constraints");
    FVecTest.makeByteVec(betaConsKey, betaConstraintsString);
    return ParseDataset.parse(Key.make("beta_constraints.hex"), betaConsKey)._key;
  }

  private HashMap<String, Object[]> makeGlmGridParameters() throws Exception {
    AccuracyTestingSuite.summaryLog.println("Making GLM grid parameters.");
    String[] tokens = gridParameters.trim().split(";", -1);
    HashMap<String, Object[]> glmHyperParms = new HashMap<String, Object[]>();
    for (int i = 0; i < tokens.length; i++) {
      if (tokens[i].equals("")) return glmHyperParms;
      String gridParameterName = tokens[i].split("=", -1)[0];
      String[] gridParameterValues = tokens[i].split("=", -1)[1].split("\\|", -1);
      switch (gridParameterName) {
        case "_alpha":
          glmHyperParms.put("_alpha", stringArrayToDoubleAA(gridParameterValues));
          break;
        case "_lambda":
          glmHyperParms.put("_lambda", stringArrayToDoubleAA(gridParameterValues));
          break;
        default:
          throw new Exception(gridParameterName + " grid parameter is not supported for glm test cases");
      }
    }
    return glmHyperParms;
  }

  private GBMModel.GBMParameters makeGbmModelParameters() throws Exception {
    AccuracyTestingSuite.summaryLog.println("Making GBM model parameters.");
    GBMModel.GBMParameters gbmParams = new GBMModel.GBMParameters();
    String[] tokens = algoParameters.trim().split(";", -1);
    for (int i = 0; i < tokens.length; i++) {
      String parameterName = tokens[i].split("=", -1)[0];
      String parameterValue = tokens[i].split("=", -1)[1];
      switch (parameterName) {
        case "_distribution":
          switch (parameterValue) {
            case "AUTO":
              gbmParams._distribution = Distribution.Family.AUTO;
              break;
            case "gaussian":
              gbmParams._distribution = Distribution.Family.gaussian;
              break;
            case "bernoulli":
              gbmParams._distribution = Distribution.Family.bernoulli;
              break;
            case "multinomial":
              gbmParams._distribution = Distribution.Family.multinomial;
              break;
            case "poisson":
              gbmParams._distribution = Distribution.Family.poisson;
              break;
            case "gamma":
              gbmParams._distribution = Distribution.Family.gamma;
              break;
            case "tweedie":
              gbmParams._distribution = Distribution.Family.tweedie;
              break;
            default:
              throw new Exception(parameterValue + " distribution is not supported for gbm test cases");
          }
          break;
        case "_nfolds":
          gbmParams._nfolds = Integer.parseInt(parameterValue);
          break;
        case "_fold_column":
          gbmParams._fold_column = tokens[i];
          break;
        case "_ignore_const_cols":
          gbmParams._ignore_const_cols = true;
          break;
        case "_offset_column":
          gbmParams._offset_column = tokens[i];
          break;
        case "_weights_column":
          gbmParams._weights_column = tokens[i];
          break;
        case "_ntrees":
          gbmParams._ntrees = Integer.parseInt(parameterValue);
          break;
        case "_max_depth":
          gbmParams._max_depth = Integer.parseInt(parameterValue);
          break;
        case "_min_rows":
          gbmParams._min_rows = Double.parseDouble(parameterValue);
          break;
        case "_nbins":
          gbmParams._nbins = Integer.parseInt(parameterValue);
          break;
        case "_nbins_cats":
          gbmParams._nbins_cats = Integer.parseInt(parameterValue);
          break;
        case "_learn_rate":
          gbmParams._learn_rate = Float.parseFloat(parameterValue);
          break;
        case "_score_each_iteration":
          gbmParams._score_each_iteration = true;
          break;
        case "_balance_classes":
          gbmParams._balance_classes = true;
          break;
        case "_max_confusion_matrix_size":
          gbmParams._max_confusion_matrix_size = Integer.parseInt(parameterValue);
          break;
        case "_r2_stopping":
          gbmParams._r2_stopping = Double.parseDouble(parameterValue);
          break;
        case "_build_tree_one_node":
          gbmParams._build_tree_one_node = true;
          break;
        case "_sample_rate":
          gbmParams._sample_rate = Float.parseFloat(parameterValue);
          break;
        case "_col_sample_rate":
          gbmParams._col_sample_rate = Float.parseFloat(parameterValue);
          break;
        default:
          throw new Exception(parameterName + " parameter is not supported for gbm test cases");
      }
    }
    // _train, _valid, _response
    gbmParams._train = trainingDataSet.getFrame()._key;
    gbmParams._valid = testingDataSet.getFrame()._key;
    gbmParams._response_column = trainingDataSet.getFrame()._names[trainingDataSet.getResponseColumn()];
    return gbmParams;
  }

  private HashMap<String, Object[]> makeGbmGridParameters() throws Exception {
    AccuracyTestingSuite.summaryLog.println("Making GBM grid parameters.");
    String[] tokens = gridParameters.trim().split(";", -1);
    HashMap<String, Object[]> gbmHyperParms = new HashMap<String, Object[]>();
    for (int i = 0; i < tokens.length; i++) {
      String gridParameterName = tokens[i].split("=", -1)[0];
      String[] gridParameterValues = tokens[i].split("=", -1)[1].split("\\|", -1);
      switch (gridParameterName) {
        case "_ntrees":
          gbmHyperParms.put("_ntrees", stringArrayToIntegerArray(gridParameterValues));
          break;
        case "_max_depth":
          gbmHyperParms.put("_max_depth", stringArrayToIntegerArray(gridParameterValues));
          break;
        case "_min_rows":
          gbmHyperParms.put("_min_rows", stringArrayToDoubleArray(gridParameterValues));
          break;
        case "_nbins":
          gbmHyperParms.put("_nbins", stringArrayToIntegerArray(gridParameterValues));
          break;
        case "_nbins_cats":
          gbmHyperParms.put("_nbins_cats", stringArrayToIntegerArray(gridParameterValues));
          break;
        case "_learn_rate":
          gbmHyperParms.put("_learn_rate", stringArrayToFloatArray(gridParameterValues));
          break;
        case "_balance_classes":
          gbmHyperParms.put("_balance_classes", stringArrayToBooleanArray(gridParameterValues));
          break;
        case "_r2_stopping":
          gbmHyperParms.put("_r2_stopping", stringArrayToDoubleArray(gridParameterValues));
          break;
        case "_build_tree_one_node":
          gbmHyperParms.put("_build_tree_one_node", stringArrayToBooleanArray(gridParameterValues));
          break;
        case "_sample_rate":
          gbmHyperParms.put("_sample_rate", stringArrayToFloatArray(gridParameterValues));
          break;
        case "_col_sample_rate":
          gbmHyperParms.put("_col_sample_rate", stringArrayToFloatArray(gridParameterValues));
          break;
        default:
          throw new Exception(gridParameterName + " grid parameter is not supported for gbm test cases");
      }
    }
    return gbmHyperParms;
  }

  private DeepLearningModel.Parameters makeDlModelParameters() throws Exception {
    AccuracyTestingSuite.summaryLog.println("Making DL model parameters.");
    DeepLearningModel.DeepLearningParameters dlParams = new DeepLearningModel.DeepLearningParameters();
    String[] tokens = algoParameters.trim().split(";", -1);
    for (int i = 0; i < tokens.length; i++) {
      String parameterName = tokens[i].split("=", -1)[0];
      String parameterValue = tokens[i].split("=", -1)[1];
      switch (parameterName) {
        case "_distribution":
          switch (parameterValue) {
            case "AUTO":
              dlParams._distribution = Distribution.Family.AUTO;
              break;
            case "gaussian":
              dlParams._distribution = Distribution.Family.gaussian;
              break;
            case "bernoulli":
              dlParams._distribution = Distribution.Family.bernoulli;
              break;
            case "multinomial":
              dlParams._distribution = Distribution.Family.multinomial;
              break;
            case "poisson":
              dlParams._distribution = Distribution.Family.poisson;
              break;
            case "gamma":
              dlParams._distribution = Distribution.Family.gamma;
              break;
            case "tweedie":
              dlParams._distribution = Distribution.Family.tweedie;
              break;
            default:
              throw new Exception(parameterValue + " distribution is not supported for gbm test cases");
          }
          break;
        case "_activation":
          switch (parameterValue) {
            case "tanh":
              dlParams._activation = DeepLearningModel.DeepLearningParameters.Activation.Tanh;
              break;
            case "tanhwithdropout":
              dlParams._activation = DeepLearningModel.DeepLearningParameters.Activation.TanhWithDropout;
              break;
            case "rectifier":
              dlParams._activation = DeepLearningModel.DeepLearningParameters.Activation.Rectifier;
              break;
            case "rectifierwithdropout":
              dlParams._activation = DeepLearningModel.DeepLearningParameters.Activation.RectifierWithDropout;
              break;
            case "maxout":
              dlParams._activation = DeepLearningModel.DeepLearningParameters.Activation.Maxout;
              break;
            case "maxoutwithdropout":
              dlParams._activation = DeepLearningModel.DeepLearningParameters.Activation.MaxoutWithDropout;
              break;
            default:
              throw new Exception(parameterValue + " activation is not supported for gbm test cases");
          }
          break;
        case "_loss":
          switch (parameterValue) {
            case "AUTO":
              dlParams._loss = DeepLearningModel.DeepLearningParameters.Loss.Automatic;
              ;
              break;
            case "crossentropy":
              dlParams._loss = DeepLearningModel.DeepLearningParameters.Loss.CrossEntropy;
              break;
            case "quadratic":
              dlParams._loss = DeepLearningModel.DeepLearningParameters.Loss.Quadratic;
              break;
            case "huber":
              dlParams._loss = DeepLearningModel.DeepLearningParameters.Loss.Huber;
              break;
            case "absolute":
              dlParams._loss = DeepLearningModel.DeepLearningParameters.Loss.Absolute;
              break;
            default:
              throw new Exception(parameterValue + " loss is not supported for gbm test cases");
          }
          break;
        case "_hidden":
          String[] hidden = tokens[i].trim().split(":", -1);
          dlParams._hidden = stringArrayTointArray(hidden);
          break;
        case "_epochs":
          dlParams._epochs = Double.parseDouble(parameterValue);
          break;
        case "_variable_importances":
          dlParams._variable_importances = true;
          break;
        case "_fold_column":
          dlParams._fold_column = tokens[i];
          break;
        case "_weights_column":
          dlParams._weights_column = tokens[i];
          break;
        case "_balance_classes":
          dlParams._balance_classes = true;
          break;
        case "_max_confusion_matrix_size":
          dlParams._max_confusion_matrix_size = Integer.parseInt(parameterValue);
          break;
        case "_use_all_factor_levels":
          dlParams._use_all_factor_levels = true;
          break;
        case "_train_samples_per_iteration":
          dlParams._train_samples_per_iteration = Long.parseLong(parameterValue);
          break;
        case "_adaptive_rate":
          dlParams._adaptive_rate = true;
          break;
        case "_input_dropout_ratio":
          dlParams._input_dropout_ratio = Double.parseDouble(parameterValue);
          break;
        case "_l1":
          dlParams._l1 = Double.parseDouble(parameterValue);
          break;
        case "_l2":
          dlParams._l2 = Double.parseDouble(parameterValue);
          break;
        case "_score_interval":
          dlParams._score_interval = Double.parseDouble(parameterValue);
          break;
        case "_score_training_samples":
          dlParams._score_training_samples = Long.parseLong(parameterValue);
          break;
        case "_score_duty_cycle":
          dlParams._score_duty_cycle = Double.parseDouble(parameterValue);
          break;
        case "_replicate_training_data":
          dlParams._replicate_training_data = true;
          break;
        case "_autoencoder":
          dlParams._autoencoder = true;
          break;
        case "_target_ratio_comm_to_comp":
          dlParams._target_ratio_comm_to_comp = Double.parseDouble(parameterValue);
          break;
        case "_seed":
          dlParams._seed = Long.parseLong(parameterValue);
          break;
        case "_rho":
          dlParams._rho = Double.parseDouble(parameterValue);
          break;
        case "_epsilon":
          dlParams._epsilon = Double.parseDouble(parameterValue);
          break;
        case "_max_w2":
          dlParams._max_w2 = Float.parseFloat(parameterValue);
          break;
        case "_regression_stop":
          dlParams._regression_stop = Double.parseDouble(parameterValue);
          break;
        case "_diagnostics":
          dlParams._diagnostics = true;
          break;
        case "_fast_mode":
          dlParams._fast_mode = true;
          break;
        case "_force_load_balance":
          dlParams._force_load_balance = true;
          break;
        case "_single_node_mode":
          dlParams._single_node_mode = true;
          break;
        case "_shuffle_training_data":
          dlParams._shuffle_training_data = true;
          break;
        case "_quiet_mode":
          dlParams._quiet_mode = true;
          break;
        case "_sparse":
          dlParams._sparse = true;
          break;
        case "_col_major":
          dlParams._col_major = true;
          break;
        case "_average_activation":
          dlParams._average_activation = Double.parseDouble(parameterValue);
          break;
        case "_sparsity_beta":
          dlParams._sparsity_beta = Double.parseDouble(parameterValue);
          break;
        case "_max_categorical_features":
          dlParams._max_categorical_features = Integer.parseInt(parameterValue);
          break;
        case "_reproducible":
          dlParams._reproducible = true;
          break;
        case "_export_weights_and_biases":
          dlParams._export_weights_and_biases = true;
          break;
        default:
          throw new Exception(parameterName + " parameter is not supported for dl test cases");
      }
    }
    // _train, _valid, _response
    dlParams._train = trainingDataSet.getFrame()._key;
    dlParams._valid = testingDataSet.getFrame()._key;
    dlParams._response_column = trainingDataSet.getFrame()._names[trainingDataSet.getResponseColumn()];
    return dlParams;
  }

  private HashMap<String, Object[]> makeDlGridParameters() throws Exception {
    AccuracyTestingSuite.summaryLog.println("Making DL grid parameters.");
    String[] tokens = gridParameters.trim().split(";", -1);
    HashMap<String, Object[]> dlHyperParms = new HashMap<String, Object[]>();
    for (int i = 0; i < tokens.length; i++) {
      if (tokens[i].equals("")) return dlHyperParms;
      String gridParameterName = tokens[i].split("=", -1)[0];
      String[] gridParameterValues = tokens[i].split("=", -1)[1].split("\\|", -1);
      switch (gridParameterName) {
        case "_hidden":
          dlHyperParms.put("_hidden", hiddenStringArrayTointAA(gridParameterValues));
          break;
        case "_epochs":
          dlHyperParms.put("_epochs", stringArrayToIntegerArray(gridParameterValues));
          break;
        default:
          throw new Exception(gridParameterName + " grid parameter is not supported for dl test cases");
      }
    }
    return dlHyperParms;
  }

  private DRFModel.DRFParameters makeDrfModelParameters() throws Exception {
    AccuracyTestingSuite.summaryLog.println("Making DRF model parameters.");
    DRFModel.DRFParameters drfParams = new DRFModel.DRFParameters();
    String[] tokens = algoParameters.trim().split(";", -1);
    for (int i = 0; i < tokens.length; i++) {
      String parameterName = tokens[i].split("=", -1)[0];
      String parameterValue = tokens[i].split("=", -1)[1];
      switch (parameterName) {
        case "_distribution":
          switch (parameterValue) {
            case "AUTO":
              drfParams._distribution = Distribution.Family.AUTO;
              break;
            case "gaussian":
              drfParams._distribution = Distribution.Family.gaussian;
              break;
            case "bernoulli":
              drfParams._distribution = Distribution.Family.bernoulli;
              break;
            case "multinomial":
              drfParams._distribution = Distribution.Family.multinomial;
              break;
            case "poisson":
              drfParams._distribution = Distribution.Family.poisson;
              break;
            case "gamma":
              drfParams._distribution = Distribution.Family.gamma;
              break;
            case "tweedie":
              drfParams._distribution = Distribution.Family.tweedie;
              break;
            default:
              throw new Exception(parameterValue + " distribution is not supported for gbm test cases");
          }
          break;
        case "_nfolds":
          drfParams._nfolds = Integer.parseInt(parameterValue);
          break;
        case "_fold_column":
          drfParams._fold_column = tokens[i];
          break;
        case "_ignore_const_cols":
          drfParams._ignore_const_cols = true;
          break;
        case "_offset_column":
          drfParams._offset_column = tokens[i];
          break;
        case "_weights_column":
          drfParams._weights_column = tokens[i];
          break;
        case "_ntrees":
          drfParams._ntrees = Integer.parseInt(parameterValue);
          break;
        case "_max_depth":
          drfParams._max_depth = Integer.parseInt(parameterValue);
          break;
        case "_min_rows":
          drfParams._min_rows = Double.parseDouble(parameterValue);
          break;
        case "_nbins":
          drfParams._nbins = Integer.parseInt(parameterValue);
          break;
        case "_nbins_cats":
          drfParams._nbins_cats = Integer.parseInt(parameterValue);
          break;
        case "_score_each_iteration":
          drfParams._score_each_iteration = true;
          break;
        case "_balance_classes":
          drfParams._balance_classes = true;
          break;
        case "_max_confusion_matrix_size":
          drfParams._max_confusion_matrix_size = Integer.parseInt(parameterValue);
          break;
        case "_r2_stopping":
          drfParams._r2_stopping = Double.parseDouble(parameterValue);
          break;
        case "_build_tree_one_node":
          drfParams._build_tree_one_node = true;
          break;
        case "_binomial_double_trees":
          drfParams._binomial_double_trees = true;
          break;
        case "_nbins_top_level":
          drfParams._nbins_top_level = Integer.parseInt(parameterValue);
          break;
        default:
          throw new Exception(parameterName + " parameter is not supported for gbm test cases");
      }
    }
    // _train, _valid, _response
    drfParams._train = trainingDataSet.getFrame()._key;
    drfParams._valid = testingDataSet.getFrame()._key;
    drfParams._response_column = trainingDataSet.getFrame()._names[trainingDataSet.getResponseColumn()];
    return drfParams;
  }

  private HashMap<String, Object[]> makeDrfGridParameters() throws Exception {
    AccuracyTestingSuite.summaryLog.println("Making DRF grid parameters.");
    String[] tokens = gridParameters.trim().split(";", -1);
    HashMap<String, Object[]> drfHyperParms = new HashMap<String, Object[]>();
    for (int i = 0; i < tokens.length; i++) {
      String gridParameterName = tokens[i].split("=", -1)[0];
      String[] gridParameterValues = tokens[i].split("=", -1)[1].split("\\|", -1);
      switch (gridParameterName) {
        case "_ntrees":
          drfHyperParms.put("_ntrees", stringArrayToIntegerArray(gridParameterValues));
          break;
        case "_max_depth":
          drfHyperParms.put("_max_depth", stringArrayToIntegerArray(gridParameterValues));
          break;
        case "_min_rows":
          drfHyperParms.put("_min_rows", stringArrayToDoubleArray(gridParameterValues));
          break;
        case "_nbins":
          drfHyperParms.put("_nbins", stringArrayToIntegerArray(gridParameterValues));
          break;
        case "_nbins_cats":
          drfHyperParms.put("_nbins_cats", stringArrayToIntegerArray(gridParameterValues));
          break;
        case "_balance_classes":
          drfHyperParms.put("_balance_classes", stringArrayToBooleanArray(gridParameterValues));
          break;
        case "_r2_stopping":
          drfHyperParms.put("_r2_stopping", stringArrayToDoubleArray(gridParameterValues));
          break;
        case "_build_tree_one_node":
          drfHyperParms.put("_build_tree_one_node", stringArrayToBooleanArray(gridParameterValues));
          break;
        case "_mtries":
          drfHyperParms.put("_mtries", stringArrayToIntegerArray(gridParameterValues));
          break;
        case "_sample_rate":
          drfHyperParms.put("_sample_rate", stringArrayToFloatArray(gridParameterValues));
          break;
        case "_binomial_double_trees":
          drfHyperParms.put("_binomial_double_trees", stringArrayToBooleanArray(gridParameterValues));
          break;
        case "_col_sample_rate_per_tree":
          drfHyperParms.put("_col_sample_rate_per_tree", stringArrayToFloatArray(gridParameterValues));
          break;
        case "_min_split_improvement":
          drfHyperParms.put("_min_split_improvement", stringArrayToDoubleArray(gridParameterValues));
          break;
        default:
          throw new Exception(gridParameterName + " grid parameter is not supported for drf test cases");
      }
    }
    return drfHyperParms;
  }

  static Integer[] stringArrayToIntegerArray(String[] sa) {
    Integer[] ia = new Integer[sa.length];
    for (int v = 0; v < sa.length; v++) ia[v] = Integer.parseInt(sa[v]);
    return ia;
  }

  static int[] stringArrayTointArray(String[] sa) {
    int[] ia = new int[sa.length];
    for (int v = 0; v < sa.length; v++) ia[v] = Integer.parseInt(sa[v]);
    return ia;
  }

  static int[][] hiddenStringArrayTointAA(String[] sa) {
    int[][] iaa = new int[sa.length][];
    for (int h=0; h<sa.length; h++) iaa[h] = stringArrayTointArray(sa[h].trim().split(":", -1));
    return iaa;
  }

  static Double[] stringArrayToDoubleArray(String[] sa) {
    Double[] da = new Double[sa.length];
    for (int v = 0; v < sa.length; v++) da[v] = Double.parseDouble(sa[v]);
    return da;
  }

  static double[][] stringArrayToDoubleAA(String[] sa) {
    double[][] daa = new double[sa.length][1];
    for (int v = 0; v < sa.length; v++) daa[v] = new double[]{Double.parseDouble(sa[v])};
    return daa;
  }

  static Float[] stringArrayToFloatArray(String[] sa) {
    Float[] fa = new Float[sa.length];
    for (int v = 0; v < sa.length; v++) fa[v] = Float.parseFloat(sa[v]);
    return fa;
  }

  static Boolean[] stringArrayToBooleanArray(String[] sa) {
    Boolean[] ba = new Boolean[sa.length];
    for (int v = 0; v < sa.length; v++) ba[v] = Boolean.parseBoolean(sa[v]);
    return ba;
  }

  static boolean higherIsBetter(String metric) {
    return metric.equals("R2") || metric.equals("AUC") || metric.equals("Precision") || metric.equals("Recall") ||
            metric.equals("F1") || metric.equals("F2") || metric.equals("F0point5") || metric.equals("Accuracy") ||
            metric.equals("Gini") || metric.equals("MCC");
  }
}


