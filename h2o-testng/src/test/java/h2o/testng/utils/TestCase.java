package h2o.testng.utils;

import h2o.testng.db.MySQL;
import hex.*;
import hex.deeplearning.DeepLearning;
import hex.deeplearning.DeepLearningModel;
import hex.deeplearning.DeepLearningParameters;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import water.*;
import water.util.Log;

import java.io.IOException;
import java.util.HashMap;

public class TestCase extends TestUtil {
  private static String testCasesPath = "h2o-testng/src/test/resources/accuracy_test_cases.csv";

  private int testCaseId;
  private String algo;
  private String algoParameters;
  private boolean tuned;
  private boolean regression;
  private int trainingDataSetId;
  private int testingDataSetId;
  public static final int size = 7; // number of fields in a test case

  private Model.Parameters params;	// the parameters object for the respective test case (algo)
  private DataSet trainingDataSet;
  private DataSet testingDataSet;

  public TestCase(int testCaseId, String algo, String algoParameters, boolean tuned, boolean regression, int
    trainingDataSetId, int testingDataSetId) throws IOException {
    this.testCaseId = testCaseId;
    this.algo = algo;
    this.algoParameters = algoParameters;
    this.tuned = tuned;
    this.regression = regression;
    this.trainingDataSetId = trainingDataSetId;
    this.testingDataSetId = testingDataSetId;

    trainingDataSet = new DataSet(trainingDataSetId);
    testingDataSet = new DataSet(testingDataSetId);
  }

  public static String getTestCasesPath() { return testCasesPath; }

  public void loadTestCaseDataSets() {
    try {
      trainingDataSet.load(regression);
    } catch (IOException e) {
      Log.err("Couldn't load trainingDataSet into H2O: " + trainingDataSet.getId());
      e.printStackTrace();
      System.exit(-1);
    }

    try {
      testingDataSet.load(regression);
    } catch (IOException e) {
      Log.err("Couldn't load testingDataSet into H2O: " + testingDataSet.getId());
      e.printStackTrace();
      System.exit(-1);
    }
  }

