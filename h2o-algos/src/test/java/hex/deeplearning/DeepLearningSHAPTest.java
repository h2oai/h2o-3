package hex.deeplearning;

import hex.DataInfo;
import hex.Model;
import hex.ModelBuilder;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters;
import org.apache.commons.lang.math.LongRange;
import org.junit.BeforeClass;
import org.junit.Test;
import water.H2O;
import water.Key;
import water.TestUtil;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.util.FrameUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DeepLearningSHAPTest extends TestUtil {
  
  /*
   NOTE: These test do not test all required properties for SHAP. 
   To be more sure after doing some changes to the SHAP, please run the python test:
   h2o-py/tests/testdir_misc/pyunit_SHAP_NOPASS.py 
  */

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }


  String toPyTorch(DeepLearningModel model, Frame fr, Frame bg) {
    //used just for manually generating tests
    StringBuilder sb = new StringBuilder();
    sb.append("nn = torch.nn.Sequential(\n");
    for (int i = 0; i < model._parms._hidden.length; i++) {
      sb.append("    torch.nn.Linear("+model.model_info().get_weights(i).cols()+", "+model.model_info().get_weights(i).rows()+"),\n");
      switch(model._parms._activation) {
        case Tanh:
          sb.append("    torch.nn.Tanh(),\n");
          break;
        case Rectifier:
          sb.append("    torch.nn.ReLU(),\n");
          break;
        default:
          H2O.unimpl();
      }
    }
    sb.append("    torch.nn.Linear("+model.model_info().get_weights(model._parms._hidden.length).cols()+
            ", "+model.model_info().get_weights(model._parms._hidden.length).rows()+"),\n");
    if (model.model_info().data_info._normRespMul != null)
      sb.append("    torch.nn.Linear(1, 1)\n");
    sb.append(")\n\n");

    for (int i = 0; i <= model._parms._hidden.length; i++) {
      String weights = "";
      String biases = "[";
      weights += "[";
      for (int k = 0; k < model.model_info().get_weights(i).rows(); k++) {
        weights += "[";
        for (int l = 0; l < model.model_info().get_weights(i).cols(); l++) {
          weights += model.model_info().get_weights(i).get(k, l) + ",";
        }
        weights += "],";
        biases += model.model_info().get_biases(i).get(k) +", ";
      }
      weights +="]";
      biases +="]";


      sb.append("nn["+(2*i)+"].weight.data = torch.tensor("+weights+", dtype=torch.float32)\n");
      sb.append("nn["+(2*i)+"].bias.data = torch.tensor("+biases+", dtype=torch.float32)\n");
    }

    if (model.model_info().data_info._normRespMul != null) {
      sb.append("nn[" + (2 * model._parms._hidden.length + 1) + "].weight.data = torch.tensor([[" + (1. / model.model_info().data_info._normRespMul[0]) + "]], dtype=torch.float32)\n");
      sb.append("nn[" + (2 * model._parms._hidden.length + 1) + "].bias.data = torch.tensor([" + (model.model_info().data_info._normRespSub[0]) + "], dtype=torch.float32)\n");
    }
    sb.append("\n");
    sb.append("bg = [\n");
    DataInfo.Row row = model.model_info().data_info().newDenseRow();
    for (int i = 0; i < bg.anyVec().nChunks(); i++) {
      Chunk[] cs = FrameUtils.extractChunks(bg, i, false);
      for (int j = 0; j < cs[0]._len; j++) {
        model.model_info().data_info().extractDenseRow(cs, j, row);
        sb.append("    torch.tensor([[");
        for (int k = 0; k < model.model_info().data_info().fullN() ; k++) {
          sb.append(row.get(k)+", ");
        }
        sb.append("]], dtype=torch.float32),\n");
        }
    }
    sb.append("]\n\n");
    sb.append("x = [\n");
    row = model.model_info().data_info().newDenseRow();
    for (int i = 0; i < fr.anyVec().nChunks(); i++) {
      Chunk[] cs = FrameUtils.extractChunks(fr, i, false);
      for (int j = 0; j < cs[0]._len; j++) {
        model.model_info().data_info().extractDenseRow(cs, j, row);
        sb.append("    torch.tensor([[");
        for (int k = 0; k < model.model_info().data_info().fullN() ; k++) {
          sb.append(row.get(k)+", ");
        }
        sb.append("]], dtype=torch.float32),\n");
      }
    }
    sb.append("]\n\n");
    
    return sb.toString();
  }

  @Test
  public void testClassificationCompactSHAP() {
    Frame fr = parseTestFile("smalldata/titanic/titanic_expanded.csv");
    Frame bgFr = fr.deepSlice(new LongRange(0, 50).toArray(), null);
    Frame test = fr.deepSlice(new LongRange(51, 101).toArray(), null);
    DeepLearningModel model = null;
    Frame scored = null;
    Frame contribs = null;
    Frame res = null;
    try {
      // Launch Deep Learning
      DeepLearningParameters params = new DeepLearningParameters();
      params._train = fr._key;
      params._epochs = 5;
      params._response_column = "survived";
      params._hidden = ari(5,5);

      model = new DeepLearning(params).trainModel().get();

      assert model != null;
      scored = model.score(test);
      contribs = model.scoreContributions(test, Key.make(), null,
              new Model.Contributions.ContributionsOptions().setOutputFormat(Model.Contributions.ContributionsOutputFormat.Compact),
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
  public void testClassificationOriginalSHAP() {
    Frame fr = parseTestFile("smalldata/titanic/titanic_expanded.csv");
    Frame bgFr = fr.deepSlice(new LongRange(0, 50).toArray(), null);
    Frame test = fr.deepSlice(new LongRange(51, 101).toArray(), null);
    DeepLearningModel model = null;
    Frame scored = null;
    Frame contribs = null;
    Frame res = null;
    try {
      // Launch Deep Learning
      DeepLearningParameters params = new DeepLearningParameters();
      params._train = fr._key;
      params._epochs = 5;
      params._response_column = "survived";
      params._hidden = ari(5,5);

      model = new DeepLearning(params).trainModel().get();

      assert model != null;
      scored = model.score(test);
      contribs = model.scoreContributions(test, Key.make(), null,
              new Model.Contributions.ContributionsOptions().setOutputFormat(Model.Contributions.ContributionsOutputFormat.Original),
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
    Frame fr = parseTestFile("smalldata/titanic/titanic_expanded.csv");
    Frame bgFr = fr.deepSlice(new LongRange(0, 50).toArray(), null);
    Frame test = fr.deepSlice(new LongRange(51, 101).toArray(), null);
    DeepLearningModel model = null;
    Frame scored = null;
    Frame contribs = null;
    Frame res = null;
    try {
      // Launch Deep Learning
      DeepLearningParameters params = new DeepLearningParameters();
      params._train = fr._key;
      params._epochs = 5;
      params._response_column = "fare";
      params._hidden = ari(5,5);

      model = new DeepLearning(params).trainModel().get();

      assert model != null;
      scored = model.score(test);
      contribs = model.scoreContributions(test, Key.make(), null,
              new Model.Contributions.ContributionsOptions().setOutputFormat(Model.Contributions.ContributionsOutputFormat.Compact),
              bgFr);

      assert fr.numCols() >= contribs.numCols();

      Val val = Rapids.exec("(sumaxis " + contribs._key + " 0 1)");
      assertTrue(val instanceof ValFrame);
      res = val.getFrame();
      assertColsEquals(scored, res, 0, 0, 1e-5);
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
    Frame fr = parseTestFile("smalldata/titanic/titanic_expanded.csv");
    Frame bgFr = fr.deepSlice(new LongRange(0, 50).toArray(), null);
    Frame test = fr.deepSlice(new LongRange(51, 101).toArray(), null);
    DeepLearningModel model = null;
    Frame scored = null;
    Frame contribs = null;
    Frame res = null;
    try {
      // Launch Deep Learning
      DeepLearningParameters params = new DeepLearningParameters();
      params._train = fr._key;
      params._epochs = 5;
      params._response_column = "fare";
      params._hidden = ari(5,5);

      model = new DeepLearning(params).trainModel().get();

      assert model != null;
      scored = model.score(test);
      contribs = model.scoreContributions(test, Key.make(), null,
              new Model.Contributions.ContributionsOptions().setOutputFormat(Model.Contributions.ContributionsOutputFormat.Original),
              bgFr);

      assert fr.numCols() < contribs.numCols(); // Titanic has categorical vars

      Val val = Rapids.exec("(sumaxis " + contribs._key + " 0 1)");
      assertTrue(val instanceof ValFrame);
      res = val.getFrame();
      assertColsEquals(scored, res, 0, 0, 1e-5);
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
