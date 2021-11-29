package water;

import hex.ModelExportOption;
import hex.glrm.GLRM;
import hex.glrm.GLRMModel;
import hex.rulefit.RuleFit;
import hex.rulefit.RuleFitModel;
import hex.tree.isofor.IsolationForest;
import hex.tree.isofor.IsolationForestModel;
import hex.tree.isoforextended.ExtendedIsolationForest;
import hex.tree.isoforextended.ExtendedIsolationForestModel;
import hex.tree.uplift.UpliftDRF;
import hex.tree.uplift.UpliftDRFModel;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import hex.Model;
import hex.ModelMetrics;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.tree.CompressedTree;
import hex.tree.SharedTreeModel;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.runner.RunWith;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.Log;

import static org.junit.Assert.*;
import static water.TestUtil.ar;
import static water.TestUtil.assertFrameEquals;
import static water.TestUtil.parseTestFile;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class ModelSerializationTest {

  @Test public void testSimpleModel() throws IOException {
    // Create a model
    BlahModel.BlahParameters params = new BlahModel.BlahParameters();
    BlahModel.BlahOutput output = new BlahModel.BlahOutput(false, false, false);

    Model model = new BlahModel(Key.make("BLAHModel"), params, output);
    DKV.put(model._key, model);
    // Create a serializer, save a model and reload it
    Model loadedModel = null;
    try {
      loadedModel = saveAndLoad(model);
      // And compare
      assertModelBinaryEquals(model, loadedModel);
    } finally {
      if (loadedModel != null) loadedModel.delete();
    }
  }

  @Test
  public void testGBMModelMultinomial() throws IOException {
    GBMModel model, loadedModel = null;
    try {
      model = prepareGBMModel("smalldata/iris/iris.csv", "C5");
      CompressedTree[][] trees = getTrees(model);
      loadedModel = saveAndLoad(model);
      // And compare
      assertModelBinaryEquals(model, loadedModel);
      CompressedTree[][] loadedTrees = getTrees(loadedModel);
      assertTreeEquals("Trees have to be binary same", trees, loadedTrees);
    } finally {
      if (loadedModel!=null) loadedModel.delete();
    }
  }

  @Test
  public void testGBMModelBinomial() throws IOException {
    GBMModel model, loadedModel = null;
    try {
      GBMModel.GBMParameters gbmParameters = new GBMModel.GBMParameters();
      gbmParameters._ignored_columns = ar("ID");
      model = prepareGBMModel("smalldata/logreg/prostate.csv", gbmParameters, "CAPSULE");
      CompressedTree[][] trees = getTrees(model);
      loadedModel = saveAndLoad(model);
      // And compare
      assertModelBinaryEquals(model, loadedModel);
      CompressedTree[][] loadedTrees = getTrees(loadedModel);
      assertTreeEquals("Trees have to be binary same", trees, loadedTrees);
    } finally {
      if (loadedModel!=null) loadedModel.delete();
    }
  }

  @Test
  public void testGBMModelBinomialWithCV() throws IOException {
    GBMModel model, loadedModel = null;
    final Key<Frame> holdPredsCloneKey = Key.make();
    try {
      GBMModel.GBMParameters gbmParameters = new GBMModel.GBMParameters();
      gbmParameters._ignored_columns = ar("ID");
      gbmParameters._nfolds = 2;
      gbmParameters._keep_cross_validation_predictions = true;
      model = prepareGBMModel("smalldata/logreg/prostate.csv", gbmParameters, "CAPSULE");
      Frame holdoutPreds = DKV.getGet(model._output._cross_validation_holdout_predictions_frame_id);
      assertNotNull(holdoutPreds);
      Frame holdoutPredsClone = holdoutPreds.deepCopy(holdPredsCloneKey.toString());
      DKV.put(holdoutPredsClone);
      loadedModel = saveAndLoad(model, ModelExportOption.INCLUDE_CV_PREDICTIONS);
      // And compare
      assertModelBinaryEquals(model, loadedModel);
      // Check that holdout predictions were re-loaded as well
      Frame holdoutPredsReloaded = DKV.getGet(model._output._cross_validation_holdout_predictions_frame_id);
      assertNotNull(holdoutPredsReloaded);
      assertFrameEquals(holdoutPredsClone, holdoutPredsReloaded, 0);
    } finally {
      if (loadedModel!=null) 
        loadedModel.delete();
      Keyed.remove(holdPredsCloneKey);
    }
  }

  @Test
  public void testGBMModelBinomialWithCV_noExport() throws IOException {
    GBMModel model, loadedModel = null;
    try {
      GBMModel.GBMParameters gbmParameters = new GBMModel.GBMParameters();
      gbmParameters._ignored_columns = ar("ID");
      gbmParameters._nfolds = 2;
      gbmParameters._keep_cross_validation_predictions = true;
      model = prepareGBMModel("smalldata/logreg/prostate.csv", gbmParameters, "CAPSULE");
      assertNotNull(DKV.getGet(model._output._cross_validation_holdout_predictions_frame_id));
      loadedModel = saveAndLoad(model);
      // And compare
      assertModelBinaryEquals(model, loadedModel);
      // Check that holdout predictions were re-loaded as well
      assertNull(DKV.getGet(model._output._cross_validation_holdout_predictions_frame_id));
    } finally {
      if (loadedModel!=null)
        loadedModel.delete();
    }
  }

  @Test
  public void testDRFModelMultinomial() throws IOException {
    DRFModel model, loadedModel = null;
    try {
      model = prepareDRFModel("smalldata/iris/iris.csv", new String[0], "C5", true, 5);
      CompressedTree[][] trees = getTrees(model);
      loadedModel = saveAndLoad(model);
      // And compare
      assertModelBinaryEquals(model, loadedModel);
      CompressedTree[][] loadedTrees = getTrees(loadedModel);
      assertTreeEquals("Trees have to be binary same", trees, loadedTrees);
    } finally {
      if (loadedModel!=null) loadedModel.delete();
    }
  }

  @Test
  public void testDRFModelBinomial() throws IOException {
    DRFModel model = null, loadedModel = null;
    try {
      model = prepareDRFModel("smalldata/logreg/prostate.csv", ar("ID"), "CAPSULE", true, 5);
      CompressedTree[][] trees = getTrees(model);
      loadedModel = saveAndLoad(model);
      // And compare
      assertModelBinaryEquals(model, loadedModel);
      CompressedTree[][] loadedTrees = getTrees(loadedModel);
      assertTreeEquals("Trees have to be binary same", trees, loadedTrees);
    } finally {
      if (model!=null) model.delete();
      if (loadedModel!=null) loadedModel.delete();
    }
  }

  @Test
  public void testIsolationForestModel() throws IOException {
    IsolationForestModel model = null, loadedModel = null;
    try {
      model = prepareIsoForModel("smalldata/logreg/prostate.csv", ar("ID", "CAPSULE"), 5);
      CompressedTree[][] trees = getTrees(model);
      loadedModel = saveAndLoad(model);
      // And compare
      assertModelBinaryEquals(model, loadedModel);
      CompressedTree[][] loadedTrees = getTrees(loadedModel);
      assertTreeEquals("Trees have to be binary same", trees, loadedTrees);
    } finally {
      if (model!=null) model.delete();
      if (loadedModel!=null) loadedModel.delete();
    }
  }

  @Test
  public void testExtendedIsolationForestModel() throws IOException {
    ExtendedIsolationForestModel model = null;
    ExtendedIsolationForestModel loadedModel = null;
    Frame frame = null;
    Frame anomaly = null;
    Frame anomalyLoaded = null;
    try {
      frame = parseTestFile("smalldata/logreg/prostate.csv");
      model = prepareExtIsoForModel(frame, ar("ID", "CAPSULE"), 5, 1);
      loadedModel = saveAndLoad(model);
      assertModelBinaryEquals(model, loadedModel);
      anomaly = model.score(frame);
      anomalyLoaded = loadedModel.score(frame);
      assertFrameEquals(anomaly, anomalyLoaded, 1e-3);
    } finally {
      if (model != null)
        model.delete();
      if (loadedModel != null)
        loadedModel.delete();
      if (frame != null)
        frame.delete();
      if (anomaly != null)
        anomaly.delete();
      if (anomalyLoaded != null)
        anomalyLoaded.delete();
    }
  }
  
  @Test
  public void testUpliftDRFModel() throws IOException {
    try {
      Scope.enter();
      Frame frame = Scope.track(parseTestFile("smalldata/uplift/criteo_uplift_13k.csv"));
      UpliftDRFModel model = prepareUpliftDRFModel(frame, ar("treatment", "conversion"), "treatment", "conversion");
      Scope.track_generic(model);
      UpliftDRFModel loadedModel = saveAndLoad(model);
      Scope.track_generic(loadedModel);
      assertModelBinaryEquals(model, loadedModel);
      Frame uplift = Scope.track(model.score(frame));
      Frame upliftLoaded = Scope.track(loadedModel.score(frame));
      assertFrameEquals(uplift, upliftLoaded, 1e-3);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testGLMModel() throws IOException {
    GLMModel model, loadedModel = null;
    try {
      model = prepareGLMModel("smalldata/junit/cars.csv", new String[0], "power (hp)", GLMModel.GLMParameters.Family.poisson);
      loadedModel = saveAndLoad(model);
      assertModelBinaryEquals(model, loadedModel);
    } finally {
      if (loadedModel!=null) loadedModel.delete();
    }
  }

  @Test
  public void testGLRMModel() throws IOException {
    GLRMModel model, loadedModel = null;
    try {
      model = prepareGLRMModel("smalldata/junit/cars.csv", new String[0], "power (hp)");
      loadedModel = saveAndLoad(model);
      assertModelBinaryEquals(model, loadedModel);
      assertNotNull(loadedModel._output._init_key.get());
      assertNotNull(loadedModel._output._representation_key.get());
      for (Key<ModelMetrics> mmKey : loadedModel._output.getModelMetrics()) {
        assertNotNull(mmKey.get());         
      }
    } finally {
      if (loadedModel != null) loadedModel.delete();
    }
  }

  private GBMModel prepareGBMModel(String dataset, String response) {
    return prepareGBMModel(dataset, new GBMModel.GBMParameters(), response);
  }

  @Test
  public void testRuleFitModel() throws IOException {
    RuleFitModel model = null, loadedModel = null;
    Frame fr = null, pr = null;
    try {
      model = prepareRuleFitModel("smalldata/junit/cars.csv", new String[0], "power (hp)");
      loadedModel = saveAndLoad(model);
      assertModelBinaryEquals(model, loadedModel);
      for (Key<ModelMetrics> mmKey : loadedModel._output.getModelMetrics()) {
        assertNotNull(mmKey.get());
      }
      fr = parseTestFile("smalldata/junit/cars.csv");
      pr = loadedModel.score(fr);
    } finally {
      if (loadedModel != null) loadedModel.delete();
      if (model != null) model.delete();
      if (fr != null) fr.delete();
      if (pr != null) pr.delete();
    }
  }
  
  private GBMModel prepareGBMModel(String dataset, GBMModel.GBMParameters gbmParams, String response) {
    Frame f = parseTestFile(dataset);
    try {
      if (!f.vec(response).isCategorical()) {
        f.replace(f.find(response), f.vec(response).toCategoricalVec()).remove();
        DKV.put(f._key, f);
      }
      gbmParams._train = f._key;
      gbmParams._response_column = response;
      gbmParams._ntrees = 5;
      gbmParams._score_each_iteration = true;
      return new GBM(gbmParams).trainModel().get();
    } finally {
      if (f!=null) f.delete();
    }
  }

  private DRFModel prepareDRFModel(String dataset, String[] ignoredColumns, String response, boolean classification, int ntrees) {
    Frame f = parseTestFile(dataset);
    try {
      if (classification && !f.vec(response).isCategorical()) {
        f.replace(f.find(response), f.vec(response).toCategoricalVec()).remove();
        DKV.put(f._key, f);
      }
      DRFModel.DRFParameters drfParams = new DRFModel.DRFParameters();
      drfParams._train = f._key;
      drfParams._ignored_columns = ignoredColumns;
      drfParams._response_column = response;
      drfParams._ntrees = ntrees;
      drfParams._score_each_iteration = true;
      return new DRF(drfParams).trainModel().get();
    } finally {
      if (f!=null) f.delete();
    }
  }

  private IsolationForestModel prepareIsoForModel(String dataset, String[] ignoredColumns, int ntrees) {
    Frame f = parseTestFile(dataset);
    try {
      IsolationForestModel.IsolationForestParameters ifParams = new IsolationForestModel.IsolationForestParameters();
      ifParams._train = f._key;
      ifParams._ignored_columns = ignoredColumns;
      ifParams._ntrees = ntrees;
      ifParams._score_each_iteration = true;
      return new IsolationForest(ifParams).trainModel().get();
    } finally {
      if (f!=null) f.delete();
    }
  }

  private ExtendedIsolationForestModel prepareExtIsoForModel(Frame frame, String[] ignoredColumns,
                                                             int ntrees, int extensionLevel) {
      ExtendedIsolationForestModel.ExtendedIsolationForestParameters eifParams =
              new ExtendedIsolationForestModel.ExtendedIsolationForestParameters();
      eifParams._train = frame._key;
      eifParams._ignored_columns = ignoredColumns;
      eifParams._ntrees = ntrees;
      eifParams._extension_level = extensionLevel;

      return new ExtendedIsolationForest(eifParams).trainModel().get();
  }

  private UpliftDRFModel prepareUpliftDRFModel(Frame frame, String[] ignoredColumns, String treatmentCol, String responseCol) {
    frame.toCategoricalCol(treatmentCol);
    frame.toCategoricalCol(responseCol);
    UpliftDRFModel.UpliftDRFParameters upliftParams = new UpliftDRFModel.UpliftDRFParameters();
    upliftParams._train = frame._key;
    upliftParams._ignored_columns = ignoredColumns;
    upliftParams._treatment_column = treatmentCol;
    upliftParams._response_column = responseCol;
    upliftParams._seed = 0xDECAF;

    return new UpliftDRF(upliftParams).trainModel().get();
  }

  private GLMModel prepareGLMModel(String dataset, String[] ignoredColumns, String response, GLMModel.GLMParameters.Family family) {
    Frame f = parseTestFile(dataset);
    try {
      GLMModel.GLMParameters params = new GLMModel.GLMParameters();
      params._train = f._key;
      params._ignored_columns = ignoredColumns;
      params._response_column = response;
      params._family = family;
      return new GLM(params).trainModel().get();
    } finally {
      if (f!=null) f.delete();
    }
  }

  private GLRMModel prepareGLRMModel(String dataset, String[] ignoredColumns, String response) {
    Frame f = parseTestFile(dataset);
    try {
      GLRMModel.GLRMParameters params = new GLRMModel.GLRMParameters();
      params._train = f._key;
      params._ignored_columns = ignoredColumns;
      params._response_column = response;
      return new GLRM(params).trainModel().get();
    } finally {
      if (f!=null) f.delete();
    }
  }
  
  private RuleFitModel prepareRuleFitModel(String dataset, String[] ignoredColumns, String response) {
    Frame f = parseTestFile(dataset);
    try {
      RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
      params._train = f._key;
      params._ignored_columns = ignoredColumns;
      params._response_column = response;
      return new RuleFit(params).trainModel().get();
    } finally {
      if (f!=null) f.delete();
    }
  }

  /** Dummy model to test model serialization */
  static class BlahModel extends Model<BlahModel, BlahModel.BlahParameters, BlahModel.BlahOutput> {
    public BlahModel(Key selfKey, BlahParameters params, BlahOutput output) { super(selfKey, params, output); }
    @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) { return null; }
    @Override protected double[] score0(double[] data, double[] preds) { return new double[0]; }
    static class BlahParameters extends Model.Parameters {
      public String algoName() { return "Blah"; }
      public String fullName() { return "Blah"; }
      public String javaName() { return BlahModel.class.getName(); }
      @Override public long progressUnits() { return 0; }
    }
    static class BlahOutput extends Model.Output {
      public BlahOutput(boolean hasWeights, boolean hasOffset, boolean hasFold) {
        super(hasWeights, hasOffset, hasFold);
      }
    }
  }

  // Serialize to and from a file
  private <M extends Model<?, ?, ?>> M saveAndLoad(M model, ModelExportOption... options) throws IOException {
    File file = File.createTempFile(model.getClass().getSimpleName(),null);
    try {
      String path = file.getAbsolutePath();
      model.exportBinaryModel(path, true, options);
      Log.info("Model export file size: " + file.length());
      model.delete();
      return Model.importBinaryModel(path);
    } finally {
      if (! file.delete())
        Log.err("Temporary file " + file + " was not deleted.");
    }
  }

  public static void assertModelBinaryEquals(Model a, Model b) {
    assertArrayEquals("The serialized models are not binary same!", a.write(new AutoBuffer()).buf(), b.write(new AutoBuffer()).buf());
  }

  public static void assertIcedBinaryEquals(String msg, Iced a, Iced b) {
    if (a == null) {
      assertNull(msg, b);
    } else {
      assertArrayEquals(msg, a.write(new AutoBuffer()).buf(), b.write(new AutoBuffer()).buf());
    }
  }

  public static void assertTreeEquals(String msg, CompressedTree[][] a, CompressedTree[][] b) {
    assertTreeEquals(msg, a, b, false);
  }

  public static void assertTreeEquals(String msg, CompressedTree[][] a, CompressedTree[][] b, boolean ignoreKeyField) {
    Assert.assertEquals("Number of trees has to match", a.length, b.length);
    for (int i = 0; i < a.length ; i++) {
      Assert.assertEquals("Number of trees per tree has to match", a[i].length, b[i].length);
      for (int j = 0; j < a[i].length; j++) {
        Key oldAKey = null;
        Key oldBKey = null;

        if (ignoreKeyField) {
          if (a[i][j] != null) {
            oldAKey = a[i][j]._key;
            a[i][j]._key = null;
          }
          if (b[i][j] != null) {
            oldBKey = b[i][j]._key;
            b[i][j]._key = null;
          }
        }
        assertIcedBinaryEquals(msg, a[i][j], b[i][j]);
        if (ignoreKeyField) {
          if (a[i][j] != null) {
            a[i][j]._key = oldAKey;
          }
          if (b[i][j] != null) {
            b[i][j]._key = oldBKey;
          }
        }
      }
    }
  }

  public static CompressedTree[][] getTrees(SharedTreeModel tm) {
    SharedTreeModel.SharedTreeOutput tmo = (SharedTreeModel.SharedTreeOutput) tm._output;
    int ntrees   = tmo._ntrees;
    int nclasses = tmo.nclasses();
    CompressedTree[][] result = new CompressedTree[ntrees][nclasses];

    for (int i = 0; i < ntrees; i++) {
      for (int j = 0; j < nclasses; j++) {
        if (tmo._treeKeys[i][j] != null)
          result[i][j] = tmo.ctree(i, j);
      }
    }

    return result;
  }
}
