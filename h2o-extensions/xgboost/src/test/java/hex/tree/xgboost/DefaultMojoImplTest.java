package hex.tree.xgboost;

import hex.genmodel.MojoModel;
import hex.genmodel.algos.xgboost.XGBoostJavaMojoModel;
import hex.genmodel.algos.xgboost.XGBoostMojoReader;
import hex.genmodel.algos.xgboost.XGBoostNativeMojoModel;
import hex.tree.xgboost.XGBoostModel.XGBoostParameters.Booster;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.util.Log;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DefaultMojoImplTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Before
  public void setupMojoJavaScoring() {
    System.clearProperty(XGBoostMojoReader.SCORE_JAVA_PROP); // force default behavior

    assertNull(XGBoostMojoReader.getJavaScoringConfig()); // check that MOJO scoring config is not present
  }

  @Test
  public void testGBTree() throws IOException {
    Scope.enter();
    try {
      XGBoostModel model = trainModel(Booster.gbtree);
      MojoModel mojo = model.toMojo();
      assertEquals(XGBoostJavaMojoModel.class.getName(), mojo.getClass().getName());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testDART() throws IOException {
    Scope.enter();
    try {
      XGBoostModel model = trainModel(Booster.dart);
      MojoModel mojo = model.toMojo();
      assertEquals(XGBoostNativeMojoModel.class.getName(), mojo.getClass().getName());
    } finally {
      Scope.exit();
    }
  }

  /* FIXME: PUBDEV-6387: gblinear causes a segmenation fault
  @Test
  public void testGBLinear() throws IOException {
    Scope.enter();
    try {
      XGBoostModel model = trainModel(Booster.gblinear);
      MojoModel mojo = model.toMojo();
      assertEquals(XGBoostNativeMojoModel.class.getName(), mojo.getClass().getName());
    } finally {
      Scope.exit();
    }
  }
  */

  private static XGBoostModel trainModel(Booster booster) {
    Frame tfr = parse_test_file("./smalldata/prostate/prostate.csv");
    Scope.track(tfr);
    Scope.track(tfr.replace(1, tfr.vecs()[1].toCategoricalVec()));   // Convert CAPSULE to categorical
    Scope.track(tfr.replace(3, tfr.vecs()[3].toCategoricalVec()));   // Convert RACE to categorical
    DKV.put(tfr);

    XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
    parms._train = tfr._key;
    parms._response_column = "AGE";
    parms._ignored_columns = new String[]{"ID"};
    parms._booster = booster;
    if (! Booster.gblinear.equals(booster)) {
      parms._ntrees = 5;
    }

    XGBoostModel model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
    Scope.track_generic(model);
    Log.info(model);
    return model;
  }

}