  public void execute() {
    Model.Output modelOutput = null;
    ModelMetrics trainingMetrics;
    ModelMetrics testMetrics;

    DRF drfJob = null;
    DRFModel drfModel = null;

    Key modelKey = Key.make("model");
    GLM glmJob = null;
    GLMModel glmModel = null;
    HashMap<String, Double> coef = null;

    GBM gbmJob = null;
    GBMModel gbmModel = null;

    DeepLearning dlJob = null;
    DeepLearningModel dlModel = null;

    try {
      Scope.enter();
      double modelStartTime = 0;
      double modelStopTime = 0;
      switch (algo) {
        case "drf":
          drfJob = new DRF((DRFModel.DRFParameters) params);
          Log.info("Train DRF model:");
          modelStartTime = System.currentTimeMillis();
          drfModel = drfJob.trainModel().get();
          modelStopTime = System.currentTimeMillis();
          modelOutput = drfModel._output;
          break;

        case "glm":
          glmJob = new GLM(modelKey, "GLM Model", (GLMModel.GLMParameters) params);
          Log.info("Train GLM model");
          modelStartTime = System.currentTimeMillis();
          glmModel = glmJob.trainModel().get();
          modelStopTime = System.currentTimeMillis();
          modelOutput = glmModel._output;
          break;

        case "gbm":
          gbmJob = new GBM((GBMModel.GBMParameters) params);
          Log.info("Train GBM model");
          modelStartTime = System.currentTimeMillis();
          gbmModel = gbmJob.trainModel().get();
          modelStopTime = System.currentTimeMillis();
          modelOutput = gbmModel._output;
          break;

        case FunctionUtils.dl:
          dlJob = new DeepLearning((DeepLearningParameters) params);
          Log.info("Train model");
          modelStartTime = System.currentTimeMillis();
          dlModel = dlJob.trainModel().get();
          modelStopTime = System.currentTimeMillis();
          modelOutput = dlModel._output;
          break;
      }

      Log.info("Testcase passed!!!");

      trainingMetrics = modelOutput._training_metrics;
      testMetrics = modelOutput._validation_metrics;

      HashMap<String,Double> train = new HashMap<String,Double>();
      HashMap<String,Double> test = new HashMap<String,Double>();

      train.put("ModelBuildTime", modelStopTime - modelStartTime);

      // Supervised metrics
      train.put("MSE",trainingMetrics.mse());
      test.put("MSE",testMetrics.mse());
      train.put("R2",((ModelMetricsSupervised) trainingMetrics).r2());
      test.put("R2",((ModelMetricsSupervised) testMetrics).r2());

      // Regression metrics
      if( trainingMetrics instanceof ModelMetricsRegression) {
        train.put("MeanResidualDeviance",((ModelMetricsRegression) trainingMetrics)._mean_residual_deviance);
        test.put("MeanResidualDeviance",((ModelMetricsRegression) testMetrics)._mean_residual_deviance);
      }

      // Binomial metrics
      if( trainingMetrics instanceof ModelMetricsBinomial) {
        train.put("AUC",((ModelMetricsBinomial) trainingMetrics).auc());
        test.put("AUC",((ModelMetricsBinomial) testMetrics).auc());
        train.put("Gini",((ModelMetricsBinomial) trainingMetrics)._auc._gini);
        test.put("Gini",((ModelMetricsBinomial) testMetrics)._auc._gini);
        train.put("Logloss",((ModelMetricsBinomial) trainingMetrics).logloss());
        test.put("Logloss",((ModelMetricsBinomial) testMetrics).logloss());
        train.put("F1",((ModelMetricsBinomial) trainingMetrics).cm().F1());
        test.put("F1",((ModelMetricsBinomial) testMetrics).cm().F1());
        train.put("F2",((ModelMetricsBinomial) trainingMetrics).cm().F2());
        test.put("F2",((ModelMetricsBinomial) testMetrics).cm().F2());
        train.put("F0point5",((ModelMetricsBinomial) trainingMetrics).cm().F0point5());
        test.put("F0point5",((ModelMetricsBinomial) testMetrics).cm().F0point5());
        train.put("Accuracy",((ModelMetricsBinomial) trainingMetrics).cm().accuracy());
        test.put("Accuracy",((ModelMetricsBinomial) testMetrics).cm().accuracy());
        train.put("Error",((ModelMetricsBinomial) trainingMetrics).cm().err());
        test.put("Error",((ModelMetricsBinomial) testMetrics).cm().err());
        train.put("Precision",((ModelMetricsBinomial) trainingMetrics).cm().precision());
        test.put("Precision",((ModelMetricsBinomial) testMetrics).cm().precision());
        train.put("Recall",((ModelMetricsBinomial) trainingMetrics).cm().recall());
        test.put("Recall",((ModelMetricsBinomial) testMetrics).cm().recall());
        train.put("MCC",((ModelMetricsBinomial) trainingMetrics).cm().mcc());
        test.put("MCC",((ModelMetricsBinomial) testMetrics).cm().mcc());
        train.put("MaxPerClassError",((ModelMetricsBinomial) trainingMetrics).cm().max_per_class_error());
        test.put("MaxPerClassError",((ModelMetricsBinomial) testMetrics).cm().max_per_class_error());
      }

      // GLM-specific metrics
      if( trainingMetrics instanceof ModelMetricsRegressionGLM) {
        train.put("ResidualDeviance",((ModelMetricsRegressionGLM) trainingMetrics)._resDev);
        test.put("ResidualDeviance",((ModelMetricsRegressionGLM) testMetrics)._resDev);
        train.put("ResidualDegreesOfFreedom",(double)((ModelMetricsRegressionGLM) trainingMetrics)._residualDegressOfFreedom);
        test.put("ResidualDegreesOfFreedom",(double)((ModelMetricsRegressionGLM) testMetrics)._residualDegressOfFreedom);
        train.put("NullDeviance",((ModelMetricsRegressionGLM) trainingMetrics)._nullDev);
        test.put("NullDeviance",((ModelMetricsRegressionGLM) testMetrics)._nullDev);
        train.put("NullDegreesOfFreedom",(double)((ModelMetricsRegressionGLM) trainingMetrics)._nullDegressOfFreedom);
        test.put("NullDegreesOfFreedom",(double)((ModelMetricsRegressionGLM) testMetrics)._nullDegressOfFreedom);
        train.put("AIC",((ModelMetricsRegressionGLM) trainingMetrics)._AIC);
        test.put("AIC",((ModelMetricsRegressionGLM) testMetrics)._AIC);
      }
      if( trainingMetrics instanceof ModelMetricsBinomialGLM) {
        train.put("ResidualDeviance",((ModelMetricsBinomialGLM) trainingMetrics)._resDev);
        test.put("ResidualDeviance",((ModelMetricsBinomialGLM) testMetrics)._resDev);
        train.put("ResidualDegreesOfFreedom",(double)((ModelMetricsBinomialGLM) trainingMetrics)._residualDegressOfFreedom);
        test.put("ResidualDegreesOfFreedom",(double)((ModelMetricsBinomialGLM) testMetrics)._residualDegressOfFreedom);
        train.put("NullDeviance",((ModelMetricsBinomialGLM) trainingMetrics)._nullDev);
        test.put("NullDeviance",((ModelMetricsBinomialGLM) testMetrics)._nullDev);
        train.put("NullDegreesOfFreedom",(double)((ModelMetricsBinomialGLM) trainingMetrics)._nullDegressOfFreedom);
        test.put("NullDegreesOfFreedom",(double)((ModelMetricsBinomialGLM) testMetrics)._nullDegressOfFreedom);
        train.put("AIC",((ModelMetricsBinomialGLM) trainingMetrics)._AIC);
        test.put("AIC",((ModelMetricsBinomialGLM) testMetrics)._AIC);
      }

      MySQL.save(train, test);
    }
    catch (Exception e) {
      System.exit(-1);
      e.printStackTrace();
    }
    finally {
      if (drfJob != null) {
        drfJob.remove();
      }
      if (drfModel != null) {
        drfModel.delete();
      }
      if (glmJob != null) {
        glmJob.remove();
      }
      if (glmModel != null) {
        glmModel.delete();
      }
      if (gbmJob != null) {
        gbmJob.remove();
      }
      if (gbmModel != null) {
        gbmModel.delete();
      }
      if (dlJob != null) {
        dlJob.remove();
      }
      if (dlModel != null) {
        dlModel.delete();
      }
      Scope.exit();
    }
  }

