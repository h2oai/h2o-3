package hex.tree.xgboost;

import hex.genmodel.MojoModel;
import hex.genmodel.MojoReaderBackend;
import hex.genmodel.MojoReaderBackendFactory;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DefaultMojoImplTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Before
  public void setupMojoJavaScoring() {
    System.clearProperty(XGBoostMojoReader.SCORE_JAVA_PROP); // force default behavior
    assertTrue(XGBoostMojoReader.useJavaScoring(true, null)); // check default value for MOJO scoring impl
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
      XGBoostModel model = trainModel(Booster.dart);

      try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
        new OldSerializationXGBMojoWriter(model).writeTo(os);
        MojoReaderBackend mojoReaderBackend = MojoReaderBackendFactory.createReaderBackend(
            new ByteArrayInputStream(os.toByteArray()), MojoReaderBackendFactory.CachingStrategy.MEMORY);
        MojoModel mojo = MojoModel.load(mojoReaderBackend);
        assertEquals(XGBoostNativeMojoModel.class.getName(), mojo.getClass().getName());
      }
    } finally {
      Scope.exit();
    }
  }


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

  /**
   * Old mojo writer that does not include use_java_scoring_by_default
   */
  private static final class OldSerializationXGBMojoWriter extends XGBoostMojoWriter {

    public OldSerializationXGBMojoWriter(XGBoostModel model) {
      super(model);
    }

    @Override
    protected void writeModelData() throws IOException {
      writeblob("boosterBytes", this.model.model_info()._boosterBytes);
      writekv("nums", model._output._nums);
      writekv("cats", model._output._cats);
      writekv("cat_offsets", model._output._catOffsets);
      writekv("use_all_factor_levels", model._output._useAllFactorLevels);
      writekv("sparse", model._output._sparse);
      writekv("booster", model._parms._booster.toString());
      writeblob("feature_map", model.model_info().getFeatureMap().getBytes(Charset.forName("UTF-8")));
    }

  }

}
