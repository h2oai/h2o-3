package water;

import hex.tree.isofor.IsolationForest;
import hex.tree.isofor.IsolationForestModel;
import org.junit.Assert;
import org.junit.BeforeClass;
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
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.Log;

import static org.junit.Assert.assertArrayEquals;

public class ModelSerializationTest extends TestUtil {

  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  private static String[] ESA = new String[] {};

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
      model = prepareGBMModel("smalldata/iris/iris.csv", ESA, "C5", true, 5);
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
      model = prepareGBMModel("smalldata/logreg/prostate.csv", ar("ID"), "CAPSULE", true, 5);
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
  public void testDRFModelMultinomial() throws IOException {
    DRFModel model, loadedModel = null;
    try {
      model = prepareDRFModel("smalldata/iris/iris.csv", ESA, "C5", true, 5);
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
  public void testGLMModel() throws IOException {
    GLMModel model, loadedModel = null;
    try {
      model = prepareGLMModel("smalldata/junit/cars.csv", ESA, "power (hp)", GLMModel.GLMParameters.Family.poisson);
      loadedModel = saveAndLoad(model);
      assertModelBinaryEquals(model, loadedModel);
    } finally {
      if (loadedModel!=null) loadedModel.delete();
    }
  }

  private GBMModel prepareGBMModel(String dataset, String[] ignoredColumns, String response, boolean classification, int ntrees) {
    Frame f = parse_test_file(dataset);
    try {
      if (classification && !f.vec(response).isCategorical()) {
        f.replace(f.find(response), f.vec(response).toCategoricalVec()).remove();
        DKV.put(f._key, f);
      }
      GBMModel.GBMParameters gbmParams = new GBMModel.GBMParameters();
      gbmParams._train = f._key;
      gbmParams._ignored_columns = ignoredColumns;
      gbmParams._response_column = response;
      gbmParams._ntrees = ntrees;
      gbmParams._score_each_iteration = true;
      return new GBM(gbmParams).trainModel().get();
    } finally {
      if (f!=null) f.delete();
    }
  }

  private DRFModel prepareDRFModel(String dataset, String[] ignoredColumns, String response, boolean classification, int ntrees) {
    Frame f = parse_test_file(dataset);
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
    Frame f = parse_test_file(dataset);
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

  private GLMModel prepareGLMModel(String dataset, String[] ignoredColumns, String response, GLMModel.GLMParameters.Family family) {
    Frame f = parse_test_file(dataset);
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

  private <M extends Model> M saveAndLoad(M model) throws IOException {
    return saveAndLoad(model,true);
  }
  // Serialize to and from a file
  private <M extends Model<?, ?, ?>> M saveAndLoad(M model, boolean deleteModel) throws IOException {
    File file = File.createTempFile(model.getClass().getSimpleName(),null);
    try {
      String path = file.getAbsolutePath();
      model.exportBinaryModel(path, true);
      if( deleteModel ) model.delete();
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
      Assert.assertEquals(msg, null, b);
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
