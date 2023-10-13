package hex.ensemble;

import hex.Model;
import hex.deeplearning.DeepLearning;
import hex.deeplearning.DeepLearningModel;
import hex.glm.GLM;
import hex.glm.GLMModel.GLMParameters;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.apache.commons.lang.math.LongRange;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.vals.ValFrame;

import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StackedEnsembleSHAPTest extends TestUtil {
  
  /*
   NOTE: These test do not test all required properties for SHAP. 
   To be more sure after doing some changes to the SHAP, please run the python test:
   h2o-py/tests/testdir_misc/pyunit_SHAP_NOPASS.py 
  */

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }


  private List<Model> trainModels(Frame fr, String response) {
    LinkedList<Model> list = new LinkedList<>();
    int seed = 0xCAFFE;
    {
      GLMParameters params = new GLMParameters();
      params._train = fr._key;
      params._response_column = response;
      params._keep_cross_validation_predictions = true;
      params._nfolds = 5;
      params._seed = seed;
      list.add(new GLM(params).trainModel().get());
    }
    {
      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = fr._key;
      params._response_column = response;
      params._keep_cross_validation_predictions = true;
      params._nfolds = 5;
      params._ntrees = 5;
      params._seed = seed;
      list.add(new GBM(params).trainModel().get());
    }
    {
      DRFModel.DRFParameters params = new DRFModel.DRFParameters();
      params._train = fr._key;
      params._response_column = response;
      params._keep_cross_validation_predictions = true;
      params._nfolds = 5;
      params._ntrees = 5;
      params._seed = seed;
      list.add(new DRF(params).trainModel().get());
    }

    {
      DeepLearningModel.DeepLearningParameters params = new DeepLearningModel.DeepLearningParameters();
      params._train = fr._key;
      params._response_column = response;
      params._keep_cross_validation_predictions = true;
      params._nfolds = 5;
      params._hidden = ari(5, 5);
      params._epochs = 5;
      params._seed = seed;
      list.add(new DeepLearning(params).trainModel().get());
    }

    {
      StackedEnsembleModel.StackedEnsembleParameters params = new StackedEnsembleModel.StackedEnsembleParameters();
      params._train = fr._key;
      params._response_column = response;
      params._seed = seed;
      params._base_models = list.stream().map(Model::getKey).toArray(Key[]::new);
      list.add(new StackedEnsemble(params).trainModel().get());
    }

    return list;
  }

  @Test
  public void testClassificationCompactSHAP() {
    Frame fr = parseTestFile("smalldata/titanic/titanic_expanded.csv");
    Frame bgFr = fr.deepSlice(new LongRange(0, 50).toArray(), null);
    Frame test = fr.deepSlice(new LongRange(51, 101).toArray(), null);

    Frame scored = null;
    Frame contribs = null;
    Frame res = null;
    List<Model> models = trainModels(fr, "survived");
    try {
      StackedEnsembleModel model = (StackedEnsembleModel) models.get(models.size() - 1);

      assert model != null;
      scored = model.score(test);
      contribs = model.scoreContributions(test, Key.make(), null,
              new Model.Contributions.ContributionsOptions().setOutputFormat(Model.Contributions.ContributionsOutputFormat.Compact),
              bgFr);

      assert fr.numCols() >= contribs.numCols();

      // convert link space contribs to output space by using link inverse 
      Val val = Rapids.exec("(sumaxis " + contribs._key + " 0 1)");
      assertTrue(val instanceof ValFrame);
      res = val.getFrame();
      val = Rapids.exec("(, (tmp= expContrSum (exp " + res._key + ")) (/ expContrSum (+ expContrSum 1)))");
      res.delete();
      DKV.remove(Key.make("expContrSum"));
      assertTrue(val instanceof ValFrame);
      res = val.getFrame();
      assertColsEquals(scored, res, 2, 0, 1e-4);
    } finally {
      fr.delete();
      bgFr.delete();
      test.delete();
      if (null != res) res.delete();
      if (null != scored) scored.delete();
      if (null != contribs) contribs.delete();
      for (Model m : models)
        m.delete(true);
    }
  }


  @Test
  public void testSHAPDoesNotLeakWhenDifferentBaseModelColumnNames() {
    Frame fr = parseTestFile("smalldata/titanic/titanic_expanded.csv");
    Frame bgFr = fr.deepSlice(new LongRange(0, 50).toArray(), null);
    Frame test = fr.deepSlice(new LongRange(51, 101).toArray(), null);
    Frame scored = null;
    Frame contribs = null;
    Frame res = null;
    List<Model> models = trainModels(fr, "survived");
    try {
      StackedEnsembleModel model = (StackedEnsembleModel) models.get(models.size() - 1);

      assert model != null;
      scored = model.score(test);
      try {
        contribs = model.scoreContributions(test, Key.make(), null,
                new Model.Contributions.ContributionsOptions().setOutputFormat(Model.Contributions.ContributionsOutputFormat.Original),
                bgFr);
      } catch (IllegalArgumentException e) {
      }

    } finally {
      fr.delete();
      bgFr.delete();
      test.delete();
      if (null != res) res.delete();
      if (null != scored) scored.delete();
      if (null != contribs) contribs.delete();
      for (Model m : models)
        m.delete(true);
    }
  }


  @Test
  public void testClassificationCompactOutputSpaceSHAP() {
    Frame fr = parseTestFile("smalldata/titanic/titanic_expanded.csv");
    Frame bgFr = fr.deepSlice(new LongRange(0, 50).toArray(), null);
    Frame test = fr.deepSlice(new LongRange(51, 101).toArray(), null);
    Frame scored = null;
    Frame contribs = null;
    Frame res = null;
    List<Model> models = trainModels(fr, "survived");
    try {
      StackedEnsembleModel model = (StackedEnsembleModel) models.get(models.size() - 1);

      assert model != null;
      scored = model.score(test);
      contribs = model.scoreContributions(test, Key.make(), null,
              new Model.Contributions.ContributionsOptions()
                      .setOutputFormat(Model.Contributions.ContributionsOutputFormat.Compact)
                      .setOutputSpace(true),
              bgFr);

      assert fr.numCols() >= contribs.numCols();

      Val val = Rapids.exec("(sumaxis " + contribs._key + " 0 1)");
      assertTrue(val instanceof ValFrame);
      res = val.getFrame();
      assertColsEquals(scored, res, 2, 0, 1e-4);
    } finally {
      fr.delete();
      bgFr.delete();
      test.delete();
      if (null != res) res.delete();
      if (null != scored) scored.delete();
      if (null != contribs) contribs.delete();
      for (Model m : models)
        m.delete(true);
    }
  }


  @Test
  public void testRegressionCompactSHAP() {
    Frame fr = parseTestFile("smalldata/titanic/titanic_expanded.csv");
    Frame bgFr = fr.deepSlice(new LongRange(0, 50).toArray(), null);
    Frame test = fr.deepSlice(new LongRange(51, 101).toArray(), null);
    Frame scored = null;
    Frame contribs = null;
    Frame res = null;
    List<Model> models = trainModels(fr, "fare");
    try {
      StackedEnsembleModel model = (StackedEnsembleModel) models.get(models.size() - 1);

      assert model != null;
      scored = model.score(test);
      contribs = model.scoreContributions(test, Key.make(), null,
              new Model.Contributions.ContributionsOptions().setOutputFormat(Model.Contributions.ContributionsOutputFormat.Compact),
              bgFr);

      assert fr.numCols() >= contribs.numCols();

      Val val = Rapids.exec("(sumaxis " + contribs._key + " 0 1)");
      assertTrue(val instanceof ValFrame);
      res = val.getFrame();
      assertColsEquals(scored, res, 0, 0, 1e-4);
    } finally {
      fr.delete();
      bgFr.delete();
      test.delete();
      if (null != res) res.delete();
      if (null != scored) scored.delete();
      if (null != contribs) contribs.delete();
      for (Model m : models)
        m.delete(true);
    }
  }


  @Test
  public void testRegressionOriginalSHAP() {
    Frame fr = parseTestFile("smalldata/titanic/titanic_expanded.csv");
    Frame bgFr = fr.deepSlice(new LongRange(0, 50).toArray(), null);
    Frame test = fr.deepSlice(new LongRange(51, 101).toArray(), null);
    Frame scored = null;
    Frame contribs = null;
    Frame res = null;
    List<Model> models = trainModels(fr, "fare");
    try {
      StackedEnsembleModel model = (StackedEnsembleModel) models.get(models.size() - 1);

      assert model != null;
      scored = model.score(test);
      contribs = model.scoreContributions(test, Key.make(), null,
              new Model.Contributions.ContributionsOptions().setOutputFormat(Model.Contributions.ContributionsOutputFormat.Original),
              bgFr);

      assert fr.numCols() < contribs.numCols(); // Titanic has categorical vars

      Val val = Rapids.exec("(sumaxis " + contribs._key + " 0 1)");
      assertTrue(val instanceof ValFrame);
      res = val.getFrame();
      assertColsEquals(scored, res, 0, 0, 1e-4);
    } finally {
      fr.delete();
      bgFr.delete();
      test.delete();
      if (null != res) res.delete();
      if (null != scored) scored.delete();
      if (null != contribs) contribs.delete();
      for (Model m : models)
        m.delete(true);
    }
  }

  private static void assertColsEquals(Frame expected, Frame actual, int colExpected, int colActual, double eps) {
    assertEquals(expected.numRows(), actual.numRows());
    for (int i = 0; i < expected.numRows(); i++) {
      assertEquals("Wrong sum in row " + i, expected.vec(colExpected).at(i), actual.vec(colActual).at(i), eps);
    }
  }
}
