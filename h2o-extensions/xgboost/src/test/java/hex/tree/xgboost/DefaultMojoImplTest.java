package hex.tree.xgboost;

import hex.genmodel.MojoModel;
import hex.genmodel.MojoReaderBackend;
import hex.genmodel.MojoReaderBackendFactory;
import hex.genmodel.algos.xgboost.XGBoostJavaMojoModel;
import hex.tree.xgboost.XGBoostModel.XGBoostParameters.Booster;
import org.apache.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;

public class DefaultMojoImplTest extends TestUtil {

  private static final Logger LOG = Logger.getLogger(DefaultMojoImplTest.class);

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
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
      assertEquals(XGBoostJavaMojoModel.class.getName(), mojo.getClass().getName());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testGBLinear() throws IOException {
    Scope.enter();
    try {
      XGBoostModel model = trainModel(Booster.gblinear);
      MojoModel mojo = model.toMojo();
      assertEquals(XGBoostJavaMojoModel.class.getName(), mojo.getClass().getName());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testOldDARTMojoUsesNativeScoring() throws IOException {
    Scope.enter();
    try {
      InputStream oldMojo = getClass().getResourceAsStream("oldDart.mojo");
      MojoReaderBackend mojoReaderBackend = MojoReaderBackendFactory.createReaderBackend(
          oldMojo, MojoReaderBackendFactory.CachingStrategy.MEMORY
      );
      MojoModel mojo = MojoModel.load(mojoReaderBackend);
      assertEquals(XGBoostJavaMojoModel.class.getName(), mojo.getClass().getName());
    } finally {
      Scope.exit();
    }
  }


  private static XGBoostModel trainModel(Booster booster) {
    Frame tfr = parseTestFile("./smalldata/prostate/prostate.csv");
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
    LOG.info(model);
    return model;
  }

}
