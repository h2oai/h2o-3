package hex.tree.xgboost;

import biz.k11i.xgboost.Predictor;
import biz.k11i.xgboost.gbm.GBTree;
import biz.k11i.xgboost.tree.RegTree;
import biz.k11i.xgboost.tree.RegTreeNodeStat;
import hex.*;
import hex.Model.Parameters.CategoricalEncodingScheme;
import hex.genmodel.MojoModel;
import hex.genmodel.MojoReaderBackend;
import hex.genmodel.MojoReaderBackendFactory;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.genmodel.algos.xgboost.XGBoostMojoModel;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.exception.PredictException;
import hex.genmodel.easy.prediction.BinomialModelPrediction;
import hex.genmodel.utils.DistributionFamily;
import hex.FeatureInteraction;
import hex.FeatureInteractions;
import hex.schemas.XGBoostV3;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import hex.tree.xgboost.predict.AuxNodeWeights;
import hex.tree.xgboost.predict.XGBoostNativeVariableImportance;
import hex.tree.xgboost.task.XGBoostSetupTask;
import hex.tree.xgboost.util.BoosterDump;
import hex.tree.xgboost.util.BoosterHelper;
import hex.tree.xgboost.util.FeatureScore;
import ai.h2o.xgboost4j.java.DMatrix;
import ai.h2o.xgboost4j.java.XGBoost;
import ai.h2o.xgboost4j.java.*;
import hex.tree.xgboost.util.GpuUtils;
import org.apache.log4j.Logger;
import org.hamcrest.CoreMatchers;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.*;
import water.rapids.Rapids;
import water.util.ArrayUtils;
import water.util.PojoUtils;
import water.util.TwoDimTable;

import java.io.*;
import java.util.*;
import java.util.logging.Filter;
import java.util.stream.Collectors;

import static hex.Model.Contributions.*;
import static hex.Model.Parameters.CategoricalEncodingScheme.Eigen;
import static hex.Model.Parameters.CategoricalEncodingScheme.Enum;
import static hex.genmodel.utils.DistributionFamily.*;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static water.util.FileUtils.getFile;

@RunWith(Parameterized.class)
public class XGBoostTest extends TestUtil {

  private static final Logger LOG = Logger.getLogger(XGBoostTest.class);

  @Parameterized.Parameters(name = "XGBoost(javaPredict={0}")
  public static Collection<Object> data() {
    return Arrays.asList(new Object[]{
            "true", "false"
    });
  }

  @Parameterized.Parameter
  public String confJavaPredict;

  @Rule
  public transient ExpectedException thrown = ExpectedException.none();

  @Rule
  public transient TemporaryFolder tmp = new TemporaryFolder();
  
  @Before
  public void setupMojoJavaScoring() {
    System.setProperty("sys.ai.h2o.xgboost.predict.java.enable", confJavaPredict); // in-h2o predict
  }
  
  @After
  public void cleanupProperties() {
    System.clearProperty("sys.ai.h2o.xgboost.predict.java.enable");
  }

  public static final class FrameMetadata {
    Vec[] vecs;
    String[] names;
    long[] checksums;
    String[][] domains;

    public FrameMetadata(Frame f) {
      vecs = f.vecs();
      names = f.names();

      checksums = new long[vecs.length];
      for (int i = 0; i < vecs.length; i++)
        checksums[i] = vecs[i].checksum();

      domains = new String[vecs.length][];
      for (int i = 0; i < vecs.length; i++)
        domains[i] = vecs[i].domain();
    }

    @Override
    public boolean equals(Object o) {
      if (! (o instanceof FrameMetadata))
        return false;

      FrameMetadata fm = (FrameMetadata)o;

      boolean error = false;

      if (vecs.length != fm.vecs.length) {
        LOG.warn("Training frame vec count has changed from: " +
                vecs.length + " to: " + fm.vecs.length);
        error = true;
      }
      if (names.length != fm.names.length) {
        LOG.warn("Training frame vec count has changed from: " +
                names.length + " to: " + fm.names.length);
        error = true;
      }

      for (int i = 0; i < fm.vecs.length; i++) {
        if (!fm.vecs[i].equals(fm.vecs[i])) {
          LOG.warn("Training frame vec number " + i + " has changed keys.  Was: " +
                  vecs[i] + " , now: " + fm.vecs[i]);
          error = true;
        }
        if (!fm.names[i].equals(fm.names[i])) {
          LOG.warn("Training frame vec number " + i + " has changed names.  Was: " +
                  names[i] + " , now: " + fm.names[i]);
          error = true;
        }
        if (checksums[i] != fm.vecs[i].checksum()) {
          LOG.warn("Training frame vec number " + i + " has changed checksum.  Was: " +
                  checksums[i] + " , now: " + fm.vecs[i].checksum());
          error = true;
        }
        if (domains[i] != null && ! Arrays.equals(domains[i], fm.vecs[i].domain())) {
          LOG.warn("Training frame vec number " + i + " has changed domain.  Was: " +
                  domains[i] + " , now: " + fm.vecs[i].domain());
          error = true;
        }
      }

      return !error;
    }
  }

  @BeforeClass public static void stall() {
    stall_till_cloudsize(1);

    // we need to check for XGBoost backend availability after H2O is initialized, since we
    // XGBoost is a core extension and they are registered as part of the H2O's class main method
    Assume.assumeTrue("XGBoost was not loaded!\n"
                    + "H2O XGBoost needs binary compatible environment;"
                    + "Make sure that you have correct libraries installed"
                    + "and correctly configured LD_LIBRARY_PATH, especially"
                    + "make sure that CUDA libraries are available if you are running on GPU!",
            ExtensionManager.getInstance().isCoreExtensionsEnabled(XGBoostExtension.NAME));
  }


