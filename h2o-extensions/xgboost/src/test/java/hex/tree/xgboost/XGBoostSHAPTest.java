package hex.tree.xgboost;

import hex.Model;
import hex.tree.xgboost.XGBoost;
import hex.tree.xgboost.XGBoostModel.XGBoostParameters;
import org.apache.commons.lang.math.LongRange;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.parser.ParseDataset;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.vals.ValFrame;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class XGBoostSHAPTest extends TestUtil {
  
  /*
   NOTE: These test do not test all required properties for SHAP. 
   To be more sure after doing some changes to the SHAP, please run the python test:
   h2o-py/tests/testdir_misc/pyunit_SHAP_NOPASS.py 
  */

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }


  @Test
  public void testClassificationCompactSHAP() {
    NFSFileVec nfs = TestUtil.makeNfsFileVec("smalldata/titanic/titanic_expanded.csv");
    Frame fr = ParseDataset.parse(Key.make(), nfs._key);
    Frame bgFr = fr.deepSlice(new LongRange(0, 50).toArray(), null);
    Frame test = fr.deepSlice(new LongRange(51, 101).toArray(), null);
    XGBoostModel model = null;
    Frame scored = null;
    Frame contribs = null;
    Frame res = null;
    try {
      XGBoostParameters params = new XGBoostParameters();
      params._train = fr._key;
      params._response_column = "survived";

      model = new XGBoost(params).trainModel().get();

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
      assertColsEquals(scored, res, 2, 0, 1e-6);
    } finally {
      fr.delete();
      bgFr.delete();
      test.delete();
      if (null != res) res.delete();
      if (null != scored) scored.delete();
      if (null != contribs) contribs.delete();
      if (null != model) model.delete();
    }
  }


  @Test
  public void testClassificationOriginalSHAP() {
    NFSFileVec nfs = TestUtil.makeNfsFileVec("smalldata/titanic/titanic_expanded.csv");
    Frame fr = ParseDataset.parse(Key.make(), nfs._key);
    Frame bgFr = fr.deepSlice(new LongRange(0, 50).toArray(), null);
    Frame test = fr.deepSlice(new LongRange(51, 101).toArray(), null);
    XGBoostModel model = null;
    Frame scored = null;
    Frame contribs = null;
    Frame res = null;
    try {
      XGBoostParameters params = new XGBoostParameters();
      params._train = fr._key;
      params._response_column = "survived";

      model = new XGBoost(params).trainModel().get();

      assert model != null;
      scored = model.score(test);
      contribs = model.scoreContributions(test, Key.make(), null,
              new Model.Contributions.ContributionsOptions().setOutputFormat(Model.Contributions.ContributionsOutputFormat.Original),
              bgFr);

      assert fr.numCols() < contribs.numCols();  // Titanic has categorical vars

      // convert link space contribs to output space by using link inverse 
      Val val = Rapids.exec("(sumaxis " + contribs._key + " 0 1)");
      assertTrue(val instanceof ValFrame);
      res = val.getFrame();
      val = Rapids.exec("(, (tmp= expContrSum (exp " + res._key + ")) (/ expContrSum (+ expContrSum 1)))");
      res.delete();
      DKV.remove(Key.make("expContrSum"));
      assertTrue(val instanceof ValFrame);
      res = val.getFrame();
      assertColsEquals(scored, res, 2, 0, 1e-6);
    } finally {
      fr.delete();
      bgFr.delete();
      test.delete();
      if (null != res) res.delete();
      if (null != scored) scored.delete();
      if (null != contribs) contribs.delete();
      if (null != model) model.delete();
    }
  }


  @Test
  public void testClassificationCompactOutputSpaceSHAP() {
    NFSFileVec nfs = TestUtil.makeNfsFileVec("smalldata/titanic/titanic_expanded.csv");
    Frame fr = ParseDataset.parse(Key.make(), nfs._key);
    Frame bgFr = fr.deepSlice(new LongRange(0, 50).toArray(), null);
    Frame test = fr.deepSlice(new LongRange(51, 101).toArray(), null);
    XGBoostModel model = null;
    Frame scored = null;
    Frame contribs = null;
    Frame res = null;
    try {
      XGBoostParameters params = new XGBoostParameters();
      params._train = fr._key;
      params._response_column = "survived";

      model = new XGBoost(params).trainModel().get();

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
      assertColsEquals(scored, res, 2, 0, 1e-6);
    } finally {
      fr.delete();
      bgFr.delete();
      test.delete();
      if (null != res) res.delete();
      if (null != scored) scored.delete();
      if (null != contribs) contribs.delete();
      if (null != model) model.delete();
    }
  }


  @Test
  public void testClassificationOriginalOutputSpaceSHAP() {
    NFSFileVec nfs = TestUtil.makeNfsFileVec("smalldata/titanic/titanic_expanded.csv");
    Frame fr = ParseDataset.parse(Key.make(), nfs._key);
    Frame bgFr = fr.deepSlice(new LongRange(0, 50).toArray(), null);
    Frame test = fr.deepSlice(new LongRange(51, 101).toArray(), null);
    XGBoostModel model = null;
    Frame scored = null;
    Frame contribs = null;
    Frame res = null;
    try {
      XGBoostParameters params = new XGBoostParameters();
      params._train = fr._key;
      params._response_column = "survived";

      model = new XGBoost(params).trainModel().get();

      assert model != null;
      scored = model.score(test);
      contribs = model.scoreContributions(test, Key.make(), null,
              new Model.Contributions.ContributionsOptions()
                      .setOutputFormat(Model.Contributions.ContributionsOutputFormat.Original)
                      .setOutputSpace(true),
              bgFr);

      assert fr.numCols() < contribs.numCols();  // Titanic has categorical vars

      Val val = Rapids.exec("(sumaxis " + contribs._key + " 0 1)");
      assertTrue(val instanceof ValFrame);
      res = val.getFrame();
      assertColsEquals(scored, res, 2, 0, 1e-6);
    } finally {
      fr.delete();
      bgFr.delete();
      test.delete();
      if (null != res) res.delete();
      if (null != scored) scored.delete();
      if (null != contribs) contribs.delete();
      if (null != model) model.delete();
    }
  }


  @Test
  public void testRegressionCompactSHAP() {
    NFSFileVec nfs = TestUtil.makeNfsFileVec("smalldata/titanic/titanic_expanded.csv");
    Frame fr = ParseDataset.parse(Key.make(), nfs._key);
    Frame bgFr = fr.deepSlice(new LongRange(0, 50).toArray(), null);
    Frame test = fr.deepSlice(new LongRange(51, 101).toArray(), null);
    XGBoostModel model = null;
    Frame scored = null;
    Frame contribs = null;
    Frame res = null;
    try {
      XGBoostParameters params = new XGBoostParameters();
      params._train = fr._key;
      params._response_column = "fare";

      model = new XGBoost(params).trainModel().get();

      assert model != null;
      scored = model.score(test);
      contribs = model.scoreContributions(test, Key.make(), null,
              new Model.Contributions.ContributionsOptions().setOutputFormat(Model.Contributions.ContributionsOutputFormat.Compact),
              bgFr);

      assert fr.numCols() >= contribs.numCols();

      Val val = Rapids.exec("(sumaxis " + contribs._key + " 0 1)");
      assertTrue(val instanceof ValFrame);
      res = val.getFrame();
      assertColsEquals(scored, res, 0, 0, 5e-4);
    } finally {
      fr.delete();
      bgFr.delete();
      test.delete();
      if (null != res) res.delete();
      if (null != scored) scored.delete();
      if (null != contribs) contribs.delete();
      if (null != model) model.delete();
    }
  }


  @Test
  public void testRegressionOriginalSHAP() {
    NFSFileVec nfs = TestUtil.makeNfsFileVec("smalldata/titanic/titanic_expanded.csv");
    Frame fr = ParseDataset.parse(Key.make(), nfs._key);
    Frame bgFr = fr.deepSlice(new LongRange(0, 50).toArray(), null);
    Frame test = fr.deepSlice(new LongRange(51, 101).toArray(), null);
    XGBoostModel model = null;
    Frame scored = null;
    Frame contribs = null;
    Frame res = null;
    try {
      XGBoostParameters params = new XGBoostParameters();
      params._train = fr._key;
      params._response_column = "fare";

      model = new XGBoost(params).trainModel().get();

      assert model != null;
      scored = model.score(test);
      contribs = model.scoreContributions(test, Key.make(), null,
              new Model.Contributions.ContributionsOptions().setOutputFormat(Model.Contributions.ContributionsOutputFormat.Original),
              bgFr);

      assert fr.numCols() < contribs.numCols(); // Titanic has categorical vars

      Val val = Rapids.exec("(sumaxis " + contribs._key + " 0 1)");
      assertTrue(val instanceof ValFrame);
      res = val.getFrame();
      assertColsEquals(scored, res, 0, 0, 5e-4);
    } finally {
      fr.delete();
      bgFr.delete();
      test.delete();
      if (null != res) res.delete();
      if (null != scored) scored.delete();
      if (null != contribs) contribs.delete();
      if (null != model) model.delete();
    }
  }

  private static void assertColsEquals(Frame expected, Frame actual, int colExpected, int colActual, double eps) {
    assertEquals(expected.numRows(), actual.numRows());
    for (int i = 0; i < expected.numRows(); i++) {
      assertEquals("Wrong sum in row " + i, expected.vec(colExpected).at(i), actual.vec(colActual).at(i), eps);
    }
  }
}