  public void setModelParameters() {
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
        Log.info("Cannot set model parameters for algo: " + algo);
        System.exit(-1);
    }
  }

  private GLMModel.GLMParameters makeGlmModelParameters() {
    return null;
  }
  private GBMModel.GBMParameters makeGbmModelParameters() {
    return null;
  }
  private DeepLearningModel.Parameters makeDlModelParameters() {
    return null;
  }
  private DRFModel.DRFParameters makeDrfModelParameters() {
    DRFModel.DRFParameters drfParams = new DRFModel.DRFParameters();
    String[] drfParamStrings = new String[]{
      "_distribution",
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
      "_nbins_top_level",
      "_train",
      "_valid",
      "_response_column"};

    String[] tokens = algoParameters.trim().split(";", -1);
    for (String p : drfParamStrings) {
      switch (p) {
        case "_distribution": // auto,gaussian,binomial,multinomial,poisson,gamma,tweedie
          if      (tokens[0].equals("x")) { drfParams._distribution = Distribution.Family.AUTO; }
          else if (tokens[1].equals("x")) { drfParams._distribution = Distribution.Family.gaussian; }
          else if (tokens[2].equals("x")) { drfParams._distribution = Distribution.Family.bernoulli; }
          else if (tokens[3].equals("x")) { drfParams._distribution = Distribution.Family.multinomial; }
          else if (tokens[4].equals("x")) { drfParams._distribution = Distribution.Family.poisson; }
          else if (tokens[5].equals("x")) { drfParams._distribution = Distribution.Family.gamma; }
          else if (tokens[6].equals("x")) { drfParams._distribution = Distribution.Family.tweedie; }
          break;
        case "_nfolds":
          if (!tokens[7].isEmpty()) { drfParams._nfolds = Integer.parseInt(tokens[7]); }
          break;
        case "_fold_column":
          if (!tokens[8].isEmpty()) { drfParams._fold_column = tokens[8]; }
          break;
        case "_ignore_const_cols":
          if (!tokens[9].isEmpty()) { drfParams._ignore_const_cols = true; }
          break;
        case "_offset_column":
          if (!tokens[10].isEmpty()) { drfParams._offset_column = tokens[10]; }
          break;
        case "_weights_column":
          if (!tokens[11].isEmpty()) { drfParams._weights_column = tokens[11]; }
          break;
        case "_ntrees":
          if (!tokens[12].isEmpty()) { drfParams._ntrees = Integer.parseInt(tokens[12]); }
          break;
        case "_max_depth":
          if (!tokens[13].isEmpty()) { drfParams._max_depth = Integer.parseInt(tokens[13]); }
          break;
        case "_min_rows":
          if (!tokens[14].isEmpty()) { drfParams._min_rows = Double.parseDouble(tokens[14]); }
          break;
        case "_nbins":
          if (!tokens[15].isEmpty()) { drfParams._nbins = Integer.parseInt(tokens[15]); }
          break;
        case "_nbins_cats":
          if (!tokens[16].isEmpty()) { drfParams._nbins_cats = Integer.parseInt(tokens[16]); }
          break;
        case "_score_each_iteration":
          if (!tokens[17].isEmpty()) { drfParams._score_each_iteration = true; }
          break;
        case "_balance_classes":
          if (!tokens[18].isEmpty()) { drfParams._balance_classes = true; }
          break;
        case "_max_confusion_matrix_size":
          if (!tokens[19].isEmpty()) { drfParams._max_confusion_matrix_size = Integer.parseInt(tokens[19]); }
          break;
        case "_max_hit_ratio_k":
          if (!tokens[20].isEmpty()) { drfParams._max_hit_ratio_k = Integer.parseInt(tokens[20]); }
          break;
        case "_r2_stopping":
          if (!tokens[21].isEmpty()) { drfParams._r2_stopping = Double.parseDouble(tokens[21]); }
          break;
        case "_build_tree_one_node":
          if (!tokens[22].isEmpty()) { drfParams._build_tree_one_node = true; }
          break;
        case "_class_sampling_factors":
          if (!tokens[23].isEmpty()) {
            Log.info("_class_sampling_factors not supported for drf test cases");
            System.exit(-1);
          }
          break;
        case "_binomial_double_trees":
          if (!tokens[24].isEmpty()) { drfParams._binomial_double_trees = true; }
          break;
        case "_checkpoint":
          if (!tokens[25].isEmpty()) {
            Log.info("_checkpoint not supported for drf test cases");
            System.exit(-1);
          }
          break;
        case "_nbins_top_level":
          if (!tokens[26].isEmpty()) { drfParams._nbins_top_level = Integer.parseInt(tokens[26]); }
          break;
        case "_train":
          drfParams._train = trainingDataSet.getFrame()._key;
          break;
        case "_valid":
          drfParams._valid = testingDataSet.getFrame()._key;
          break;
        case "_response_column":
          drfParams._response_column = trainingDataSet.getFrame()._names[trainingDataSet.getResponseColumn()];
      }
    }
    return drfParams;
  }

  public void cleanUp() {
    Log.info("Removing all objects");
    Futures fs = new Futures();
    // Cancel and remove leftover running jobs
    for( Job j : Job.jobs() ) { j.cancel(); j.remove(fs); }
    fs.blockForPending();
    // Bulk brainless key removal.  Completely wipes all Keys without regard.
    new MRTask(){
      @Override public byte priority() { return H2O.GUI_PRIORITY; }
      @Override public void setupLocal() {  H2O.raw_clear();  water.fvec.Vec.ESPC.clear(); }
    }.doAllNodes();
    // Wipe the backing store without regard as well
    H2O.getPM().getIce().cleanUp();
    Log.info("Finished removing objects");
  }
}

