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
    loadTestCaseDataSet(trainingDataSet);
    loadTestCaseDataSet(testingDataSet);
  }

  private void loadTestCaseDataSet(DataSet d) {
    try {
      d.load(regression);
    } catch (IOException e) {
      Log.err("Couldn't load data set: " + d.getId() + " into H2O.");
      Log.err(e.getMessage());
      System.exit(-1);
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
        Log.err("Cannot set model parameters for algo: " + algo);
        System.exit(-1);
    }
  }

  public void execute() {
    Model.Output modelOutput = null;

    DRF drfJob = null;
    DRFModel drfModel = null;
    GLM glmJob = null;
    GLMModel glmModel = null;
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
          glmJob = new GLM(Key.make("GLMModel"), "GLM Model", (GLMModel.GLMParameters) params);
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

      HashMap<String,Double> trainingMetrics = getMetrics(modelOutput._training_metrics);
      trainingMetrics.put("ModelBuildTime", modelStopTime - modelStartTime);
      HashMap<String,Double> testingMetrics = getMetrics(modelOutput._validation_metrics);
      MySQL.save(trainingMetrics, testingMetrics);
    }
    catch (Exception e) {
      Log.err(e.getMessage());
      System.exit(-1);
    }
    finally {
      if (drfJob != null)   { drfJob.remove(); }
      if (drfModel != null) { drfModel.delete(); }
      if (glmJob != null)   { glmJob.remove(); }
      if (glmModel != null) { glmModel.delete(); }
      if (gbmJob != null)   { gbmJob.remove(); }
      if (gbmModel != null) { gbmModel.delete(); }
      if (dlJob != null)    { dlJob.remove(); }
      if (dlModel != null)  { dlModel.delete(); }
      Scope.exit();
    }
  }

  public void cleanUp() {
    //FIXME: This was just copied over from RemoveAllHandler.
    Log.info("Removing all objects.");
    trainingDataSet.closeFrame();
    testingDataSet.closeFrame();
    Futures fs = new Futures();
    for( Job j : Job.jobs() ) { j.cancel(); j.remove(fs); }
    fs.blockForPending();
    new MRTask(){
      @Override public byte priority() { return H2O.GUI_PRIORITY; }
      @Override public void setupLocal() {  H2O.raw_clear();  water.fvec.Vec.ESPC.clear(); }
    }.doAllNodes();
    H2O.getPM().getIce().cleanUp();
    Log.info("Finished removing objects.");
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

  private GLMModel.GLMParameters makeGlmModelParameters() {
    return null;
  }
  private GBMModel.GBMParameters makeGbmModelParameters() {
    GBMModel.GBMParameters gbmParams = new GBMModel.GBMParameters();
    String[] gbmParamStrings = new String[]{
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
      "_learn_rate",
      "_score_each_iteration",
      "_balance_classes",
      "_max_confusion_matrix_size",
      "_max_hit_ratio_k",
      "_r2_stopping",
      "_build_tree_one_node",
      "_class_sampling_factors",
      "_sample_rate",
      "_col_sample_rate",
      "_train",
      "_valid",
      "_response_column"};

    String[] tokens = algoParameters.trim().split(";", -1);
    for (String p : gbmParamStrings) {
      switch (p) {
        case "_distribution": // auto,gaussian,binomial,multinomial,poisson,gamma,tweedie
          if      (tokens[0].equals("x")) { gbmParams._distribution = Distribution.Family.AUTO; }
          else if (tokens[1].equals("x")) { gbmParams._distribution = Distribution.Family.gaussian; }
          else if (tokens[2].equals("x")) { gbmParams._distribution = Distribution.Family.bernoulli; }
          else if (tokens[3].equals("x")) { gbmParams._distribution = Distribution.Family.multinomial; }
          else if (tokens[4].equals("x")) { gbmParams._distribution = Distribution.Family.poisson; }
          else if (tokens[5].equals("x")) { gbmParams._distribution = Distribution.Family.gamma; }
          else if (tokens[6].equals("x")) { gbmParams._distribution = Distribution.Family.tweedie; }
          break;
        case "_nfolds":
          if (!tokens[7].isEmpty()) { gbmParams._nfolds = Integer.parseInt(tokens[7]); }
          break;
        case "_fold_column":
          if (!tokens[8].isEmpty()) { gbmParams._fold_column = tokens[8]; }
          break;
        case "_ignore_const_cols":
          if (!tokens[9].isEmpty()) { gbmParams._ignore_const_cols = true; }
          break;
        case "_offset_column":
          if (!tokens[10].isEmpty()) { gbmParams._offset_column = tokens[10]; }
          break;
        case "_weights_column":
          if (!tokens[11].isEmpty()) { gbmParams._weights_column = tokens[11]; }
          break;
        case "_ntrees":
          if (!tokens[12].isEmpty()) { gbmParams._ntrees = Integer.parseInt(tokens[12]); }
          break;
        case "_max_depth":
          if (!tokens[13].isEmpty()) { gbmParams._max_depth = Integer.parseInt(tokens[13]); }
          break;
        case "_min_rows":
          if (!tokens[14].isEmpty()) { gbmParams._min_rows = Double.parseDouble(tokens[14]); }
          break;
        case "_nbins":
          if (!tokens[15].isEmpty()) { gbmParams._nbins = Integer.parseInt(tokens[15]); }
          break;
        case "_nbins_cats":
          if (!tokens[16].isEmpty()) { gbmParams._nbins_cats = Integer.parseInt(tokens[16]); }
          break;
        case "_learn_rate":
          if (!tokens[17].isEmpty()) { gbmParams._learn_rate = Float.parseFloat(tokens[17]); }
          break;
        case "_score_each_iteration":
          if (!tokens[18].isEmpty()) { gbmParams._score_each_iteration = true; }
          break;
        case "_balance_classes":
          if (!tokens[19].isEmpty()) { gbmParams._balance_classes = true; }
          break;
        case "_max_confusion_matrix_size":
          if (!tokens[20].isEmpty()) { gbmParams._max_confusion_matrix_size = Integer.parseInt(tokens[20]); }
          break;
        case "_max_hit_ratio_k":
          if (!tokens[21].isEmpty()) { gbmParams._max_hit_ratio_k = Integer.parseInt(tokens[21]); }
          break;
        case "_r2_stopping":
          if (!tokens[22].isEmpty()) { gbmParams._r2_stopping = Double.parseDouble(tokens[22]); }
          break;
        case "_build_tree_one_node":
          if (!tokens[23].isEmpty()) { gbmParams._build_tree_one_node = true; }
          break;
        case "_class_sampling_factors":
          if (!tokens[24].isEmpty()) {
            Log.info("_class_sampling_factors not supported for drf test cases");
            System.exit(-1);
          }
          break;
        case "_sample_rate":
          if (!tokens[25].isEmpty()) { gbmParams._sample_rate = Float.parseFloat(tokens[25]); }
          break;
        case "_col_sample_rate":
          if (!tokens[26].isEmpty()) { gbmParams._col_sample_rate = Float.parseFloat(tokens[26]); }
          break;
        case "_train":
          gbmParams._train = trainingDataSet.getFrame()._key;
          break;
        case "_valid":
          gbmParams._valid = testingDataSet.getFrame()._key;
          break;
        case "_response_column":
          gbmParams._response_column = trainingDataSet.getFrame()._names[trainingDataSet.getResponseColumn()];
      }
    }
    return gbmParams;
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
}