  private static DMatrix[] getMatrices() throws XGBoostError, IOException {
    // load file from text file, also binary buffer generated by xgboost4j
    return new DMatrix[]{
            new DMatrix(getFile("smalldata/xgboost/demo/data/agaricus.txt.train").getAbsolutePath() + "?indexing_mode=1"),
            new DMatrix(getFile("smalldata/xgboost/demo/data/agaricus.txt.test").getAbsolutePath() + "?indexing_mode=1")
    };
  }
  static void saveDumpModel(File modelFile, String[] modelInfos) throws IOException {
    try{
      PrintWriter writer = new PrintWriter(modelFile, "UTF-8");
      for(int i = 0; i < modelInfos.length; ++ i) {
        writer.print("booster[" + i + "]:\n");
        writer.print(modelInfos[i]);
      }
      writer.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  static boolean checkPredicts(float[][] fPredicts, float[][] sPredicts) {
    if (fPredicts.length != sPredicts.length) {
      return false;
    }

    for (int i = 0; i < fPredicts.length; i++) {
      if (!Arrays.equals(fPredicts[i], sPredicts[i])) {
        return false;
      }
    }

    return true;
  }

  @Test
  public void testMatrices() throws XGBoostError, IOException {
    Map<String, String> rabitEnv = new HashMap<>();
    rabitEnv.put("DMLC_TASK_ID", "0");
    Rabit.init(rabitEnv);
    getMatrices();
    Rabit.shutdown();
  }

  @Test public void BasicModel() throws XGBoostError, IOException {
    Map<String, String> rabitEnv = new HashMap<>();
    rabitEnv.put("DMLC_TASK_ID", "0");
    Rabit.init(rabitEnv);
    // load file from text file, also binary buffer generated by xgboost4j
    DMatrix[] mat = getMatrices();
    DMatrix trainMat = mat[0];
    DMatrix testMat = mat[1];

    HashMap<String, Object> params = new HashMap<>();
    params.put("eta", 0.1);
    params.put("max_depth", 5);
    params.put("silent", 1);
    params.put("objective", "binary:logistic");

    HashMap<String, DMatrix> watches = new HashMap<>();
    watches.put("train", trainMat);
    watches.put("test",  testMat);

    Booster booster = XGBoost.train(trainMat, params, 10, watches, null, null);
    float[][] preds = booster.predict(trainMat, true);
    float[][] contribs = booster.predictContrib(trainMat, 0);
    for (int i = 0; i < preds.length; ++i) {
      float[] ps = preds[i];
      float[] cs = contribs[i];
      if (i < 10) {
        LOG.info(ps[0] + " = Sum" + Arrays.toString(cs).replaceAll("0.0, ", ""));
      }
      assertEquals(ps[0], ArrayUtils.sum(cs), 1e-6);
    }
    Rabit.shutdown();
  }

  @Test public void testScoring() throws XGBoostError, IOException {
    Map<String, String> rabitEnv = new HashMap<>();
    rabitEnv.put("DMLC_TASK_ID", "0");
    Rabit.init(rabitEnv);
    // load file from text file, also binary buffer generated by xgboost4j
    DMatrix[] mat = getMatrices();
    DMatrix trainMat = mat[0];
    DMatrix testMat = mat[1];

    HashMap<String, Object> params = new HashMap<>();
    params.put("eta", 0.1);
    params.put("max_depth", 5);
    params.put("silent", 1);
    params.put("objective", "reg:linear");

    HashMap<String, DMatrix> watches = new HashMap<>();
    watches.put("train", trainMat);
    watches.put("test",  testMat);

    Booster booster = XGBoost.train(trainMat, params, 10, watches, null, null);
    // slice some rows out and predict on those
    float[][] preds1 = booster.predict(trainMat.slice(new int[]{0}));
    float[][] preds2 = booster.predict(trainMat.slice(new int[]{1}));
    float[][] preds3 = booster.predict(trainMat.slice(new int[]{2}));
    float[][] preds4 = booster.predict(trainMat.slice(new int[]{0,1,2}));

    assertEquals(preds1.length, 1);
    assertEquals(1, preds2.length);
    assertEquals(1, preds3.length);
    assertEquals(3, preds4.length);

    assertEquals(preds4[0][0], preds1[0][0], 1e-10);
    assertEquals(preds4[1][0], preds2[0][0], 1e-10);
    assertEquals(preds4[2][0], preds3[0][0], 1e-10);
    assertNotEquals(preds4[0][0], preds4[1][0], 1e-10);
    assertNotEquals(preds4[0][0],preds4[2][0], 1e-10);
    Rabit.shutdown();
  }

  @Test public void testScore0() throws XGBoostError {
    Map<String, String> rabitEnv = new HashMap<>();
    rabitEnv.put("DMLC_TASK_ID", "0");
    Rabit.init(rabitEnv);
    // trivial dataset with 3 rows and 2 columns
    // (4,5) -> 1
    // (3,1) -> 2
    // (2,3) -> 3
    DMatrix trainMat = new DMatrix(new float[]{4f,5f, 3f,1f, 2f,3f},3,2);
    trainMat.setLabel(new float[]{             1f,    2f,    3f       });

    HashMap<String, Object> params = new HashMap<>();
    params.put("eta", 1);
    params.put("max_depth", 5);
    params.put("silent", 1);
    params.put("objective", "reg:linear");

    HashMap<String, DMatrix> watches = new HashMap<>();
    watches.put("train", trainMat);

    Booster booster = XGBoost.train(trainMat, params, 10, watches, null, null);

    // check overfitting
    // (4,5) -> 1
    // (3,1) -> 2
    // (2,3) -> 3
    float[][] preds1 = booster.predict(new DMatrix(new float[]{4f,5f},1,2));
    float[][] preds2 = booster.predict(new DMatrix(new float[]{3f,1f},1,2));
    float[][] preds3 = booster.predict(new DMatrix(new float[]{2f,3f},1,2));

    assertEquals(preds1.length, 1);
    assertEquals(preds2.length, 1);
    assertEquals(preds3.length, 1);

    assertTrue(Math.abs(preds1[0][0]-1) < 1e-2);
    assertTrue(Math.abs(preds2[0][0]-2) < 1e-2);
    assertTrue(Math.abs(preds3[0][0]-3) < 1e-2);
    Rabit.shutdown();
  }

  @Test public void testExtendedFeatureScore() throws XGBoostError, IOException {
    Map<String, String> rabitEnv = new HashMap<>();
    rabitEnv.put("DMLC_TASK_ID", "0");
    Rabit.init(rabitEnv);
    // load file from text file, also binary buffer generated by xgboost4j
    DMatrix[] mat = getMatrices();
    DMatrix trainMat = mat[0];
    DMatrix testMat = mat[1];

    HashMap<String, Object> params = new HashMap<>();
    params.put("eta", 0.1);
    params.put("max_depth", 5);
    params.put("silent", 1);
    params.put("objective", "reg:linear");
    params.put("seed", 12);

    HashMap<String, DMatrix> watches = new HashMap<>();
    watches.put("train", trainMat);
    watches.put("test",  testMat);

    Booster booster = XGBoost.train(trainMat, params, 10, watches, null, null);

    final Map<String, Integer> expected = booster.getFeatureScore((String) null);
    final Map<String, FeatureScore> actual = getExtFeatureScore(booster);

    assertEquals(expected.keySet(), actual.keySet());
    for (String feature : expected.keySet()) {
      assertEquals((int) expected.get(feature), actual.get(feature)._frequency);
    }

    Booster booster1 = XGBoost.train(trainMat, params, 1, watches, null, null);
    Booster booster2 = XGBoost.train(trainMat, params, 2, watches, null, null);

    // Check that gain(booster2) >= gain(booster1)
    final Map<String, FeatureScore> fs1 = getExtFeatureScore(booster1);
    final Map<String, FeatureScore> fs2 = getExtFeatureScore(booster2);

    for (String feature : fs2.keySet()) {
      assertTrue(fs2.get(feature)._gain > 0);
      assertTrue(fs1.get(feature)._gain <= fs2.get(feature)._gain);
    }
    
    Rabit.shutdown();
  }

  private Map<String, FeatureScore> getExtFeatureScore(Booster booster) throws XGBoostError {
    String[] modelDump = booster.getModelDump((String) null, true);
    return XGBoostNativeVariableImportance.parseFeatureScores(modelDump);
  }

  @Test
  public void saveLoadDataAndModel() throws XGBoostError, IOException {
    Map<String, String> rabitEnv = new HashMap<>();
    rabitEnv.put("DMLC_TASK_ID", "0");
    Rabit.init(rabitEnv);
    // load file from text file, also binary buffer generated by xgboost4j
    DMatrix[] mat = getMatrices();
    DMatrix trainMat = mat[0];
    DMatrix testMat = mat[1];

    HashMap<String, Object> params = new HashMap<>();
    params.put("eta", 0.1);
    params.put("max_depth", 5);
    params.put("silent", 1);
    params.put("objective", "binary:logistic");

    HashMap<String, DMatrix> watches = new HashMap<>();
    watches.put("train", trainMat);
    watches.put("test",  testMat);

    Booster booster = XGBoost.train(trainMat, params, 10, watches, null, null);

    float[][] predicts = booster.predict(testMat);

    //save model to modelPath
    File modelDir = java.nio.file.Files.createTempDirectory("xgboost-model").toFile();

    booster.saveModel(path(modelDir, "xgb.model"));

    //dump model with feature map
    String[] modelInfos = booster.getModelDump(getFile("smalldata/xgboost/demo/data/featmap.txt").getAbsolutePath(), false);
    saveDumpModel(new File(modelDir, "dump.raw.txt"), modelInfos);

    //save dmatrix into binary buffer
    testMat.saveBinary(path(modelDir, "dtest.buffer"));

    //reload model and data
    Booster booster2 = XGBoost.loadModel(path(modelDir, "xgb.model"));
    DMatrix testMat2 = new DMatrix(path(modelDir, "dtest.buffer"));
    float[][] predicts2 = booster2.predict(testMat2);

    //check the two predicts
    System.out.println(checkPredicts(predicts, predicts2));

    //specify watchList
    HashMap<String, DMatrix> watches2 = new HashMap<>();
    watches2.put("train", trainMat);
    watches2.put("test", testMat2);
    Booster booster3 = XGBoost.train(trainMat, params, 10, watches2, null, null);
    float[][] predicts3 = booster3.predict(testMat2);

    //check predicts
    System.out.println(checkPredicts(predicts, predicts3));
    Rabit.shutdown();
  }

  private static String path(File parentDir, String fileName) {
    return new File(parentDir, fileName).getAbsolutePath();
  }

  @Test
  public void checkpoint() throws XGBoostError, IOException {
    Map<String, String> rabitEnv = new HashMap<>();
    rabitEnv.put("DMLC_TASK_ID", "0");
    Rabit.init(rabitEnv);
    // load file from text file, also binary buffer generated by xgboost4j
    DMatrix[] mat = getMatrices();
    DMatrix trainMat = mat[0];
    DMatrix testMat = mat[1];

    HashMap<String, Object> params = new HashMap<>();
    params.put("eta", 0.1);
    params.put("max_depth", 5);
    params.put("silent", 1);
    params.put("objective", "binary:logistic");

    HashMap<String, DMatrix> watches = new HashMap<>();
    watches.put("train", trainMat);

    Booster booster = XGBoost.train(trainMat, params, 0, watches, null, null);
    // Train for 10 iterations
    for (int i=0;i<10;++i) {
      booster.update(trainMat, i);
      float[][] preds = booster.predict(testMat);
      for (int j = 0; j < 10; ++j)
        LOG.info(preds[j][0]);
    }
    Rabit.shutdown();
  }
  
  public static Frame loadWeather(String response) {
    Frame df = parseTestFile("./smalldata/junit/weather.csv");
    int responseIdx = df.find(response);
    Scope.track(df.replace(responseIdx, df.vecs()[responseIdx].toCategoricalVec()));
    // remove columns correlated with the response
    df.remove("RISK_MM").remove();
    df.remove("EvapMM").remove();
    DKV.put(df);
    return Scope.track(df);
  }

  @Test
  public void WeatherBinary() {
    Scope.enter();
    try {
      String response = "RainTomorrow";
      Frame df = loadWeather(response);
      FrameMetadata metadataBefore = new FrameMetadata(df);  // make sure it's after removing those columns!

      // split into train/test
      SplitFrame sf = new SplitFrame(df, new double[] { 0.7, 0.3 }, null);
      sf.exec().get();
      Key<Frame>[] ksplits = sf._destination_frames;
      Frame trainFrame = Scope.track(ksplits[0].get());
      Frame testFrame = Scope.track(ksplits[1].get());

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._ntrees = 5;
      parms._max_depth = 5;
      parms._train = trainFrame._key;
      parms._valid = testFrame._key;
      parms._response_column = response;

      XGBoostModel model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      Scope.track_generic(model);
      LOG.info(model);

      FrameMetadata metadataAfter = new FrameMetadata(df);
      assertEquals(metadataBefore, metadataAfter);

      Frame preds = Scope.track(model.score(testFrame));
      assertJavaScoring(model, testFrame, preds);
      assertEquals(
              ModelMetricsBinomial.make(preds.vec(2), testFrame.vec(response)).auc(),
              ((ModelMetricsBinomial) model._output._validation_metrics).auc(),
              1e-5
      );
      assertTrue(preds.anyVec().sigma() > 0);

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void WeatherBinaryCV() {
    try {
      Scope.enter();
      String response = "RainTomorrow";
      Frame df = loadWeather(response);
      FrameMetadata metadataBefore = new FrameMetadata(df);  // make sure it's after removing those columns!

      // split into train/test
      SplitFrame sf = new SplitFrame(df, new double[] { 0.7, 0.3 }, null);
      sf.exec().get();
      Key<Frame>[] ksplits = sf._destination_frames;
      Frame trainFrame = Scope.track(ksplits[0].get());
      Frame testFrame = Scope.track(ksplits[1].get());


      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._ntrees = 5;
      parms._max_depth = 5;
      parms._train = trainFrame._key;
      parms._valid = testFrame._key;
      parms._nfolds = 5;
      parms._response_column = response;

      XGBoostModel model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      Scope.track_generic(model);
      LOG.info(model);

      FrameMetadata metadataAfter = new FrameMetadata(df);
      assertEquals(metadataBefore, metadataAfter);

      Frame preds = Scope.track(model.score(testFrame));
      assertJavaScoring(model, testFrame, preds);
      assertEquals(
              ((ModelMetricsBinomial)model._output._validation_metrics).auc(),
              ModelMetricsBinomial.make(preds.vec(2), testFrame.vec(response)).auc(),
              1e-5
      );
      assertTrue(preds.anyVec().sigma() > 0);

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testWeatherBinaryCVEarlyStopping() {
    try {
      Scope.enter();
      final String response = "RainTomorrow";
      Frame df = loadWeather(response);

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._ntrees = 50;
      parms._max_depth = 5;
      parms._train = df._key;
      parms._nfolds = 5;
      parms._response_column = response;
      parms._stopping_rounds = 3;
      parms._score_tree_interval = 1;
      parms._seed = 123;
      parms._keep_cross_validation_models = true;

      XGBoostModel model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      Scope.track_generic(model);
      final int ntrees = model._output._ntrees;

      int expected = 0;
      for (Key k : model._output._cross_validation_models) {
        expected += ((XGBoostModel) k.get())._output._ntrees;
      }
      expected = (int) ((double) expected) / model._output._cross_validation_models.length;
      assertEquals(expected, ntrees);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void WeatherBinaryCheckpoint() {
    Scope.enter();
    try {
      String response = "RainTomorrow";
      Frame df = loadWeather(response);

      XGBoostModel.XGBoostParameters directParms = new XGBoostModel.XGBoostParameters();
      directParms._ntrees = 10;
      directParms._max_depth = 5;
      directParms._train = df._key;
      directParms._response_column = response;
      XGBoostModel directModel = new hex.tree.xgboost.XGBoost(directParms).trainModel().get();
      Scope.track_generic(directModel);
      Frame directPreds = Scope.track(directModel.score(df));

      XGBoostModel.XGBoostParameters step1Parms = new XGBoostModel.XGBoostParameters();
      step1Parms._ntrees = 5;
      step1Parms._max_depth = 5;
      step1Parms._train = df._key;
      step1Parms._response_column = response;
      XGBoostModel step1Model = new hex.tree.xgboost.XGBoost(step1Parms).trainModel().get();
      Scope.track_generic(step1Model);

      XGBoostModel.XGBoostParameters step2Parms = new XGBoostModel.XGBoostParameters();
      step2Parms._ntrees = 10;
      step2Parms._max_depth = 5;
      step2Parms._train = df._key;
      step2Parms._response_column = response;
      step2Parms._checkpoint = step1Model._key;
      XGBoostModel step2Model = new hex.tree.xgboost.XGBoost(step2Parms).trainModel().get();
      Scope.track_generic(step2Model);
      Frame step2Preds = Scope.track(step2Model.score(df));

      // on GPU the resume from checkpoint is slightly in-deterministic
      double delta = (GpuUtils.hasGPU(H2O.CLOUD.members()[0], null)) ? 1e-6d : 0;
      assertFrameEquals(directPreds, step2Preds, delta);
    } finally {
      Scope.exit();
    }
  }

  @Test(expected = H2OModelBuilderIllegalArgumentException.class)
  public void RegressionCars() {
    Frame tfr = null;
    Frame trainFrame = null;
    Frame testFrame = null;
    Frame preds = null;
    XGBoostModel model = null;
    Scope.enter();
    try {
      // Parse frame into H2O
      tfr = parseTestFile("./smalldata/junit/cars.csv");
      FrameMetadata metadataBefore = new FrameMetadata(tfr);
      DKV.put(tfr);

      Scope.track(tfr.replace(1, tfr.vecs()[1].toCategoricalVec()));   // Convert CAPSULE to categorical
      Scope.track(tfr.replace(3, tfr.vecs()[3].toCategoricalVec()));   // Convert RACE to categorical
      DKV.put(tfr);

      // split into train/test
      SplitFrame sf = new SplitFrame(tfr, new double[] { 0.7, 0.3 }, null);
      sf.exec().get();
      Key[] ksplits = sf._destination_frames;
      trainFrame = (Frame)ksplits[0].get();
      testFrame = (Frame)ksplits[1].get();

      String response = "economy (mpg)";

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._train = trainFrame._key;
      parms._valid = testFrame._key;
      parms._response_column = response;
      parms._ignored_columns = new String[]{"name"};

      model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      LOG.info(model);

      FrameMetadata metadataAfter = new FrameMetadata(tfr);
      assertEquals(metadataBefore, metadataAfter);

      preds = model.score(testFrame);
      assertJavaScoring(model, testFrame, preds);
      assertEquals(
              ((ModelMetricsRegression)model._output._validation_metrics).mae(),
              ModelMetricsRegression.make(preds.anyVec(), testFrame.vec(response), DistributionFamily.gaussian).mae(),
              1e-5
      );
      assertTrue(preds.anyVec().sigma() > 0);

    } finally {
      Scope.exit();
      if (trainFrame!=null) trainFrame.remove();
      if (testFrame!=null) testFrame.remove();
      if (tfr!=null) tfr.remove();
      if (preds!=null) preds.remove();
      if (model!=null) {
        model.delete();
      }
    }
  }

  @Test
  public void ProstateRegression() {
    Frame tfr = null;
    Frame trainFrame = null;
    Frame testFrame = null;
    Frame preds = null;
    XGBoostModel model = null;
    Scope.enter();
    try {
      // Parse frame into H2O
      tfr = parseTestFile("./smalldata/prostate/prostate.csv");
      FrameMetadata metadataBefore = new FrameMetadata(tfr);

      Scope.track(tfr.replace(1, tfr.vecs()[1].toCategoricalVec()));   // Convert CAPSULE to categorical
      Scope.track(tfr.replace(3, tfr.vecs()[3].toCategoricalVec()));   // Convert RACE to categorical
      DKV.put(tfr);

      // split into train/test
      SplitFrame sf = new SplitFrame(tfr, new double[] { 0.7, 0.3 }, null);
      sf.exec().get();
      Key[] ksplits = sf._destination_frames;
      trainFrame = (Frame)ksplits[0].get();
      testFrame = (Frame)ksplits[1].get();

      // define special columns
      String response = "AGE";

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._train = trainFrame._key;
      parms._valid = testFrame._key;
      parms._response_column = response;
      parms._ignored_columns = new String[]{"ID"};

      model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      LOG.info(model);

      FrameMetadata metadataAfter = new FrameMetadata(tfr);
      assertEquals(metadataBefore, metadataAfter);

      preds = model.score(testFrame);
      assertJavaScoring(model, testFrame, preds);
      assertEquals(
              ((ModelMetricsRegression)model._output._validation_metrics).mae(),
              ModelMetricsRegression.make(preds.anyVec(), testFrame.vec(response), DistributionFamily.gaussian).mae(),
              1e-5
      );
      assertTrue(preds.anyVec().sigma() > 0);
    } finally {
      Scope.exit();
      if (trainFrame!=null) trainFrame.remove();
      if (testFrame!=null) testFrame.remove();
      if (tfr!=null) tfr.remove();
      if (preds!=null) preds.remove();
      if (model!=null) {
        model.delete();
      }
    }
  }

  @Test
  public void sparseMatrixDetectionTest() {
    Frame tfr = null;
    XGBoostModel model = null;
    Scope.enter();
    try {
      tfr = parseTestFile("./smalldata/prostate/prostate.csv");
      Scope.track(tfr.replace(8, tfr.vecs()[8].toCategoricalVec()));   // Convert GLEASON to categorical
      DKV.put(tfr);

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      // Automatic detection should compute sparsity and decide
      parms._dmatrix_type = XGBoostModel.XGBoostParameters.DMatrixType.auto;
      parms._response_column = "AGE";
      parms._train = tfr._key;
      parms._ignored_columns = new String[]{"ID","DPROS", "DCAPS", "PSA", "VOL", "RACE", "CAPSULE"};

      model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      assertTrue(model._output._sparse);

    } finally {
      Scope.exit();
      if (tfr!=null) tfr.remove();
      if (model!=null) {
        model.delete();
        model.deleteCrossValidationModels();
      }
    }

  }

  @Test
  public void testNativeParams() {
    Frame tfr = null;
    XGBoostModel model = null;
    Scope.enter();
    try {
      tfr = parseTestFile("./smalldata/prostate/prostate.csv");

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._train = tfr._key;
      parms._tree_method = XGBoostModel.XGBoostParameters.TreeMethod.hist;
      parms._grow_policy = XGBoostModel.XGBoostParameters.GrowPolicy.lossguide;
      parms._response_column = "AGE";
      parms._ignored_columns = new String[]{"ID"};
      parms._backend = XGBoostModel.XGBoostParameters.Backend.cpu;
      parms._max_bins = 10;

      model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();

      TwoDimTable np = model._output._native_parameters;
      assertNotNull(np);
      System.out.println(np.toString());

      Set<String> names = new HashSet<>();
      for (int i = 0; i < np.getRowDim(); i++) {
        names.add(String.valueOf(np.get(i, 0)));
      }
      assertEquals(names, new HashSet<>(Arrays.asList(
              "colsample_bytree", "silent", "tree_method", "seed", "max_depth", "booster", "objective", "nround",
              "lambda", "eta", "grow_policy", "nthread", "alpha", "colsample_bylevel", "subsample", "min_child_weight",
              "gamma", "max_delta_step", "max_bin", "max_leaves")));
    } finally {
      Scope.exit();
      if (tfr!=null) tfr.remove();
      if (model!=null) {
        model.delete();
        model.deleteCrossValidationModels();
      }
    }
  }

  @Test
  public void testBinomialTrainingWeights() {
    XGBoostModel model = null;
    XGBoostModel noWeightsModel = null;
    Scope.enter();
    try {
      Frame airlinesFrame = Scope.track(parseTestFile("./smalldata/testng/airlines.csv"));
      airlinesFrame.replace(0, airlinesFrame.vecs()[0].toCategoricalVec()).remove();

      final Vec weightsVector = createRandomBinaryWeightsVec(airlinesFrame.numRows(), 10);
      final String weightsColumnName = "weights";
      airlinesFrame.add(weightsColumnName, weightsVector);
      DKV.put(airlinesFrame);


      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._dmatrix_type = XGBoostModel.XGBoostParameters.DMatrixType.auto;
      parms._response_column = "IsDepDelayed";
      parms._train = airlinesFrame._key;
      parms._backend = XGBoostModel.XGBoostParameters.Backend.cpu;
      parms._weights_column = weightsColumnName;
      parms._ignored_columns = new String[]{"fYear", "fMonth", "fDayofMonth", "fDayOfWeek"};

      model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      assertEquals(ModelCategory.Binomial, model._output.getModelCategory());
      assertEquals(model._output.weightsName(), weightsColumnName);

      Frame trainingFrameSubset = Rapids.exec(String.format("(rows %s ( == (cols %s [9]) 1))", airlinesFrame._key, airlinesFrame._key)).getFrame();
      trainingFrameSubset._key = Key.make();
      Scope.track(trainingFrameSubset);
      assertEquals(airlinesFrame.vec(weightsColumnName).nzCnt(), trainingFrameSubset.numRows());
      DKV.put(trainingFrameSubset);
      parms._weights_column = null;
      parms._train = trainingFrameSubset._key;

      noWeightsModel = new hex.tree.xgboost.XGBoost(parms).trainModel().get();

      Vec predicted = Scope.track(noWeightsModel.score(trainingFrameSubset)).vec(2);
      ModelMetricsBinomial expected = ModelMetricsBinomial.make(predicted, trainingFrameSubset.vec("IsDepDelayed"));

      checkMetrics(expected, (ModelMetricsBinomial) noWeightsModel._output._training_metrics);
      checkMetrics(expected, (ModelMetricsBinomial) model._output._training_metrics);
    } finally {
      Scope.exit();
      if (model != null) model.delete();
      if (noWeightsModel != null) noWeightsModel.delete();
    }

  }

  @Test
  public void testRegressionTrainingWeights() {
    XGBoostModel model = null;
    XGBoostModel noWeightsModel = null;
    Scope.enter();
    try {
      Frame prostateFrame = parseTestFile("./smalldata/prostate/prostate.csv");
      prostateFrame.replace(8, prostateFrame.vecs()[8].toCategoricalVec()).remove();   // Convert GLEASON to categorical
      Scope.track(prostateFrame);

      final Vec weightsVector = createRandomBinaryWeightsVec(prostateFrame.numRows(), 10);
      final String weightsColumnName = "weights";
      prostateFrame.add(weightsColumnName, weightsVector);
      DKV.put(prostateFrame);


      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._dmatrix_type = XGBoostModel.XGBoostParameters.DMatrixType.auto;
      parms._response_column = "AGE";
      parms._train = prostateFrame._key;
      parms._weights_column = weightsColumnName;
      parms._ignored_columns = new String[]{"ID"};

      model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      assertEquals(ModelCategory.Regression, model._output.getModelCategory());
      assertEquals(weightsColumnName, model._output.weightsName());

      Frame trainingFrameSubset = Rapids.exec(String.format("(rows %s ( == (cols %s [9]) 1))", prostateFrame._key, prostateFrame._key)).getFrame();
      trainingFrameSubset._key = Key.make();
      Scope.track(trainingFrameSubset);
      assertEquals(prostateFrame.vec(weightsColumnName).nzCnt(), trainingFrameSubset.numRows());
      DKV.put(trainingFrameSubset);
      parms._weights_column = null;
      parms._train = trainingFrameSubset._key;

      noWeightsModel = new hex.tree.xgboost.XGBoost(parms).trainModel().get();

      Vec predicted = Scope.track(noWeightsModel.score(trainingFrameSubset)).vec(0);
      ModelMetricsRegression expected = ModelMetricsRegression.make(predicted, trainingFrameSubset.vec("AGE"), DistributionFamily.gaussian);

      checkMetrics(expected, (ModelMetricsRegression) noWeightsModel._output._training_metrics);
      checkMetrics(expected, (ModelMetricsRegression) model._output._training_metrics);
    } finally {
      Scope.exit();
      if (model != null) model.delete();
      if (noWeightsModel != null) noWeightsModel.delete();
    }

  }

  @Test
  public void testMultinomialTrainingWeights() {
    XGBoostModel model = null;
    XGBoostModel noWeightsModel = null;
    Scope.enter();
    try {
      Frame irisFrame = parseTestFile("./smalldata/extdata/iris.csv");
      irisFrame.replace(4, irisFrame.vecs()[4].toCategoricalVec()).remove();
      Scope.track(irisFrame);

      final Vec weightsVector = createRandomBinaryWeightsVec(irisFrame.numRows(), 10);
      final String weightsColumnName = "weights";
      irisFrame.add(weightsColumnName, weightsVector);
      DKV.put(irisFrame);

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._dmatrix_type = XGBoostModel.XGBoostParameters.DMatrixType.auto;
      parms._response_column = "C5"; // iris-setosa, iris-versicolor, iris-virginica
      parms._train = irisFrame._key;
      parms._weights_column = weightsColumnName;

      model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      assertEquals(ModelCategory.Multinomial, model._output.getModelCategory());
      assertEquals(weightsColumnName, model._output.weightsName());

      Frame trainingFrameSubset = Rapids.exec(String.format("(rows %s ( == (cols %s [5]) 1))", irisFrame._key, irisFrame._key)).getFrame();
      trainingFrameSubset._key = Key.make();
      Scope.track(trainingFrameSubset);
      assertEquals(irisFrame.vec(weightsColumnName).nzCnt(), trainingFrameSubset.numRows());
      DKV.put(trainingFrameSubset);
      parms._weights_column = null;
      parms._train = trainingFrameSubset._key;
      parms._fold_assignment = Model.Parameters.FoldAssignmentScheme.AUTO;

      noWeightsModel = new hex.tree.xgboost.XGBoost(parms).trainModel().get();

      Frame predicted = Scope.track(noWeightsModel.score(trainingFrameSubset));
      predicted.remove(0);
      ModelMetricsMultinomial expected = ModelMetricsMultinomial.make(predicted, trainingFrameSubset.vec("C5"), MultinomialAucType.NONE);

      checkMetrics(expected, (ModelMetricsMultinomial) model._output._training_metrics);
      checkMetrics(expected, (ModelMetricsMultinomial) noWeightsModel._output._training_metrics);
    } finally {
      Scope.exit();
      if (model != null) model.delete();
      if (noWeightsModel != null) noWeightsModel.delete();
    }

  }

  @Test
  public void denseMatrixDetectionTest() {
    Frame tfr = null;
    XGBoostModel model = null;
    Scope.enter();
    try {
      tfr = parseTestFile("./smalldata/prostate/prostate.csv");
      DKV.put(tfr);

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      // Automatic detection should compute sparsity and decide
      parms._dmatrix_type = XGBoostModel.XGBoostParameters.DMatrixType.auto;
      parms._response_column = "AGE";
      parms._train = tfr._key;
      parms._ignored_columns = new String[]{"ID","DPROS", "DCAPS", "PSA", "VOL", "RACE", "CAPSULE"};

      // GLEASON used as predictor variable, numeric variable, dense
      model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      assertFalse(model._output._sparse);

    } finally {
      Scope.exit();
      if (tfr!=null) tfr.remove();
      if (model!=null) {
        model.delete();
        model.deleteCrossValidationModels();
      }
    }

  }

  @Test
  public void ProstateRegressionCV() {
    for (XGBoostModel.XGBoostParameters.DMatrixType dMatrixType : XGBoostModel.XGBoostParameters.DMatrixType.values()) {
      Frame tfr = null;
      Frame trainFrame = null;
      Frame testFrame = null;
      Frame preds = null;
      XGBoostModel model = null;
      try {
        // Parse frame into H2O
        tfr = parseTestFile("./smalldata/prostate/prostate.csv");
        FrameMetadata metadataBefore = new FrameMetadata(tfr);

        // split into train/test
        SplitFrame sf = new SplitFrame(tfr, new double[] { 0.7, 0.3 }, null);
        sf.exec().get();
        Key[] ksplits = sf._destination_frames;
        trainFrame = (Frame)ksplits[0].get();
        testFrame = (Frame)ksplits[1].get();

        // define special columns
        String response = "AGE";

        XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
        parms._dmatrix_type = dMatrixType;
        parms._nfolds = 2;
        parms._train = trainFrame._key;
        parms._valid = testFrame._key;
        parms._response_column = response;
        parms._ignored_columns = new String[]{"ID"};

        model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
        LOG.info(model);

        FrameMetadata metadataAfter = new FrameMetadata(tfr);
        assertEquals(metadataBefore, metadataAfter);

        preds = model.score(testFrame);
        assertJavaScoring(model, testFrame, preds);
        assertTrue(preds.anyVec().sigma() > 0);

      } finally {
        if (trainFrame!=null) trainFrame.remove();
        if (testFrame!=null) testFrame.remove();
        if (tfr!=null) tfr.remove();
        if (preds!=null) preds.remove();
        if (model!=null) {
          model.delete();
          model.deleteCrossValidationModels();
        }
      }
    }
  }

  @Test
  public void MNIST() {
    Frame tfr = null;
    Frame preds = null;
    XGBoostModel model = null;
    Scope.enter();
    try {
      // Parse frame into H2O
      tfr = parseTestFile("bigdata/laptop/mnist/train.csv.gz");
      FrameMetadata metadataBefore = new FrameMetadata(tfr);
      Scope.track(tfr.replace(784, tfr.vecs()[784].toCategoricalVec()));   // Convert response 'C785' to categorical
      DKV.put(tfr);

      // define special columns
      String response = "C785";

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._ntrees = 3;
      parms._max_depth = 3;
      parms._train = tfr._key;
      parms._response_column = response;
      parms._seed = 0xCAFEBABE;

      model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      LOG.info(model);

      FrameMetadata metadataAfter = new FrameMetadata(tfr);
      assertEquals(metadataBefore, metadataAfter);

      preds = model.score(tfr);
      assertTrue(model.testJavaScoring(tfr, preds, 1e-6));
      preds.remove(0).remove();
      assertTrue(preds.anyVec().sigma() > 0);
      assertEquals(
              ((ModelMetricsMultinomial)model._output._training_metrics).logloss(),
              ModelMetricsMultinomial.make(preds, tfr.vec(response), tfr.vec(response).domain(), MultinomialAucType.NONE).logloss(),
              1e-5
      );
    } finally {
      if (tfr!=null) tfr.remove();
      if (preds!=null) preds.remove();
      if (model!=null) model.delete();
      Scope.exit();
    }
  }

  @Test
  public void testGPUIncompatParams() {
    XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
    parms._backend = XGBoostModel.XGBoostParameters.Backend.gpu;
    parms._grow_policy = XGBoostModel.XGBoostParameters.GrowPolicy.lossguide;
    Map<String, Object> expectedIncompats = Collections.singletonMap("grow_policy", (Object) XGBoostModel.XGBoostParameters.GrowPolicy.lossguide);
    assertEquals(expectedIncompats, parms.gpuIncompatibleParams());
  }

  @Test
  public void testGPUIncompats() {
    Scope.enter();
    try {
      Frame tfr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB")
              .withVecTypes(Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ard(Double.NaN, 1, 2, 3, 4, 5.6, 7))
              .withDataForCol(1, ar("A", "B,", "A", "C", "A", "B", "A"))
              .build();
      Scope.track(tfr);

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._ntrees = 3;
      parms._max_depth = 3;
      parms._train = tfr._key;
      parms._response_column = "ColB";

      // Force GPU backend
      parms._backend = XGBoostModel.XGBoostParameters.Backend.gpu;

      // Set GPU incompatible parameter 'grow_policy = lossguide'
      parms._grow_policy = XGBoostModel.XGBoostParameters.GrowPolicy.lossguide;
      parms._tree_method = XGBoostModel.XGBoostParameters.TreeMethod.hist; // Needed by lossguide

      try {
        XGBoostModel model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
        Scope.track_generic(model);
        fail("Thes parameter settings are not suppose to work!");
      } catch (H2OModelBuilderIllegalArgumentException e) {
        String expected = "ERRR on field: _backend: GPU backend is not available for parameter setting 'grow_policy = lossguide'. Use CPU backend instead.\n";
        assertTrue(e.getMessage().endsWith(expected));
      }
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void MNIST_LightGBM() {
    Frame tfr = null;
    Frame preds = null;
    XGBoostModel model = null;
    Scope.enter();
    try {
      // Parse frame into H2O
      tfr = parseTestFile("bigdata/laptop/mnist/train.csv.gz");
      FrameMetadata metadataBefore = new FrameMetadata(tfr);
      Scope.track(tfr.replace(784, tfr.vecs()[784].toCategoricalVec()));   // Convert response 'C785' to categorical
      DKV.put(tfr);

      // define special columns
      String response = "C785";

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._ntrees = 3;
      parms._max_depth = 3;
      parms._train = tfr._key;
      parms._response_column = response;
      parms._seed = 0xCAFEBABE;

      // emulate LightGBM
      parms._tree_method = XGBoostModel.XGBoostParameters.TreeMethod.hist;
      parms._grow_policy = XGBoostModel.XGBoostParameters.GrowPolicy.lossguide;

      model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      LOG.info(model);

      FrameMetadata metadataAfter = new FrameMetadata(tfr);
      assertEquals(metadataBefore, metadataAfter);

      preds = model.score(tfr);
      assertTrue(model.testJavaScoring(tfr, preds, 1e-6));
      preds.remove(0).remove();
      assertTrue(preds.anyVec().sigma() > 0);
      assertEquals(
              ((ModelMetricsMultinomial)model._output._training_metrics).logloss(),
              ModelMetricsMultinomial.make(preds, tfr.vec(response), tfr.vec(response).domain(), MultinomialAucType.NONE).logloss(),
              1e-5
      );
    } finally {
      if (tfr!=null) tfr.remove();
      if (preds!=null) preds.remove();
      if (model!=null) model.delete();
      Scope.exit();
    }
  }

  @Ignore
  @Test
  public void testCSC() {
    Frame tfr = null;
    Frame preds = null;
    XGBoostModel model = null;
    Scope.enter();
    try {
      // Parse frame into H2O
      tfr = parseTestFile("csc.csv");
      FrameMetadata metadataBefore = new FrameMetadata(tfr);
      String response = "response";

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._ntrees = 3;
      parms._max_depth = 3;
      parms._train = tfr._key;
      parms._response_column = response;

      model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      LOG.info(model);

      FrameMetadata metadataAfter = new FrameMetadata(tfr);
      assertEquals(metadataBefore, metadataAfter);

      preds = model.score(tfr);
      assertJavaScoring(model, tfr, preds);
      assertTrue(preds.vec(2).sigma() > 0);
      assertEquals(
              ((ModelMetricsBinomial)model._output._training_metrics).logloss(),
              ModelMetricsBinomial.make(preds.vec(2), tfr.vec(response), tfr.vec(response).domain()).logloss(),
              1e-5
      );
    } finally {
      if (tfr!=null) tfr.remove();
      if (preds!=null) preds.remove();
      if (model!=null) model.delete();
      Scope.exit();
    }
  }

  @Test
  public void testModelMetrics() {
      Frame tfr = null, trainFrame = null, testFrame = null, validFrame = null;
      XGBoostModel model = null;
      try {
        // Parse frame into H2O
        tfr = parseTestFile("./smalldata/prostate/prostate.csv");
        FrameMetadata metadataBefore = new FrameMetadata(tfr);

        // split into train/test
        SplitFrame sf = new SplitFrame(tfr, new double[] { 0.6, 0.2, 0.2 }, null);
        sf.exec().get();

        trainFrame = sf._destination_frames[0].get();
        testFrame = sf._destination_frames[1].get();
        validFrame = sf._destination_frames[2].get();
        String response = "AGE";

        XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
        parms._ntrees = 2;
        parms._train = trainFrame._key;
        parms._valid = testFrame._key;
        parms._response_column = response;
        parms._ignored_columns = new String[]{"ID"};

        model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
        assertNotNull("Train metrics are not null", model._output._training_metrics);
        assertNotNull("Validation metrics are not null", model._output._validation_metrics);
        assertEquals("Initial model output metrics contains 2 model metrics",
                            2, model._output.getModelMetrics().length);
        for(String name : model._output._names){
          assertNotEquals(parms._ignored_columns[0], name);
        }

        model.score(testFrame).remove();
        assertEquals("After scoring on test data, model output metrics contains 2 model metrics",
                            2, model._output.getModelMetrics().length);

        model.score(validFrame).remove();
        assertEquals("After scoring on unseen data, model output metrics contains 3 model metrics",
                            3, model._output.getModelMetrics().length);


        FrameMetadata metadataAfter = new FrameMetadata(tfr);
        assertEquals(metadataBefore, metadataAfter);

      } finally {
        if (trainFrame!=null) trainFrame.remove();
        if (testFrame!=null) testFrame.remove();
        if (validFrame!=null) validFrame.remove();
        if (tfr!=null) tfr.remove();
        if (model!=null) {
          model.delete();
        }
      }
  }

  @Test
  public void testCrossValidation() {
    Scope.enter();
    XGBoostModel denseModel = null;
    XGBoostModel sparseModel = null;
    try {
      Frame tfr = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._train = tfr._key;
      parms._response_column = "AGE";
      parms._ignored_columns = new String[]{"ID"};
      parms._seed = 42;
      parms._ntrees = 5;
      parms._weights_column = "CAPSULE";
      parms._dmatrix_type = XGBoostModel.XGBoostParameters.DMatrixType.dense;

      // Dense model utilizes fold column zero values to calculate precise memory requirements
      denseModel = (XGBoostModel) Scope.track_generic(new hex.tree.xgboost.XGBoost(parms).trainModel().get());
      assertNotNull(denseModel);

      parms._dmatrix_type = XGBoostModel.XGBoostParameters.DMatrixType.sparse;
      sparseModel = (XGBoostModel) Scope.track_generic(new hex.tree.xgboost.XGBoost(parms).trainModel().get());
      assertNotNull(sparseModel);


      LOG.info(denseModel);
    } finally {
      if(denseModel != null) denseModel.deleteCrossValidationModels();
      if(sparseModel != null) sparseModel.deleteCrossValidationModels();
      Scope.exit();
    }
  }

  @Test
  public void testSparsityDetection(){
    Scope.enter();
    XGBoostModel sparseModel = null;
    XGBoostModel denseModel = null;
    try {

      // Fill ratio 0.2 (response not counted)
      Frame sparseFrame = Scope.track(new TestFrameBuilder()
              .withName("sparse_frame")
              .withColNames("C1", "C2", "C3")
              .withVecTypes(Vec.T_NUM,Vec.T_NUM,Vec.T_NUM)
              .withDataForCol(0, ard( 1,0, 0, 0, 0))
              .withDataForCol(1, ard( 0, 1, 0, 0, 0))
              .withDataForCol(2, ard( 2, 1,1, 4, 3))
              .build());


      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._train = sparseFrame._key;
      parms._response_column = "C3";
      parms._seed = 42;
      parms._backend = XGBoostModel.XGBoostParameters.Backend.cpu;
      parms._ntrees = 1;
      parms._dmatrix_type = XGBoostModel.XGBoostParameters.DMatrixType.auto;

      sparseModel = (XGBoostModel) Scope.track_generic(new hex.tree.xgboost.XGBoost(parms).trainModel().get());
      assertNotNull(sparseModel);
      assertTrue(sparseModel._output._sparse);

      // Fill ratio 0.3 (response not counted) - the threshold for sprase is >= 0.3
      Frame denseFrame = Scope.track(new TestFrameBuilder()
              .withName("sparse_frame")
              .withColNames("C1", "C2", "C3")
              .withVecTypes(Vec.T_NUM,Vec.T_NUM,Vec.T_NUM)
              .withDataForCol(0, ard( 1,0, 0, 0, 0))
              .withDataForCol(1, ard( 1, 1, 0, 0, 0))
              .withDataForCol(2, ard( 2, 1,1, 4, 3))
              .build());

      parms._train = denseFrame._key;
      parms._response_column = "C3";
      parms._seed = 42;
      parms._backend = XGBoostModel.XGBoostParameters.Backend.cpu;
      parms._ntrees = 1;
      parms._dmatrix_type = XGBoostModel.XGBoostParameters.DMatrixType.auto;

      // Dense model utilizes fold column zero values to calculate precise memory requirements
      denseModel = (XGBoostModel) Scope.track_generic(new hex.tree.xgboost.XGBoost(parms).trainModel().get());
      assertNotNull(denseModel);
      assertFalse(denseModel._output._sparse);

      LOG.info(sparseModel);
    } finally {
      if(sparseModel != null) sparseModel.deleteCrossValidationModels();
      if(denseModel != null) denseModel.deleteCrossValidationModels();
      Scope.exit();
    }
  }

  @Test
  public void testMojoBoosterDump() throws IOException { 
    Scope.enter();
    try {
      Frame tfr = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));

      Scope.track(tfr.replace(1, tfr.vecs()[1].toCategoricalVec()));   // Convert CAPSULE to categorical
      Scope.track(tfr.replace(3, tfr.vecs()[3].toCategoricalVec()));   // Convert RACE to categorical
      DKV.put(tfr);

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._train = tfr._key;
      parms._response_column = "AGE";
      parms._ignored_columns = new String[]{"ID"};
      parms._seed = 42;
      parms._ntrees = 7;

      XGBoostModel model = (XGBoostModel) Scope.track_generic(new hex.tree.xgboost.XGBoost(parms).trainModel().get());
      LOG.info(model);

      String[] dump = BoosterDump.getBoosterDump(model.model_info()._boosterBytes, model.model_info()._featureMap, false, "text");
      assertEquals(parms._ntrees, dump.length);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testMojoSerializable() throws IOException {
    Scope.enter();
    try {
      Frame tfr = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._train = tfr._key;
      parms._response_column = "AGE";
      parms._ignored_columns = new String[]{"ID"};
      parms._seed = 42;
      parms._ntrees = 7;

      XGBoostModel model = (XGBoostModel) Scope.track_generic(new hex.tree.xgboost.XGBoost(parms).trainModel().get());

      XGBoostMojoModel mojo = getMojo(model);

      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      try (ObjectOutput out = new ObjectOutputStream(bos)) {
        out.writeObject(mojo);
      }
      assertNotNull(bos.toByteArray());
    } finally {
      Scope.exit();
    }
  }

  /**
   * PUBDEV-5816: Tests correctness of training metrics returned for Multinomial XGBoost models
   */
  @Test
  public void testPubDev5816() {
    Scope.enter();
    try {
      final String response = "cylinders";

      Frame f = parseTestFile("./smalldata/junit/cars.csv");
      f.replace(f.find(response), f.vecs()[f.find(response)].toCategoricalVec()).remove();
      DKV.put(Scope.track(f));

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._dmatrix_type = XGBoostModel.XGBoostParameters.DMatrixType.auto;
      parms._response_column = response;
      parms._train = f._key;
      parms._ignored_columns = new String[]{"name"};

      XGBoostModel model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      Scope.track_generic(model);

      ModelMetricsMultinomial modelMetricsMultinomial = (ModelMetricsMultinomial) model._output._training_metrics;

      Frame predicted = Scope.track(model.score(f));
      predicted.remove(0);
      ModelMetricsMultinomial expected = (ModelMetricsMultinomial.make(predicted, f.vec(response), f.vec(response).domain(), MultinomialAucType.NONE));
      Scope.track_generic(expected);

      checkMetrics(expected, modelMetricsMultinomial);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testMonotoneConstraints() {
    Scope.enter();
    try {
      final String response = "power (hp)";
      Frame f = parseTestFile("./smalldata/junit/cars.csv");
      f.replace(f.find(response), f.vecs()[f.find("cylinders")].toNumericVec()).remove();
      DKV.put(Scope.track(f));

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._dmatrix_type = XGBoostModel.XGBoostParameters.DMatrixType.auto;
      parms._response_column = response;
      parms._train = f._key;
      parms._ignored_columns = new String[] { "name" };
      parms._seed = 42;
      parms._reg_lambda = 0;
      parms._tree_method = XGBoostModel.XGBoostParameters.TreeMethod.hist;

      XGBoostModel.XGBoostParameters noConstrParams = (XGBoostModel.XGBoostParameters) parms.clone();
      XGBoostModel noConstrModel = new hex.tree.xgboost.XGBoost(noConstrParams).trainModel().get();
      Scope.track_generic(noConstrModel);

      assertTrue(ArrayUtils.contains(noConstrModel._output._varimp._names, "cylinders"));

      XGBoostModel.XGBoostParameters constrParams = (XGBoostModel.XGBoostParameters) parms.clone();
      constrParams._monotone_constraints = new KeyValue[] { new KeyValue("cylinders", -1) };
      XGBoostModel constrModel = new hex.tree.xgboost.XGBoost(constrParams).trainModel().get();
      Scope.track_generic(constrModel);

      // we essentially eliminated the effect of the feature by setting an inverted constraint
      assertFalse(ArrayUtils.contains(constrModel._output._varimp._names, "cylinders"));
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testValidateMonotoneConstraints() {
    Scope.enter();
    try {
      final String response = "power (hp)";

      Frame f = parseTestFile("smalldata/junit/cars.csv");
      Scope.track(f);

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._dmatrix_type = XGBoostModel.XGBoostParameters.DMatrixType.auto;
      parms._response_column = response;
      parms._train = f._key;
      parms._seed = 42;

      thrown.expect(H2OModelBuilderIllegalArgumentException.class);
      thrown.expectMessage(CoreMatchers.containsString(
              "Details: ERRR on field: _monotone_constraints: Invalid constraint - column 'name' has type Enum. Only numeric columns can have monotonic constraints."));
      Model m = trainWithConstraints(parms, new KeyValue("name", -1));
      assertNotNull(m); // shouldn't be reached
      assertFalse(true);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testMakeDataInfo() {
    Scope.enter();
    try {
      Frame f = parseTestFile("smalldata/junit/cars.csv");
      Scope.track(f);

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._response_column = "year";
      parms._train = f._key;

      DataInfo dinfo = hex.tree.xgboost.XGBoost.makeDataInfo(f, null, parms);
      assertNotNull(dinfo._coefNames);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testScoreContributions() throws IOException, XGBoostError {
    Scope.enter();
    try {
      String response = "RainTomorrow";
      Frame df = loadWeather(response);
      assertEquals(1, df.anyVec().nChunks()); // tiny file => should always fit in a single chunk

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._ntrees = 5;
      parms._max_depth = 5;
      parms._train = df._key;
      parms._response_column = response;
      parms._save_matrix_directory = tmp.newFolder("matrix_dump").getAbsolutePath();

      XGBoostModel model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      Scope.track_generic(model);
      LOG.info(model);

      Frame contributions = Scope.track(scoreContributionsChecked(model, df, true));

      assertEquals("BiasTerm", contributions.names()[contributions.names().length - 1]);
      
      // basic sanity check - contributions should sum-up to predictions
      Frame predsFromContributions = new CalcContribsTask().doAll(Vec.T_NUM, contributions).outputFrame();
      Frame expectedPreds = Scope.track(model.score(df));
      assertVecEquals(expectedPreds.vec(2), predsFromContributions.vec(0), 1e-6);

      // make the predictions with XGBoost
      Map<String, String> rabitEnv = new HashMap<>();
      rabitEnv.put("DMLC_TASK_ID", "0");
      Rabit.init(rabitEnv);
      Booster booster = BoosterHelper.loadModel(model.model_info()._boosterBytes);
      File trainMatrixFile = new File(parms._save_matrix_directory, "train_matrix.part0");
      assertTrue(trainMatrixFile.exists());
      DMatrix matrix = new DMatrix(trainMatrixFile.getAbsolutePath());
      final float[][] expectedContribs = booster.predictContrib(matrix, 0);
      booster.dispose();
      matrix.dispose();
      Rabit.shutdown();
      
      // finally check the contributions
      assertEquals(expectedContribs.length, contributions.numRows());
      new CheckContribsTask(expectedContribs).doAll(contributions).outputFrame();
    } finally {
      Scope.exit();
    }
  }

  private static class CalcContribsTask extends MRTask<CalcContribsTask> {
    @Override
    public void map(Chunk[] cs, NewChunk nc) {
      for (int i = 0; i < cs[0]._len; i++) {
        float sum = 0;
        for (Chunk c : cs)
          sum += c.atd(i);
        nc.addNum(sigmoid(sum));
      }
    }
    private float sigmoid(float x) {
      return (1f / (1f + (float) Math.exp(-x)));
    }
  }
  
  private static class CheckContribsTask extends MRTask<CheckContribsTask> {
    private final float[][] _expectedContribs;

    private CheckContribsTask(float[][] expectedContribs) {
      _expectedContribs = expectedContribs;
    }

    @Override
    public void map(Chunk[] cs) {
      for (int i = 0; i < cs[0]._len; i++) {
        int row = (int) cs[0].start() + i;
        for (int j = 0; j < cs.length - 1; j++) {
          float contrib = (float) cs[j].atd(i);
          assertEquals("Contribution in row=" + row + " on position=" + j + " should match.",
                  _expectedContribs[row][j], contrib, 1e-6);
        }
        float biasTerm = (float) cs[cs.length - 1].atd(i);
        float expectedBiasTerm = _expectedContribs[row][cs.length - 1];
        assertEquals("BiasTerm in row=" + row + " should match.",  expectedBiasTerm, biasTerm, 1e-6);
      }
    }
  }

  @Test
  public void testUpdateAuxTreeWeights_gaussian() {
    Scope.enter();
    try {
      String response = "AGE";
      Frame train = Scope.track(parseTestFile("smalldata/prostate/prostate.csv", new int[]{0}));

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._train = train._key;
      parms._ntrees = 10;
      parms._response_column = response;
      parms._distribution = gaussian;

      XGBoostModel xgb = (XGBoostModel) Scope.track_generic(new hex.tree.xgboost.XGBoost(parms).trainModel().get());
      
      Frame scored = Scope.track(xgb.score(train));
      assertTrue(xgb.testJavaScoring(train, scored, 1e-6));

      checkUpdateAuxTreeWeights(xgb, train);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testUpdateAuxTreeWeights_bernoulli() {
    Scope.enter();
    try {
      String response = "CAPSULE";
      Frame train = Scope.track(parseTestFile("smalldata/prostate/prostate.csv", new int[]{0}));
      train.toCategoricalCol(response);

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._train = train._key;
      parms._ntrees = 10;
      parms._response_column = response;
      parms._distribution = bernoulli;

      XGBoostModel xgb = (XGBoostModel) Scope.track_generic(new hex.tree.xgboost.XGBoost(parms).trainModel().get());

      Frame scored = Scope.track(xgb.score(train));
      assertTrue(xgb.testJavaScoring(train, scored, 1e-6));

      checkUpdateAuxTreeWeights(xgb, train);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testUpdateAuxTreeWeights_hist_bernoulli() {
    Scope.enter();
    try {
      String response = "Angaus";
      Frame train = Scope.track(parseTestFile("smalldata/gbm_test/ecology_model.csv", new int[]{0}));
      train.toCategoricalCol(response);

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._train = train._key;
      parms._ntrees = 10;
      parms._response_column = response;
      parms._distribution = bernoulli;
      parms._tree_method = XGBoostModel.XGBoostParameters.TreeMethod.hist;

      XGBoostModel xgb = (XGBoostModel) Scope.track_generic(new hex.tree.xgboost.XGBoost(parms).trainModel().get());

      Frame scored = Scope.track(xgb.score(train));
      assertTrue(xgb.testJavaScoring(train, scored, 1e-6));

      checkUpdateAuxTreeWeights(xgb, train);
    } finally {
      Scope.exit();
    }
  }

  private void checkUpdateAuxTreeWeights(XGBoostModel xgb, Frame frame) {
    frame = ensureDistributed(frame);
    
    Predictor orgPredictor = xgb.makePredictor(false);
    RegTree[] orgTrees = ((GBTree) orgPredictor.getBooster()).getGroupedTrees()[0];

    double weightCoef = 2;

    Frame fr = new Frame(frame);
    fr.add("weights", fr.anyVec().makeCon(weightCoef));
    Scope.track(fr);
    xgb.updateAuxTreeWeights(fr, "weights");

    AuxNodeWeights auxNodeWeights = xgb.model_info().auxNodeWeights();
    assertNotNull(auxNodeWeights);

    Predictor updPredictor = xgb.makePredictor(false);
    RegTree[] updTrees = ((GBTree) updPredictor.getBooster()).getGroupedTrees()[0];

    for (int i = 0; i < orgTrees.length; i++) {
      RegTreeNodeStat[] expectedStats = orgTrees[i].getStats();
      RegTreeNodeStat[] actualStats = updTrees[i].getStats();
      for (int j = 0; j < expectedStats.length; j++) {
        assertEquals(expectedStats[j].getWeight() * weightCoef, actualStats[j].getWeight(), 1e-4);
      }
    }
  }

  @Test
  public void testScoringWithUnseenCategoricals() {
    try {
      Scope.enter();
      final Frame training = new TestFrameBuilder()
              .withName("trainFrame")
              .withColNames("y", "x1", "x2")
              .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ard(0, 0, 2, 3, 4, 5.6, 7))
              .withDataForCol(1, ar("A", "B", "C", "E", "F", "I", "J"))
              .withDataForCol(2, ar("A", "B,", "A", "C", "A", "B", "A"))
              .withChunkLayout(2, 2, 2, 1)
              .build();
      Scope.track(training);

      final Frame test = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("y", "x1", "x2")
              .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ard(0, 0, 2, 3, 4, 5.6, 7))
              .withDataForCol(1, ar("X", "Y", "U", "W", "W", "Z", "Q"))
              .withDataForCol(2, ar("X", "Y,", "U", "W", "W", "Z", "Q"))
              .withChunkLayout(2, 2, 2, 1)
              .build();
      Scope.track(test);
      
      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._dmatrix_type = XGBoostModel.XGBoostParameters.DMatrixType.sparse;
      parms._response_column = "y";
      parms._train = training._key;
      parms._seed = 42;
      parms._ntrees = 5;

      XGBoostModel model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      Scope.track_generic(model);

      Frame preds = model.score(test);
      Scope.track(preds);
      assertEquals(test.numRows(), preds.numRows());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testScoreContributionsBernoulli() throws IOException, PredictException {
    try {
      Scope.enter();
      Frame fr = Scope.track(parseTestFile("smalldata/junit/titanic_alt.csv"));
      fr.replace(fr.find("survived"), fr.vec("survived").toCategoricalVec());
      DKV.put(fr);
      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._train = fr._key;
      parms._distribution = bernoulli;
      parms._response_column = "survived";
      parms._ntrees = 5;
      parms._max_depth = 4;
      parms._min_rows = 1;
      parms._learn_rate = .2f;
      parms._score_each_iteration = true;
      parms._seed = 42;

      hex.tree.xgboost.XGBoost job = new hex.tree.xgboost.XGBoost(parms);
      XGBoostModel model = job.trainModel().get();
      Scope.track_generic(model);

      Frame contributionsExpanded = model.scoreContributions(fr, Key.make("contributions_titanic_exp"));
      Scope.track(contributionsExpanded);

      Frame contributionsAggregated = model.scoreContributions(fr, Key.make("contributions_titanic"), null,
              new ContributionsOptions().setOutputFormat(ContributionsOutputFormat.Compact));
      Scope.track(contributionsAggregated);

      CheckExpandedContributionsMatchAggregatedContributions.assertEquals(model, fr, contributionsAggregated, contributionsExpanded);

      MojoModel mojo = model.toMojo();

      EasyPredictModelWrapper.Config cfg = new EasyPredictModelWrapper.Config()
              .setModel(mojo)
              .setEnableContributions(true);
      EasyPredictModelWrapper wrapper = new EasyPredictModelWrapper(cfg);
      assertArrayEquals(contributionsExpanded.names(), wrapper.getContributionNames());

      for (long row = 0; row < fr.numRows(); row++) {
        RowData rd = toRowData(fr, model._output._names, row);
        BinomialModelPrediction pr = wrapper.predictBinomial(rd);
        assertArrayEquals("Contributions should match, row=" + row, 
                toNumericRow(contributionsExpanded, row), ArrayUtils.toDouble(pr.contributions), 0);
      }
    } finally {
      Scope.exit();
    }
  }

  private static Frame scoreContributionsChecked(XGBoostModel model, Frame fr, boolean outputExpanded) {
    try {
      Scope.enter();
      Frame contribs = model.scoreContributions(fr, Key.make("contribs"), null,
              new ContributionsOptions().setOutputFormat(ContributionsOutputFormat.Compact));
      Scope.track(contribs);
      Frame contribsExpanded = model.scoreContributions(fr, Key.make("contribsExpanded"));
      Scope.track(contribsExpanded);
      CheckExpandedContributionsMatchAggregatedContributions.assertEquals(model, fr, contribs, contribsExpanded);
      Frame result = outputExpanded ? contribsExpanded : contribs;
      Scope.untrack(result);
      return result;
    } finally {
      Scope.exit();
    }
  }
  
  private static class CheckExpandedContributionsMatchAggregatedContributions 
          extends MRTask<CheckExpandedContributionsMatchAggregatedContributions> {
    int _frCols;
    DataInfo _di;

    CheckExpandedContributionsMatchAggregatedContributions(int frCols, DataInfo di) {
      _frCols = frCols;
      _di = di;
    }

    void map(Chunk[] fr, Chunk[] contribs, Chunk[] contribsExpanded) {
      int numPrecedingCats = 0;
      for (int c = 0; c < fr.length; c++) {
        Vec v = fr[c].vec();
        if (v.isCategorical()) {
          numPrecedingCats++;
          for (int i = 0; i < fr[c]._len; i++) {
            float sum = 0;
            for (int j = _di._catOffsets[c]; j < _di._catOffsets[c + 1]; j++) {
              sum += contribsExpanded[j].atd(i);
            }
            Assert.assertEquals(sum, contribs[c].atd(i), 0);
          }
        } else {
          assert v.isNumeric() || v.isTime();
          int colOffset = _di.numStart() - numPrecedingCats;
          for (int i = 0; i < fr[c]._len; i++) {
            Assert.assertEquals("Contribution not matching in col=" + _fr.name(c) + ", row=" + (fr[0].start() + i), 
                    contribsExpanded[colOffset + c].atd(i), contribs[c].atd(i), 0);
          }
        }
      }
    }

    @Override
    public void map(Chunk[] cs) {
      Chunk[] fr = Arrays.copyOf(cs, _frCols);
      Chunk[] contribs = Arrays.copyOfRange(cs, _frCols, _frCols * 2 + 1);
      Chunk[] contribsExpanded = Arrays.copyOfRange(cs, _frCols * 2 + 1, cs.length);
      map(fr, contribs, contribsExpanded);
    }

    public static void assertEquals(XGBoostModel model, Frame fr, Frame contributionsAggregated, Frame contributionsExpanded) {
      Frame checkContribsFr = new Frame(model._output.features(), fr.vecs(model._output.features()));
      Assert.assertEquals(checkContribsFr.numCols() + 1, contributionsAggregated.numCols());
      checkContribsFr.add(contributionsAggregated);
      checkContribsFr.add(contributionsExpanded);

      new CheckExpandedContributionsMatchAggregatedContributions(contributionsAggregated.numCols() - 1, model.model_info().dataInfo())
              .doAll(checkContribsFr);
    }

  }
  
  // Scoring should output original probabilities and probabilities calibrated by Platt Scaling
  @Test public void testPredictWithCalibration() {
    Scope.enter();
    try {
      Frame train = parseTestFile("smalldata/gbm_test/ecology_model.csv");
      Frame calib = parseTestFile("smalldata/gbm_test/ecology_eval.csv");

      // Fix training set
      train.remove("Site").remove();     // Remove unique ID
      Scope.track(train.vec("Angaus"));
      train.replace(train.find("Angaus"), train.vecs()[train.find("Angaus")].toCategoricalVec());
      Scope.track(train);
      DKV.put(train); // Update frame after hacking it

      // Fix calibration set (the same way as training)
      Scope.track(calib.vec("Angaus"));
      calib.replace(calib.find("Angaus"), calib.vecs()[calib.find("Angaus")].toCategoricalVec());
      Scope.track(calib);
      DKV.put(calib); // Update frame after hacking it
      
      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._train = train._key;
      parms._calibrate_model = true;
      parms._calibration_frame = calib._key;
      parms._response_column = "Angaus"; // Train on the outcome
      parms._distribution = multinomial;
      parms._ntrees = 5;
      parms._min_rows = 1;
      parms._seed = 42;
      
      hex.tree.xgboost.XGBoost job = new hex.tree.xgboost.XGBoost(parms);
      XGBoostModel model = job.trainModel().get();
      Scope.track_generic(model);

      Frame pred = parseTestFile("smalldata/gbm_test/ecology_eval.csv");
      pred.remove("Angaus").remove();    // No response column during scoring
      Scope.track(pred);
      Frame res = Scope.track(model.score(pred));

      assertArrayEquals(new String[]{"predict", "p0", "p1", "cal_p0", "cal_p1"}, res._names);
      assertEquals(res.vec("cal_p0").mean(), 0.7860, 1e-4);
      assertEquals(res.vec("cal_p1").mean(), 0.2140, 1e-4);
    } finally {
      Scope.exit();
    }
  }

  private static XGBoostModel trainWithConstraints(XGBoostModel.XGBoostParameters p, KeyValue... constraints) {
    XGBoostModel.XGBoostParameters parms = (XGBoostModel.XGBoostParameters) p.clone();
    parms._monotone_constraints = constraints;
    XGBoostModel model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
    Scope.track_generic(model);
    return model;
  }

  private static XGBoostMojoModel getMojo(XGBoostModel model) throws IOException {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    model.getMojo().writeTo(os);
    os.close();
    MojoReaderBackend mojoReaderBackend = MojoReaderBackendFactory.createReaderBackend(
            new ByteArrayInputStream(os.toByteArray()), MojoReaderBackendFactory.CachingStrategy.MEMORY);
    return (XGBoostMojoModel) MojoModel.load(mojoReaderBackend);
  }
  
  private static <T extends ModelMetricsSupervised> void checkMetrics(final T expectedMM, final T actualMM) {
    double precision = 1e-8;
    boolean doCheckCM = true;
    if (H2O.getCloudSize() >= 2) {
      precision = 5e-2; // results are non-deterministic
      doCheckCM = false; // CM can be about 5% different values
    }
    assertEquals(expectedMM.rmse(), actualMM.rmse(), precision);
    assertEquals(expectedMM._sigma, actualMM._sigma, precision);
    assertEquals(expectedMM._nobs, actualMM._nobs, precision);
    if (expectedMM instanceof ModelMetricsBinomial) {
      final ModelMetricsBinomial mmbExp = (ModelMetricsBinomial) expectedMM;
      final ModelMetricsBinomial mmbAct = (ModelMetricsBinomial) actualMM;
      assertEquals(mmbExp.logloss(), mmbExp.logloss(), precision);
      assertEquals(mmbExp.auc(), mmbAct.auc(), precision);
      assertEquals(mmbExp.mean_per_class_error(), mmbAct.mean_per_class_error(), precision);
      if (doCheckCM) {
        checkConfusionMatrix(mmbExp.cm(), mmbAct.cm());
      }
    } else if (expectedMM instanceof ModelMetricsMultinomial) {
      final ModelMetricsMultinomial mmmExp = (ModelMetricsMultinomial) expectedMM;
      final ModelMetricsMultinomial mmmAct = (ModelMetricsMultinomial) actualMM;
      assertEquals(mmmExp.logloss(), mmmAct.logloss(), precision);
      assertEquals(mmmExp.mean_per_class_error(), mmmAct.mean_per_class_error(), precision);
      if (doCheckCM) {
        checkConfusionMatrix(mmmExp.cm(), mmmAct.cm());
      }
    }
    assertArrayEquals(expectedMM.hr(), actualMM.hr(), (float) precision);
    assertArrayEquals(expectedMM._domain, actualMM._domain);
  }

  private static void checkConfusionMatrix(final ConfusionMatrix expectedCM, final ConfusionMatrix actualCM) {
    assertTrue("Expected: " + Arrays.deepToString(expectedCM._cm) + ", Got: " + Arrays.deepToString(actualCM._cm),
            Arrays.deepEquals(actualCM._cm, expectedCM._cm));
  }

  @Test
  public void testIsFeatureUsedInPredict() {
    isFeatureUsedInPredictHelper(false, false);
    isFeatureUsedInPredictHelper(true, false);
    isFeatureUsedInPredictHelper(false, true);
    isFeatureUsedInPredictHelper(true, true);
  }

  private void isFeatureUsedInPredictHelper(boolean ignoreConstCols, boolean multinomial) {
    Scope.enter();
    Vec target = Vec.makeRepSeq(100, 3);
    if (multinomial) target = target.toCategoricalVec();
    Vec zeros = Vec.makeCon(0d, 100);
    Vec nonzeros = Vec.makeCon(1e10d, 100);
    Frame dummyFrame = new Frame(
            new String[]{"a", "b", "c", "d", "e", "target"},
            new Vec[]{zeros, zeros, zeros, zeros, target, target}
    );
    dummyFrame._key = Key.make("DummyFrame_testIsFeatureUsedInPredict");

    Frame otherFrame = new Frame(
            new String[]{"a", "b", "c", "d", "e", "target"},
            new Vec[]{nonzeros, nonzeros, nonzeros, nonzeros, target, target}
    );

    Frame reference = null;
    Frame prediction = null;
    XGBoostModel model = null;
    try {
      DKV.put(dummyFrame);
      hex.tree.xgboost.XGBoostModel.XGBoostParameters xgb = new  hex.tree.xgboost.XGBoostModel.XGBoostParameters();
      xgb._train = dummyFrame._key;
      xgb._response_column = "target";
      xgb._seed = 1;
      xgb._ignore_const_cols = ignoreConstCols;

      hex.tree.xgboost.XGBoost job = new hex.tree.xgboost.XGBoost(xgb);
      model = job.trainModel().get();

      String lastUsedFeature = "";
      int usedFeatures = 0;
      for(String feature : model._output._names) {
        if (model.isFeatureUsedInPredict(feature)) {
          usedFeatures ++;
          lastUsedFeature = feature;
        }
      }
      assertEquals(1, usedFeatures);
      assertEquals("e", lastUsedFeature);

      reference = model.score(dummyFrame);
      prediction = model.score(otherFrame);
      for (int i = 0; i < reference.numRows(); i++) {
        assertEquals(reference.vec(0).at(i), prediction.vec(0).at(i), 1e-10);
      }
    } finally {
      dummyFrame.delete();
      if (model != null) model.delete();
      if (reference != null) reference.delete();
      if (prediction != null) prediction.delete();
      target.remove();
      zeros.remove();
      nonzeros.remove();
      Scope.exit();
    }
  }

  @Test
  public void testNAPredictor_cat() {
    Assume.assumeTrue(H2O.getCloudSize() == 1); // using `tree_method`=exact is not supported in multi-node
    checkNAPredictor(new TestFrameBuilder()
            .withVecTypes(Vec.T_CAT, Vec.T_CAT)
            .withDataForCol(0, ar(null, "V", null, "V", null, "V"))
    );
  }

  @Test
  public void testNAPredictor_num() {
    Assume.assumeTrue(H2O.getCloudSize() == 1); // using `tree_method`=exact is not supported in multi-node
    checkNAPredictor(new TestFrameBuilder()
            .withVecTypes(Vec.T_NUM, Vec.T_CAT)
            .withDataForCol(0, ard(Double.NaN, 1, Double.NaN, 1, Double.NaN, 1))
    );
  }

  private void checkNAPredictor(TestFrameBuilder fb) {
    Scope.enter();
    try {
      final Frame frame = fb
              .withColNames("F", "Response")
              .withDataForCol(1, ar("A", "B", "A", "B", "A", "B"))
              .build();

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._train = frame._key;
      parms._response_column = "Response";
      parms._ntrees = 1;
      parms._learn_rate = 1;
      parms._ignore_const_cols = true; // default but to make sure and illustrate the point
      parms._min_rows = 0;
      parms._tree_method = XGBoostModel.XGBoostParameters.TreeMethod.exact;

      XGBoostModel model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      Scope.track_generic(model);

      // We should have a perfect model
      assertEquals(0, model.classification_error(), 0);

      // Check that we predict perfectly
      Frame test = Scope.track(frame.subframe(new String[]{"F"}));
      Frame scored = Scope.track(model.score(test));
      assertCatVecEquals(frame.vec("Response"), scored.vec("predict"));

      // Tree should split on NAs
      SharedTreeSubgraph tree0 = model.convert(0, "A").subgraphArray.get(0);
      assertEquals(3, tree0.nodesArray.size()); // this implies depth 1
    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void testIdentityModel() throws XGBoostError, IOException {
    try {
      Map<String, String> rabitEnv = new HashMap<>();
      rabitEnv.put("DMLC_TASK_ID", "0");
      Rabit.init(rabitEnv);
      // trivial identity matrix
      DMatrix trainMat = new DMatrix(new float[]{0, 1}, 2, 1);
      trainMat.setLabel(new float[]{0, 1});

      HashMap<String, Object> params = new HashMap<>();
      params.put("eta", 1.0);
      params.put("max_depth", 5);
      params.put("silent", 0);
      params.put("objective", "binary:logistic");
      params.put("min_child_weight", 0);           // Test won't with min_child_weight == 1 (dunno why)

      HashMap<String, DMatrix> watches = new HashMap<>();
      watches.put("train", trainMat);

      Booster booster = XGBoost.train(trainMat, params, 1, watches, null, null);
      float[][] preds = booster.predict(trainMat);
      assertTrue(preds[0][0] < 0.5);
      assertTrue(preds[1][0] > 0.5);
    } finally {
      Rabit.shutdown();
    }
  }

  @Test
  public void testXGBoostMaximumDepth() {
    Scope.enter();
    try {
      Frame tfr = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._train = tfr._key;
      parms._response_column = "AGE";
      parms._ignored_columns = new String[]{"ID"};
      parms._seed = 0xDECAF;
      parms._max_depth = 0;

      XGBoostModel model = (XGBoostModel) Scope.track_generic(new hex.tree.xgboost.XGBoost(parms).trainModel().get());
      Scope.track_generic(model);
      assertEquals(Integer.MAX_VALUE, model._parms._max_depth);

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testXGBoostFeatureInteractions() {
    Scope.enter();
    try {
      Frame tfr = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._train = tfr._key;
      parms._response_column = "AGE";
      parms._ignored_columns = new String[]{"ID"};
      parms._seed = 0xDECAF;
      parms._build_tree_one_node = true;
      parms._tree_method = XGBoostModel.XGBoostParameters.TreeMethod.exact;

      XGBoostModel model = (XGBoostModel) Scope.track_generic(new hex.tree.xgboost.XGBoost(parms).trainModel().get());
      FeatureInteractions featureInteractionMap = model.getFeatureInteractions(2,100,-1);

      assertEquals(featureInteractionMap.size(), 85);
      
      double epsilon = 1e-4;
      // check some interactions of depth 0:
      FeatureInteraction capsuleInteraction = featureInteractionMap.get("CAPSULE");
      assertEquals(capsuleInteraction.gain, 768.988530628086, epsilon);
      assertEquals(capsuleInteraction.fScore, 82.0, epsilon);
      assertEquals(capsuleInteraction.fScoreWeighted, 5.21315789473684, epsilon);
      assertEquals(capsuleInteraction.averageFScoreWeighted, 0.0635750962772786, epsilon);
      assertEquals(capsuleInteraction.averageGain, 9.37790891009861, epsilon);
      assertEquals(capsuleInteraction.expectedGain, 43.0394271689104, epsilon);
      assertEquals(capsuleInteraction.averageTreeDepth, 4.2, 1e-1);
      assertEquals(capsuleInteraction.averageTreeIndex, 25.78, 1e-2);

      FeatureInteraction psaInteraction = featureInteractionMap.get("PSA");
      assertEquals(psaInteraction.gain, 11264.2880798631, epsilon);
      assertEquals(psaInteraction.fScore, 572.0, epsilon);
      assertEquals(psaInteraction.fScoreWeighted, 148.452631578947, epsilon);
      assertEquals(psaInteraction.averageFScoreWeighted, 0.259532572690467, epsilon);
      assertEquals(psaInteraction.averageGain, 19.692811328432, epsilon);
      assertEquals(psaInteraction.expectedGain, 2323.51525060787, epsilon);
      assertEquals(psaInteraction.averageTreeDepth, 3.55, 1e-2);
      assertEquals(psaInteraction.averageTreeIndex, 27.29, 1e-2);

      // check some interactions of depth 1:
      FeatureInteraction psaPsaInteraction = featureInteractionMap.get("PSA|PSA");
      assertEquals(psaPsaInteraction.gain, 13584.7678359787, epsilon);
      assertEquals(psaPsaInteraction.fScore, 326.0, epsilon);
      assertEquals(psaPsaInteraction.fScoreWeighted, 101.189473684211, epsilon);
      assertEquals(psaPsaInteraction.averageFScoreWeighted, 0.310397158540523, epsilon);
      assertEquals(psaPsaInteraction.averageGain, 41.6710669815298, epsilon);
      assertEquals(psaPsaInteraction.expectedGain, 2906.05613890999, epsilon);
      assertEquals(psaPsaInteraction.averageTreeDepth, 3.66, 1e-2);
      assertEquals(psaPsaInteraction.averageTreeIndex, 27.78, 1e-2);

      FeatureInteraction gleasonRaceInteraction = featureInteractionMap.get("GLEASON|RACE");
      assertEquals(gleasonRaceInteraction.gain, 2028.03059237, epsilon);
      assertEquals(gleasonRaceInteraction.fScore, 14.0, epsilon);
      assertEquals(gleasonRaceInteraction.fScoreWeighted, 4.61052631578947, epsilon);
      assertEquals(gleasonRaceInteraction.averageFScoreWeighted, 0.329323308270677, epsilon);
      assertEquals(gleasonRaceInteraction.averageGain, 144.859328026429, epsilon);
      assertEquals(gleasonRaceInteraction.expectedGain, 815.892524956053, epsilon);
      assertEquals(gleasonRaceInteraction.averageTreeDepth, 3.14, 1e-2);
      assertEquals(gleasonRaceInteraction.averageTreeIndex, 14.5, 1e-1);

      // check some interactions of depth 2:
      FeatureInteraction volVolVolInteraction = featureInteractionMap.get("VOL|VOL|VOL");
      assertEquals(volVolVolInteraction.gain, 3197.648769331, epsilon);
      assertEquals(volVolVolInteraction.fScore, 37.0, epsilon);
      assertEquals(volVolVolInteraction.fScoreWeighted, 12.5947368421053, epsilon);
      assertEquals(volVolVolInteraction.averageFScoreWeighted, 0.340398293029872, epsilon);
      assertEquals(volVolVolInteraction.averageGain, 86.4229397116487, epsilon);
      assertEquals(volVolVolInteraction.expectedGain, 740.075555944026, epsilon);
      assertEquals(volVolVolInteraction.averageTreeDepth, 3.7, 1e-1);
      assertEquals(volVolVolInteraction.averageTreeIndex, 22.03, 1e-2);

      FeatureInteraction capsuleDprosGleasonInteraction = featureInteractionMap.get("CAPSULE|DPROS|GLEASON");
      assertEquals(capsuleDprosGleasonInteraction.gain, 227.14370899, epsilon);
      assertEquals(capsuleDprosGleasonInteraction.fScore, 4.0, epsilon);
      assertEquals(capsuleDprosGleasonInteraction.fScoreWeighted, 0.347368421052632, epsilon);
      assertEquals(capsuleDprosGleasonInteraction.averageFScoreWeighted, 0.0868421052631579, epsilon);
      assertEquals(capsuleDprosGleasonInteraction.averageGain, 56.7859272475, epsilon);
      assertEquals(capsuleDprosGleasonInteraction.expectedGain, 17.2918210555789, epsilon);
      assertEquals(capsuleDprosGleasonInteraction.averageTreeDepth, 4.0, 1e-1);
      assertEquals(capsuleDprosGleasonInteraction.averageTreeIndex, 28.5, 1e-1);

      // check leaf statistics
      FeatureInteraction psaVolInteraction = featureInteractionMap.get("PSA|VOL");
      assertEquals(psaVolInteraction.sumLeafValuesLeft, -0.467761263, epsilon);
      assertEquals(psaVolInteraction.sumLeafValuesRight, 0.56550011, epsilon);
      assertEquals(psaVolInteraction.sumLeafCoversLeft, 2.0, 1e-1);
      assertEquals(psaVolInteraction.sumLeafCoversRight, 2.0, 1e-1);

      // check split value histograms
      // CAPSULE
      assertEquals(capsuleInteraction.splitValueHistogram.get(0.5).toInteger(), 82.0, 1e-1);
      assertEquals(capsuleInteraction.splitValueHistogram.entrySet().size(), 1);
      // DCAPS
      assertEquals(featureInteractionMap.get("DCAPS").splitValueHistogram.get(1.5).toInteger(), 42.0, 1e-1);
      assertEquals(featureInteractionMap.get("DCAPS").splitValueHistogram.entrySet().size(), 1);
      // RACE
      assertEquals(featureInteractionMap.get("RACE").splitValueHistogram.get(0.5).toInteger(), 18.0, 1e-1);
      assertEquals(featureInteractionMap.get("RACE").splitValueHistogram.get(1.5).toInteger(), 62.0, 1e-1);
      assertEquals(featureInteractionMap.get("RACE").splitValueHistogram.entrySet().size(), 2);
      // GLEASON
      double[] expectedKeys = new double[]{2.5, 3.0, 5.5, 6.5, 7, 7.5, 8.5};
      double[] expectedValues = new double[]{2.0, 3.0, 44.0, 31.0, 1.0, 21.0, 14.0};
      for (int i = 0; i < expectedKeys.length; i++) {
        assertEquals(featureInteractionMap.get("GLEASON").splitValueHistogram.get(expectedKeys[i]).toInteger(), expectedValues[i], 1e-1);
      }
      assertEquals(featureInteractionMap.get("GLEASON").splitValueHistogram.entrySet().size(), 7);
      // DPROS
      expectedKeys = new double[]{1.5, 2.0, 2.5, 3.0, 3.5};
      expectedValues = new double[]{67.0, 3.0, 63.0, 12.0, 36.0};
      for (int i = 0; i < expectedKeys.length; i++) {
        assertEquals(featureInteractionMap.get("DPROS").splitValueHistogram.get(expectedKeys[i]).toInteger(), expectedValues[i], 1e-1);
      }
      assertEquals(featureInteractionMap.get("DPROS").splitValueHistogram.entrySet().size(), 5);
      // VOL
      expectedKeys = new double[]{5.75, 11.25, 13.75, 14.5, 15.75};
      expectedValues = new double[]{1.0, 1.0, 1.0, 2.0, 1.0};
      for (int i = 0; i < expectedKeys.length; i++) {
        assertEquals(featureInteractionMap.get("VOL").splitValueHistogram.get(expectedKeys[i]).toInteger(), expectedValues[i], 1e-1);
      }
      assertEquals(featureInteractionMap.get("VOL").splitValueHistogram.entrySet().size(), 180);
      // PSA
      expectedKeys = new double[]{1.5, 1.75, 3.25, 5.25, 8.75};
      expectedValues = new double[]{1.0, 4.0, 4.0, 3.0, 2.0};
      for (int i = 0; i < expectedKeys.length; i++) {
        assertEquals(psaInteraction.splitValueHistogram.get(expectedKeys[i]).toInteger(), expectedValues[i], 1e-1);
      }
      assertEquals(psaInteraction.splitValueHistogram.entrySet().size(), 261);

      TwoDimTable[] featureInteractionsTables = featureInteractionMap.getAsTable();
      TwoDimTable leafStatisticsTable = featureInteractionMap.getLeafStatisticsTable();
      TwoDimTable[] getSplitValuesHistograms = featureInteractionMap.getSplitValueHistograms();

      assertEquals(featureInteractionsTables.length, 3);
      assertEquals(featureInteractionsTables[0].getRowDim(), 7);
      assertEquals(featureInteractionsTables[1].getRowDim(), 25);
      assertEquals(featureInteractionsTables[2].getRowDim(), 53);
      assertEquals(leafStatisticsTable.getRowDim(), 1);
      assertEquals(getSplitValuesHistograms[0].getRowDim(), 261);
      assertEquals(getSplitValuesHistograms[1].getRowDim(), 180);
      assertEquals(getSplitValuesHistograms[2].getRowDim(), 1);
      assertEquals(getSplitValuesHistograms[3].getRowDim(), 5);
      assertEquals(getSplitValuesHistograms[4].getRowDim(), 7);
      assertEquals(getSplitValuesHistograms[5].getRowDim(), 2);
      assertEquals(getSplitValuesHistograms[6].getRowDim(), 1);
      
      TwoDimTable[][] overallFeatureInteractionsTable = model.getFeatureInteractionsTable(2,100,-1);
      assertEquals(overallFeatureInteractionsTable[0].length, 3);
      assertEquals(overallFeatureInteractionsTable[1].length, 1);
      assertEquals(overallFeatureInteractionsTable[2].length, 7);
    } finally {
      Scope.exit();
    }
  }


  @Test
  public void testXGBoostFeatureInteractionsAndCompareWithGBMFeatureInteractions() {
    Scope.enter();
    try {
      // create 2 similar trees and check whether they have similar feature interactions 
      Frame tfr = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._train = tfr._key;
      parms._response_column = "CAPSULE";
      parms._ignored_columns = new String[]{"ID"};
      parms._seed = 0xDECAF;
      parms._build_tree_one_node = true;
      parms._tree_method = XGBoostModel.XGBoostParameters.TreeMethod.exact;
      parms._ntrees = 1;
      parms._max_depth = 3;

      XGBoostModel model = (XGBoostModel) Scope.track_generic(new hex.tree.xgboost.XGBoost(parms).trainModel().get());
      
      GBMModel.GBMParameters gbmParms = new GBMModel.GBMParameters();
      gbmParms._train = parms._train;
      gbmParms._response_column = parms._response_column;
      gbmParms._ignored_columns = parms._ignored_columns;
      gbmParms._seed = parms._seed;
      gbmParms._build_tree_one_node = parms._build_tree_one_node;
      gbmParms._ntrees = parms._ntrees;
      gbmParms._max_depth = parms._max_depth;

      GBMModel gbmModel = new GBM(gbmParms).trainModel().get();
      Scope.track_generic(gbmModel);
      
      double level0avgDistanceByGain = calculateAverageDistanceOfSortedInteractions(gbmModel.getFeatureInteractions(0,100,-1), model.getFeatureInteractions(0,100,-1),0);
      double level1avgDistanceByGain = calculateAverageDistanceOfSortedInteractions(gbmModel.getFeatureInteractions(1,100,-1), model.getFeatureInteractions(1,100,-1),0);
      double level2avgDistanceByGain = calculateAverageDistanceOfSortedInteractions(gbmModel.getFeatureInteractions(2,100,-1), model.getFeatureInteractions(2,100,-1), 0);

      assertEquals(level0avgDistanceByGain, 1.66666666, 0.000001);
      assertEquals(level1avgDistanceByGain, 2.66666666, 0.000001);
      assertEquals(level2avgDistanceByGain, 2.42857142, 0.000001);

      double level0avgDistanceByCover = calculateAverageDistanceOfSortedInteractions(gbmModel.getFeatureInteractions(0,100,-1), model.getFeatureInteractions(0,100,-1),1);
      double level1avgDistanceByCover = calculateAverageDistanceOfSortedInteractions(gbmModel.getFeatureInteractions(1,100,-1), model.getFeatureInteractions(1,100,-1),1);
      double level2avgDistanceByCover = calculateAverageDistanceOfSortedInteractions(gbmModel.getFeatureInteractions(2,100,-1), model.getFeatureInteractions(2,100,-1), 1);

      assertEquals(level0avgDistanceByCover, 1.00000000, 0.000001);
      assertEquals(level1avgDistanceByCover, 2.33333333, 0.000001);
      assertEquals(level2avgDistanceByCover, 2.71428571, 0.000001);
      
    } finally {
      Scope.exit();
    }
  }
  
  // if some interaction is not present in both inputs, it is ignored
  // sortBy = 0 to sort by gain
  // sortBy != 1 to sort by cover
  private static double calculateAverageDistanceOfSortedInteractions(FeatureInteractions featureInteractions1, FeatureInteractions featureInteractions2, int sortByFeature) {
    List<KeyValue> list1 = new ArrayList<>();
    List<KeyValue> list2 = new ArrayList<>();

    for (Map.Entry<String, FeatureInteraction> featureInteraction : featureInteractions1.entrySet()) {
      list1.add(new KeyValue(featureInteraction.getKey(), sortByFeature == 0 ? featureInteraction.getValue().gain :  featureInteraction.getValue().cover));
    }
    for (Map.Entry<String, FeatureInteraction> featureInteraction : featureInteractions2.entrySet()) {
      list2.add(new KeyValue(featureInteraction.getKey(), sortByFeature == 0 ? featureInteraction.getValue().gain :  featureInteraction.getValue().cover));
    }
    List<String> sortedKeys1 = list1.stream()
            .sorted(Comparator.comparing(KeyValue::getValue))
            .map(KeyValue::getKey)
            .collect(Collectors.toList());
    List<String> sortedKeys2 = list2.stream()
            .sorted(Comparator.comparing(KeyValue::getValue))
            .map(KeyValue::getKey)
            .collect(Collectors.toList());

    double averageDistance = 0;
    int i, missing = 0;
    for (i = 0; i < sortedKeys1.size(); i++) {
      int j = sortedKeys2.indexOf(sortedKeys1.get(i));
      // if the key is missing in featureInteractions2 then don't count
      if (j != -1) {
        averageDistance += Math.abs(i - j);
      } else {
        missing++;
      }
    }
    
    return averageDistance / (i - missing);
  }

  @Test
  public void testMissingFoldColumnIsNotReportedInScoring() {
    try {
      Scope.enter();
      final Frame frame = TestFrameCatalog.specialColumns();

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._train = frame._key;
      parms._response_column = "Response";
      parms._fold_column = "Fold";
      parms._weights_column = "Weight";
      parms._offset_column = "Offset";
      parms._ntrees = 1;
      parms._min_rows = 0.1;
      parms._keep_cross_validation_models = false;
      parms._keep_cross_validation_predictions = false;
      parms._keep_cross_validation_fold_assignment = false;

      XGBoostModel xgb = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      Scope.track_generic(xgb);

      assertArrayEquals(new String[0], xgb._warnings);
      assertArrayEquals(null, xgb._warningsP); // no predict warning to begin with

      final Frame test = TestFrameCatalog.specialColumns();
      test.remove("Fold").remove();
      DKV.put(test);

      xgb.score(test).remove();

      assertArrayEquals(new String[0], xgb._warningsP); // no predict warnings
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testSamplingRatesAreValidated() {
    try {
      Scope.enter();
      final Frame frame = TestFrameCatalog.oneChunkFewRows();

      List<String> checkedSamplingParams = Arrays.asList(
              "colsample_bytree", "sample_rate", "col_sample_rate_per_tree", "colsample_bynode", 
              "subsample", "colsample_bylevel", "col_sample_rate"
      );
      List<String> ignoredParams = Collections.singletonList("sample_type");
      Set<String> knownSamplingParams = new HashSet<>();
      knownSamplingParams.addAll(checkedSamplingParams);
      knownSamplingParams.addAll(ignoredParams);

      Set<String> samplingParams = Arrays.stream(XGBoostV3.XGBoostParametersV3.fields)
              .filter(name -> name.contains("sample"))
              .collect(Collectors.toSet());
      assertEquals(new HashSet<>(knownSamplingParams), samplingParams);

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._train = frame._key;
      parms._response_column = frame.name(0);

      hex.tree.xgboost.XGBoost builder = new hex.tree.xgboost.XGBoost(parms);
      assertNoValidationError(builder);

      for (String param : checkedSamplingParams) {
        assertNoValidationError(runParamValidation(parms, param, 1e-3));
        assertNoValidationError(runParamValidation(parms, param, 0.42));
        assertNoValidationError(runParamValidation(parms, param, 1.0));
        assertHasValidationError(runParamValidation(parms, param, 0.0), param, "must be between 0 (exclusive) and 1 (inclusive)");
      }
    } finally {
      Scope.exit();
    }
  }

  private hex.tree.xgboost.XGBoost runParamValidation(XGBoostModel.XGBoostParameters parms, String param, double value) {
    XGBoostModel.XGBoostParameters p = (XGBoostModel.XGBoostParameters) parms.clone();
    PojoUtils.setField(p, param, value, PojoUtils.FieldNaming.DEST_HAS_UNDERSCORES);
    return new hex.tree.xgboost.XGBoost(p);
  }
  
  private void assertNoValidationError(hex.tree.xgboost.XGBoost builder) {
    assertEquals("", builder.validationErrors());
  }

  private void assertHasValidationError(hex.tree.xgboost.XGBoost builder, String field, String error) {
    System.out.println(builder.validationErrors());
    assertTrue(field, builder.validationErrors().contains(error));
  }

  private void assertJavaScoring(XGBoostModel model, Frame testFrame, Frame preds) {
    double relEpsilon = Boolean.parseBoolean(confJavaPredict) ? 
            1e-7 // pure java predict 
            : 
            getNativeRelEpsilon(model._parms._backend); // rel_epsilon based on backend
    assertTrue(model.testJavaScoring(testFrame, preds, relEpsilon));
  }

  private static double getNativeRelEpsilon(XGBoostModel.XGBoostParameters.Backend backend) {
    final double relEpsilon;
    switch (backend) {
      case cpu:
        relEpsilon = 1e-7;
        break;
      case gpu:
        // As demonstrated in https://github.com/h2oai/h2o-3/pull/5373/files sigmoid transformation
        // implemented on GPU gives different result than when running on CPU, therefore we pick a lower tolerance  
        relEpsilon = 1e-6;
        break;
      default:
        throw new IllegalStateException("Don't know how to determine tolerance for backend `" + backend + "`.");
    }
    return relEpsilon;
  }

  @Test
  public void testHStatistic() {
    XGBoostModel model = null;
    Scope.enter();
    try {
      Frame irisFrame = parseTestFile("smalldata/iris/iris_wheader.csv");
      Scope.track(irisFrame);
      
      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._dmatrix_type = XGBoostModel.XGBoostParameters.DMatrixType.auto;
      parms._response_column = "class";
      parms._train = irisFrame._key;
      parms._ntrees = 3;
      parms._seed = 1234L;
      
      model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      double h = model.getFriedmanPopescusH(irisFrame, new String[] {"sepal_len","sepal_wid"});
      assertTrue(Double.isNaN(h) || (h >= 0.0 && h <= 1.0));
    } finally {
      Scope.exit();
      if (model != null) model.delete();
    }
  }

  @Test
  public void testScalePosWeight() {
    Scope.enter();
    try {
      Frame train = parseTestFile("smalldata/covtype/covtype.20k.data");
      Scope.track(train);

      transformVec(train.lastVec(), x -> x == 6.0 ? 1.0 : 0.0);
      train.toCategoricalCol(train.lastVecName());

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._ignored_columns = new String[]{train.name(0)};
      parms._response_column = train.lastVecName();
      parms._train = train._key;
      parms._ntrees = 10;
      parms._seed = 1234L;

      XGBoostModel.XGBoostParameters parmsScaled = (XGBoostModel.XGBoostParameters) parms.clone();
      Vec response = train.lastVec();
      parmsScaled._scale_pos_weight = (float) ((response.length() - response.nzCnt()) / (double) response.nzCnt());

      XGBoostModel modelDefault = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      Scope.track_generic(modelDefault);
      XGBoostModel modelScaled = new hex.tree.xgboost.XGBoost(parmsScaled).trainModel().get();
      Scope.track_generic(modelScaled);

      // expect that _scale_pos_weight gives at least different output
      assertNotEquals("_scale_pos_weight xgboost parameter is not working. MPCEs for both models are the same",
              modelDefault.mean_per_class_error(), modelScaled.mean_per_class_error(), 1e-6);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testColSampleRate() {
    Scope.enter();
    try {
      XGBoostModel model1, model2;
      Frame train = parseTestFile("smalldata/gbm_test/ecology_model.csv");

      train.remove("Site").remove();
      train.remove("Method").remove();
      train.toCategoricalCol("Angaus");
      Scope.track(train);

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._train = train._key;
      parms._valid = train._key;
      parms._response_column = "Angaus";
      parms._distribution = multinomial;
      parms._ntrees = 5;
      parms._tree_method = XGBoostModel.XGBoostParameters.TreeMethod.hist;
      parms._seed = 42;
      parms._col_sample_rate = 0.9;
      model1 = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      Scope.track_generic(model1);

      XGBoostModel.XGBoostParameters parms2 = new XGBoostModel.XGBoostParameters();
      parms2._train = train._key;
      parms2._valid = train._key;
      parms2._response_column = "Angaus";
      parms2._distribution = multinomial;
      parms2._ntrees = 5;
      parms2._tree_method = XGBoostModel.XGBoostParameters.TreeMethod.hist;
      parms2._seed = 42;
      parms2._col_sample_rate = 0.1;
      model2 = new hex.tree.xgboost.XGBoost(parms2).trainModel().get();
      Scope.track_generic(model2);
      assertNotEquals(model1._output._training_metrics.rmse(), model2._output._training_metrics.rmse(), 0);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testColSampleRateSameValue() {
    Scope.enter();
    try {
      Frame train = parseTestFile("smalldata/gbm_test/ecology_model.csv");
      
      train.remove("Site").remove();
      train.remove("Method").remove();
      train.toCategoricalCol("Angaus");
      Scope.track(train);

      XGBoostModel model1, model2;
      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._train = train._key;
      parms._valid = train._key;
      parms._response_column = "Angaus";
      parms._distribution = multinomial;
      parms._ntrees = 5;
      parms._tree_method = XGBoostModel.XGBoostParameters.TreeMethod.hist;
      parms._seed = 42;
      parms._col_sample_rate = 0.9;
      model1 = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      Scope.track_generic(model1);
      
      parms._colsample_bylevel = 0.9;
      model2 = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      Scope.track_generic(model2);
      assertEquals(model1._output._training_metrics.rmse(), model2._output._training_metrics.rmse(), 0);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testColSampleRateAndAlias() {
    Scope.enter();
    try {
      XGBoostModel model1;
      Frame train = parseTestFile("smalldata/gbm_test/ecology_model.csv");

      // Fix training set
      train.remove("Site").remove();
      train.remove("Method").remove();
      train.toCategoricalCol("Angaus");
      Scope.track(train);

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._train = train._key;
      parms._valid = train._key;
      parms._response_column = "Angaus";
      parms._distribution = multinomial;
      parms._ntrees = 5;
      parms._tree_method = XGBoostModel.XGBoostParameters.TreeMethod.hist;
      parms._seed = 42;
      parms._col_sample_rate = 0.9;
      parms._colsample_bylevel = 0.3;
      model1 = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      Scope.track_generic(model1);
      fail("Model training should fail.");
    } catch(H2OModelBuilderIllegalArgumentException ex){
      assertTrue(ex.getMessage().contains("col_sample_rate and its alias colsample_bylevel are both set"));
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testColSampleRateAndAliasSame() {
    Scope.enter();
    try {
      XGBoostModel model1;
      Frame train = parseTestFile("smalldata/gbm_test/ecology_model.csv");

      train.remove("Site").remove();
      train.remove("Method").remove();
      train.toCategoricalCol("Angaus");
      Scope.track(train);

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._train = train._key;
      parms._valid = train._key;
      parms._response_column = "Angaus";
      parms._distribution = multinomial;
      parms._ntrees = 5;
      parms._tree_method = XGBoostModel.XGBoostParameters.TreeMethod.hist;
      parms._seed = 42;
      parms._col_sample_rate = 0.9;
      parms._colsample_bylevel = 0.9;
      model1 = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      Scope.track_generic(model1);
      assertEquals(model1._parms._col_sample_rate, model1._parms._colsample_bylevel, 0);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testUpdateWeightsWarning() {
    Scope.enter();
    try {
      Frame train = new TestFrameBuilder()
              .withColNames("C1", "C2")
              .withDataForCol(0, new double[]{0, 1})
              .withDataForCol(1, new double[]{0, 1})
              .build();
      
      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._response_column = train.lastVecName();
      parms._train = train._key;
      parms._ntrees = 100;

      XGBoostModel model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      Scope.track_generic(model);
      
      Vec weights = train.anyVec().makeCon(1.0);
      train.add("ws", weights);
      DKV.put(train);
      
      int nonEmpty = 0;
      for (int i = 0; i < parms._ntrees; i++) {
        if (model.convert(i, null).subgraphArray.get(0).rootNode.isLeaf()) {
          break;
        }
        nonEmpty += 1;
      }
      assertTrue(nonEmpty < parms._ntrees);
      
      assertFalse(model.updateAuxTreeWeights(train, "ws").hasWarnings());

      // now use a subset of the dataset to leave some nodes empty 
      weights.set(0, 0);
      Model.UpdateAuxTreeWeights.UpdateAuxTreeWeightsReport report = model.updateAuxTreeWeights(train, "ws");
      assertTrue(report.hasWarnings());
      assertArrayEquals(ArrayUtils.seq(0, nonEmpty), report._warn_trees);
      assertArrayEquals(new int[nonEmpty], report._warn_classes);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testValidateApproxTreeMethodAndColSampleRate() {
    Scope.enter();
    try {
      final String response = "power (hp)";

      Frame f = parseTestFile("smalldata/junit/cars.csv");
      Scope.track(f);

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._dmatrix_type = XGBoostModel.XGBoostParameters.DMatrixType.auto;
      parms._response_column = response;
      parms._train = f._key;
      parms._seed = 42;
      parms._colsample_bylevel = 0.5;
      parms._tree_method = XGBoostModel.XGBoostParameters.TreeMethod.approx;

      XGBoostModel model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      Scope.track_generic(model);
      fail("Model training should fail."); 
    } catch(H2OModelBuilderIllegalArgumentException ex){ 
      assertTrue(ex.getMessage().contains("Details: ERRR on field: _tree_method: approx is not supported with _col_sample_rate or _colsample_bylevel, use exact/hist instead or disable column sampling.")); 
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testApproxTreeMethodAndColSampleRateNative() throws XGBoostError {
    try {
      Map<String, String> rabitEnv = new HashMap<>();
      rabitEnv.put("DMLC_TASK_ID", "0");
      Rabit.init(rabitEnv);
      DMatrix trainMat = new DMatrix(new float[]{0, 1, 0, 1, 0, 1, 1, 1, 1, 1, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 1, 1, 0, 1, 1, 1, 1, 0, 1}, 10, 3);
      trainMat.setLabel(new float[]{0, 1, 0, 1, 1, 1, 1, 0, 0, 1});

      HashMap<String, Object> params = new HashMap<>();
      params.put("eta", 1.0);
      params.put("max_depth", 5);
      params.put("silent", 0);
      params.put("objective", "binary:logistic");
      params.put("min_child_weight", 0);
      params.put("colsample_bylevel", 0.3);
      params.put("tree_method", "approx");
      params.put("seed", 42);

      HashMap<String, DMatrix> watches = new HashMap<>();
      watches.put("train", trainMat);

      Booster booster = XGBoost.train(trainMat, params, 1, watches, null, null);
      float[][] preds = booster.predict(trainMat);
      
      params = new HashMap<>();
      params.put("eta", 1.0);
      params.put("max_depth", 5);
      params.put("silent", 0);
      params.put("objective", "binary:logistic");
      params.put("min_child_weight", 0);
      params.put("colsample_bylevel", 1);
      params.put("tree_method", "approx");
      params.put("seed", 42);

      Booster booster2 = XGBoost.train(trainMat, params, 1, watches, null, null);
      float[][] preds2 = booster2.predict(trainMat);
      
      assertArrayEquals(preds, preds2);

      params = new HashMap<>();
      params.put("eta", 1.0);
      params.put("max_depth", 5);
      params.put("silent", 0);
      params.put("objective", "binary:logistic");
      params.put("min_child_weight", 0);
      params.put("colsample_bylevel", 1);
      params.put("tree_method", "hist");
      params.put("seed", 42);

      Booster booster3 = XGBoost.train(trainMat, params, 1, watches, null, null);
      float[][] preds3 = booster3.predict(trainMat);

      params = new HashMap<>();
      params.put("eta", 1.0);
      params.put("max_depth", 5);
      params.put("silent", 0);
      params.put("objective", "binary:logistic");
      params.put("min_child_weight", 0);
      params.put("colsample_bylevel", 0.3);
      params.put("tree_method", "hist");
      params.put("seed", 42);

      Booster booster4 = XGBoost.train(trainMat, params, 1, watches, null, null);
      float[][] preds4 = booster4.predict(trainMat);

      assertFalse(Arrays.equals(preds3, preds4));

    } finally {
      Rabit.shutdown();
    }
  }

  @Test
  public void testSomeCategoricalEncodingIsNotSupported() {
    ExpectedException exceptionRule = ExpectedException.none();
    Scope.enter();
    try {
      String response = "RainTomorrow";
      Frame df = loadWeather(response);

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._ntrees = 5;
      parms._max_depth = 5;
      parms._train = df._key;
      parms._response_column = response;

      for (CategoricalEncodingScheme categoricalEncoding : Arrays.asList(Eigen, Enum)) {
        parms._categorical_encoding = categoricalEncoding;
        try {
          exceptionRule = ExpectedException.none();
          new hex.tree.xgboost.XGBoost(parms).trainModel().get();
        }
        catch (H2OModelBuilderIllegalArgumentException e) {
          exceptionRule.expect(H2OModelBuilderIllegalArgumentException.class);
          exceptionRule.expectMessage(categoricalEncoding + " encoding is not supported");
        }
      }
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void checkRunningWithClusterPrestartInCI() {
    Assume.assumeTrue(isCI());
    assertTrue(hex.tree.xgboost.XGBoost.prestartExternalClusterForCV());
  }

  @Test
  public void testEvalMetric() {
    Scope.enter();
    try {
      String response = "RainTomorrow";
      Frame df = loadWeather(response);

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._ntrees = 5;
      parms._max_depth = 5;
      parms._train = df._key;
      parms._response_column = response;
      parms._eval_metric = "logloss";
      parms._score_each_iteration = true;

      XGBoostModel model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      Scope.track_generic(model);
      LOG.info(model);

      TwoDimTable scoringHistory = model._output._scoring_history;
      int h2oLoglossIdx = ArrayUtils.find(scoringHistory.getColHeaders(), "Training LogLoss");
      assertTrue(h2oLoglossIdx > 0);
      int xgbLoglossIdx = ArrayUtils.find(scoringHistory.getColHeaders(), "Training Custom");
      assertTrue(xgbLoglossIdx > 0);

      assertEquals(1 /*null model*/ + parms._ntrees, scoringHistory.getRowDim());
      for (int i = 0; i < scoringHistory.getRowDim(); i++) {
        double h2oValue = (Double) scoringHistory.get(i, h2oLoglossIdx);
        double xgbValue = (Double) scoringHistory.get(i, xgbLoglossIdx);
        assertEquals(h2oValue, xgbValue, 1e-5);
      }
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testEvalMetricWithValidation() {
    Scope.enter();
    try {
      String response = "RainTomorrow";
      Frame df = loadWeather(response);

      // split into train/test
      SplitFrame sf = new SplitFrame(df, new double[] { 0.7, 0.3 }, null);
      sf.exec().get();
      Key<Frame>[] ksplits = sf._destination_frames;
      Frame trainFrame = Scope.track(ksplits[0].get());
      Frame testFrame = Scope.track(ksplits[1].get());

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._ntrees = 5;
      parms._max_depth = 5;
      parms._train = trainFrame._key;
      parms._valid = testFrame._key;
      parms._response_column = response;
      parms._eval_metric = "logloss";
      parms._score_each_iteration = true;

      XGBoostModel model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      Scope.track_generic(model);
      LOG.info(model);

      TwoDimTable scoringHistory = model._output._scoring_history;
      int h2oTrainLoglossIdx = ArrayUtils.find(scoringHistory.getColHeaders(), "Training LogLoss");
      assertTrue(h2oTrainLoglossIdx > 0);
      int xgbTrainLoglossIdx = ArrayUtils.find(scoringHistory.getColHeaders(), "Training Custom");
      assertTrue(xgbTrainLoglossIdx > 0);
      int h2oValidLoglossIdx = ArrayUtils.find(scoringHistory.getColHeaders(), "Validation LogLoss");
      assertTrue(h2oValidLoglossIdx > 0);
      int xgbValidLoglossIdx = ArrayUtils.find(scoringHistory.getColHeaders(), "Validation Custom");
      assertTrue(xgbValidLoglossIdx > 0);

      assertEquals(1 /*null model*/ + parms._ntrees, scoringHistory.getRowDim());
      for (int i = 0; i < scoringHistory.getRowDim(); i++) {
        // check training
        double h2oTrainValue = (Double) scoringHistory.get(i, h2oTrainLoglossIdx);
        double xgbTrainValue = (Double) scoringHistory.get(i, xgbTrainLoglossIdx);
        assertEquals(h2oTrainValue, xgbTrainValue, 1e-5);
        // check validation
        double h2oValidValue = (Double) scoringHistory.get(i, h2oValidLoglossIdx);
        double xgbValidValue = (Double) scoringHistory.get(i, xgbValidLoglossIdx);
        assertEquals(h2oValidValue, xgbValidValue, 1e-5);
      }
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testEarlyStoppingOnEvalMetric() {
    checkEarlyStoppingOnEvalMetric(false, false);
  }

  @Test
  public void testEarlyStoppingOnEvalMetricWithValidation() {
    checkEarlyStoppingOnEvalMetric(true, false);
  }

  @Test
  public void testEarlyStoppingOnEvalMetric_scoreEvalMetricOnly() {
    checkEarlyStoppingOnEvalMetric(false, true);
  }

  @Test
  public void testEarlyStoppingOnEvalMetricWithValidation_scoreEvalMetricOnly() {
    checkEarlyStoppingOnEvalMetric(true, true);
  }

  private void checkEarlyStoppingOnEvalMetric(final boolean useValidation, final boolean scoreEvalMetricOnly) {
    Scope.enter();
    try {
      final String response = "RainTomorrow";
      final int ntrees = 100;

      XGBoostModel.XGBoostParameters basicParms = new XGBoostModel.XGBoostParameters();
      Frame df = loadWeather(response);

      if (useValidation) {
        // split into train/valid
        SplitFrame sf = new SplitFrame(df, new double[] { 0.7, 0.3 }, null);
        sf.exec().get();
        //df.delete();
        Key<Frame>[] splits = sf._destination_frames;
        Frame trainFrame = Scope.track(splits[0].get());
        Frame validFrame = Scope.track(splits[1].get());
        basicParms._train = trainFrame._key;
        basicParms._valid = validFrame._key;
      } else {
        basicParms._train = df._key;
      }

      basicParms._ntrees = ntrees;
      basicParms._max_depth = 3;
      basicParms._response_column = response;
      basicParms._stopping_rounds = 3;
      basicParms._stopping_tolerance = 1e-1;
      basicParms._score_each_iteration = true;

      XGBoostModel.XGBoostParameters parms = (XGBoostModel.XGBoostParameters) basicParms.clone();
      parms._eval_metric = "logloss";
      parms._stopping_metric = ScoreKeeper.StoppingMetric.custom;
      parms._score_eval_metric_only = scoreEvalMetricOnly;

      XGBoostModel model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      Scope.track_generic(model);
      LOG.info(model);

      TwoDimTable scoringHistory = model._output._scoring_history;
      // 1.  Check that we actually stopped early - for ntrees = 100, in the interval of [5, 95]
      assertEquals(ntrees / 2.0, scoringHistory.getRowDim(), (ntrees / 2.0) * 0.9);

      // 2. If we do have H2O metric, compare it to eval metric
      if (!scoreEvalMetricOnly) {
        for (ScoreKeeper sk : model._output.scoreKeepers()) {
          assertEquals(sk._logloss, sk._custom_metric, 1e-5);
        }
      }

      // 3. Check that we stopped at the right time based on the value of the evaluation stopping metric
      int shouldStopIter = -1;
      for (int i = 1; i < ntrees; i++) {
        ScoreKeeper[] sks = Arrays.copyOf(model._output.scoreKeepers(), i);
        boolean shouldStop = ScoreKeeper.stopEarly(sks, 3, ScoreKeeper.ProblemType.classification, ScoreKeeper.StoppingMetric.custom, 1e-1, "model", true);
        if (shouldStop) {
          shouldStopIter = i;
          break;
        }
      }
      assertNotEquals(-1, shouldStopIter);
      assertEquals(shouldStopIter, model._output._ntrees + 1);

      // 4. Validate early stopping against a fully scored model trained with logloss for early stopping
      // Keep in mind that H2O logloss and XGBoost logloss will not be identical - there will be differences
      // caused by a different precision for each value, and we cannot expect the same stopping iteration
      // in all cases. This should work for the purpose of this test.
      XGBoostModel.XGBoostParameters parmsH2O = (XGBoostModel.XGBoostParameters) basicParms.clone();
      parmsH2O._eval_metric = null;
      parmsH2O._stopping_metric = ScoreKeeper.StoppingMetric.logloss;

      XGBoostModel modelH2O = new hex.tree.xgboost.XGBoost(basicParms).trainModel().get();
      assertNotNull(modelH2O);
      Scope.track_generic(modelH2O);
      LOG.info(modelH2O);

      assertEquals(modelH2O._output._ntrees, model._output._ntrees);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testToCustomMetric() {
    EvalMetric em = new EvalMetric("anything", Math.E, Math.PI);

    CustomMetric cmTrain = hex.tree.xgboost.XGBoost.toCustomMetricTrain(em);
    assertEquals("anything", cmTrain.name);
    assertEquals(Math.E, cmTrain.value, 0);

    CustomMetric cmValid = hex.tree.xgboost.XGBoost.toCustomMetricValid(em);
    assertEquals("anything", cmValid.name);
    assertEquals(Math.PI, cmValid.value, 0);

    assertNull(hex.tree.xgboost.XGBoost.toCustomMetricTrain(null));
    assertNull(hex.tree.xgboost.XGBoost.toCustomMetricValid(null));
  }

  @Test
  public void testValidMatrixOnSubsetOfTrainMatrixNodes() { // == some nodes don't hold any data of the validation dataset
    Assume.assumeTrue(H2O.getCloudSize() > 1);
    Scope.enter();
    try {
      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();

      Frame train = parseAndTrackTestFile("./smalldata/logreg/prostate_train.csv");
      train = ensureDistributed(train); // distributed

      Frame valid = parseAndTrackTestFile("./smalldata/logreg/prostate_test.csv");
      assertEquals(1, valid.anyVec().nChunks()); // not distributed

      parms._response_column = "AGE";
      parms._train = train._key;
      parms._valid = valid._key;
      parms._ntrees = 10;
      parms._max_depth = 3;
      parms._score_each_iteration = true;
      parms._eval_metric = "rmse";
      parms._score_eval_metric_only = true;

      XGBoostModel model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      Scope.track_generic(model);
      LOG.info(model);

      TwoDimTable scoringHistory = model._output._scoring_history;
      assertTrue(ArrayUtils.find(scoringHistory.getColHeaders(), "Validation Custom") >= 0);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testValidMatrixNotCollocatedWithTrainMatrix() { // == training and validation are on different nodes
    Assume.assumeTrue(H2O.getCloudSize() > 1);
    Scope.enter();
    try {
      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();

      Frame train = parseAndTrackTestFile("./smalldata/logreg/prostate_train.csv");
      assertEquals(1, train.anyVec().nChunks()); // not distributed, only on leader node
      Frame valid = parseAndTrackTestFile("./smalldata/logreg/prostate_test.csv");
      valid = ensureDistributed(valid); // distributed on all nodes

      XGBoostSetupTask.FrameNodes trainFrameNodes = XGBoostSetupTask.findFrameNodes(train);
      XGBoostSetupTask.FrameNodes validFrameNodes = XGBoostSetupTask.findFrameNodes(valid);
      assertFalse(validFrameNodes.isSubsetOf(trainFrameNodes)); 

      parms._response_column = "AGE";
      parms._train = train._key;
      parms._valid = valid._key;
      parms._ntrees = 10;
      parms._max_depth = 3;
      parms._score_each_iteration = true;
      parms._eval_metric = "rmse";
      parms._score_eval_metric_only = true;

      XGBoostModel model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      Scope.track_generic(model);
      LOG.info(model);

      TwoDimTable scoringHistory = model._output._scoring_history;
      assertTrue(ArrayUtils.find(scoringHistory.getColHeaders(), "Validation Custom") >= 0);
    } finally {
      Scope.exit();
    }
  }

  /**
   * Show 2 XGBoost models can run concurrently on a (single) GPU. Models used to have exclusive access to GPU
   * which we removed. This test shows there is no longer exclusive ownership of the GPU and models are not forced
   * to run sequentially.
   */
  @Test
  public void testConcurrentModelsOnGPU() {
    Assume.assumeTrue(H2O.getCloudSize() == 1); // would run single node anyway
    Assume.assumeTrue(GpuUtils.hasGPU());
    Scope.enter();
    try {
      Frame train = parseAndTrackTestFile("./smalldata/chicago/chicagoCrimes10k.csv.zip");

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._response_column = "Arrest";
      parms._train = train._key;
      parms._ntrees = 20;
      parms._max_depth = 5;
      parms._score_each_iteration = true;

      Job<XGBoostModel> job1 = new hex.tree.xgboost.XGBoost((XGBoostModel.XGBoostParameters) parms.clone()).trainModel();
      Job<XGBoostModel> job2 = new hex.tree.xgboost.XGBoost((XGBoostModel.XGBoostParameters) parms.clone()).trainModel();

      XGBoostModel model1 = job1.get();
      assertNotNull(model1);
      Scope.track_generic(model1);
      XGBoostModel model2 = job2.get();
      assertNotNull(model2);
      Scope.track_generic(model2);

      long[] timestamps1 = model1._output._training_time_ms;
      long[] timestamps2 = model2._output._training_time_ms;
      // check that there was an overlap in model training (=they were running at the same time)
      assertFalse(
              timestamps2[0] > timestamps1[timestamps1.length-1] || // second model started after the first one
                      timestamps2[timestamps2.length-1] < timestamps1[0] // second model finished before the first one
      );
    } finally {
      Scope.exit();
    }
    
  }

  @Test
  public void testWarnEvalMetricOnlyWithouEvalMetric() {
    Scope.enter();
    try {
      String response = "CAPSULE";
      Frame train = parseAndTrackTestFile("./smalldata/logreg/prostate_train.csv");
      train.toCategoricalCol(response);

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._ntrees = 1;
      parms._train = train._key;
      parms._response_column = response;
      parms._score_eval_metric_only = true;

      ModelBuilder job =  new hex.tree.xgboost.XGBoost(parms);

      XGBoostModel xgboost = (XGBoostModel) job.trainModel().get();
      Scope.track_generic(xgboost);
      assertNotNull(xgboost);
      assertTrue("Parameter is not validate", job.validationWarnings().contains("score_eval_metric_only is set but eval_metric parameter is not defined"));
      System.out.println(job.validationWarnings());
    }
    finally {
      Scope.exit();
    }
  }

  @Test
  public void testGBLinearTopKAndFeatureSelector() {
    Scope.enter();
    try {
      String response = "CAPSULE";
      Frame train = parseAndTrackTestFile("./smalldata/logreg/prostate_train.csv");
      train.toCategoricalCol(response);

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._ntrees = 1;
      parms._train = train._key;
      parms._response_column = response;
      parms._booster = XGBoostModel.XGBoostParameters.Booster.gblinear;
      parms._top_k = 2;
      parms._feature_selector = XGBoostModel.XGBoostParameters.FeatureSelector.greedy;

      ModelBuilder job =  new hex.tree.xgboost.XGBoost(parms);

      XGBoostModel xgboost = (XGBoostModel) job.trainModel().get();
      Scope.track_generic(xgboost);
      assertNotNull(xgboost);

      Frame score = xgboost.score(train);
      Scope.track(score);

      parms._top_k = 100;
      ModelBuilder jobTopKChanged =  new hex.tree.xgboost.XGBoost(parms);

      XGBoostModel xgboostTopKChanged = (XGBoostModel) jobTopKChanged.trainModel().get();
      Scope.track_generic(xgboostTopKChanged);
      assertNotNull(xgboostTopKChanged);

      Frame scoreTopKChanged = xgboostTopKChanged.score(train);
      Scope.track(scoreTopKChanged);
      assertNotEquals("top_k should affect the predictions", score.toTwoDimTable().get(0,1), scoreTopKChanged.toTwoDimTable().get(0,1));
    }
    finally {
      Scope.exit();
    }
  }


  @Test
  public void testGBLinearShotgun() {
    Scope.enter();
    try {
      String response = "CAPSULE";
      Frame train = parseAndTrackTestFile("./smalldata/logreg/prostate_train.csv");
      train.toCategoricalCol(response);

      XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._ntrees = 1;
      parms._train = train._key;
      parms._response_column = response;
      parms._booster = XGBoostModel.XGBoostParameters.Booster.gblinear;
      parms._updater = XGBoostModel.XGBoostParameters.Updater.shotgun;
      parms._feature_selector = XGBoostModel.XGBoostParameters.FeatureSelector.shuffle;

      ModelBuilder job =  new hex.tree.xgboost.XGBoost(parms);
      XGBoostModel xgboost = (XGBoostModel) job.trainModel().get();
      assertNotNull(xgboost);
      Scope.track_generic(xgboost);
      assertEquals("updater should be changed", xgboost._output._native_parameters.get(1,1), XGBoostModel.XGBoostParameters.Updater.shotgun.toString());
    }
    finally {
      Scope.exit();
    }
  }
}
