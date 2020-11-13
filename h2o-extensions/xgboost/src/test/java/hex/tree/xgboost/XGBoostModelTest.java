package hex.tree.xgboost;

import hex.Model;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.H2O;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.Map;

import static org.junit.Assert.assertEquals;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class XGBoostModelTest {
  
  @Test
  public void testCreateParamsNThreads() {
    int maxNThreads = XGBoostModel.getMaxNThread();
    // default
    XGBoostModel.XGBoostParameters pDefault = new XGBoostModel.XGBoostParameters();
    pDefault._backend = XGBoostModel.XGBoostParameters.Backend.cpu; // to disable the GPU check
    BoosterParms bpDefault = XGBoostModel.createParams(pDefault, 2, null);
    assertEquals(maxNThreads, bpDefault.get().get("nthread"));
    // user specified
    XGBoostModel.XGBoostParameters pUser = new XGBoostModel.XGBoostParameters();
    pUser._backend = XGBoostModel.XGBoostParameters.Backend.cpu; // to disable the GPU check
    pUser._nthread = maxNThreads - 1;
    BoosterParms bpUser = XGBoostModel.createParams(pUser, 2, null);
    assertEquals(maxNThreads - 1, bpUser.get().get("nthread"));
    // user specified (over the limit)
    XGBoostModel.XGBoostParameters pOver = new XGBoostModel.XGBoostParameters();
    pOver._backend = XGBoostModel.XGBoostParameters.Backend.cpu; // to disable the GPU check
    pOver._nthread = H2O.ARGS.nthreads + 1;
    BoosterParms bpOver = XGBoostModel.createParams(pOver, 2, null);
    assertEquals(maxNThreads, bpOver.get().get("nthread"));
  }

  @Test
  public void gpuIncompatibleParametersMaxDepth(){
    XGBoostModel.XGBoostParameters xgBoostParameters = new XGBoostModel.XGBoostParameters();
    xgBoostParameters._max_depth = 16;

    Map<String, Object> incompatibleParams = xgBoostParameters.gpuIncompatibleParams();
    assertEquals(incompatibleParams.size(), 1);
    assertEquals(incompatibleParams.get("max_depth"), 16 + " . Max depth must be greater than 0 and lower than 16 for GPU backend.");

    xgBoostParameters._max_depth = 0;
    incompatibleParams = xgBoostParameters.gpuIncompatibleParams();
    assertEquals(incompatibleParams.size(), 1);
    assertEquals(incompatibleParams.get("max_depth"), 0 + " . Max depth must be greater than 0 and lower than 16 for GPU backend.");
  }


  @Test
  public void gpuIncompatibleParametersGrowPolicy() {
    XGBoostModel.XGBoostParameters xgBoostParameters = new XGBoostModel.XGBoostParameters();
    xgBoostParameters._grow_policy = XGBoostModel.XGBoostParameters.GrowPolicy.lossguide;

    Map<String, Object> incompatibleParams = xgBoostParameters.gpuIncompatibleParams();
    assertEquals(incompatibleParams.size(), 1);
    assertEquals(incompatibleParams.get("grow_policy"), XGBoostModel.XGBoostParameters.GrowPolicy.lossguide);
  }

  @Test
  public void testCrossValidationWithWeights() {
    Scope.enter();
    try {
      final Frame airlinesFrame = Scope.track(TestUtil.parse_test_file("./smalldata/testng/airlines.csv"));
      airlinesFrame.replace(0, airlinesFrame.vecs()[0].toCategoricalVec()).remove();

      final Vec weightsVector = TestUtil.createRandomBinaryWeightsVec(airlinesFrame.numRows(), 0xFEED);
      final String weightsColumnName = "weights";
      airlinesFrame.add(weightsColumnName, weightsVector);
      DKV.put(airlinesFrame);

      final XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._dmatrix_type = XGBoostModel.XGBoostParameters.DMatrixType.auto;
      parms._response_column = "IsDepDelayed";
      parms._train = airlinesFrame._key;
      parms._backend = XGBoostModel.XGBoostParameters.Backend.cpu;
      parms._weights_column = weightsColumnName;
      parms._nfolds = 5;
      parms._keep_cross_validation_models = true;
      parms._ignored_columns = new String[]{"fYear", "fMonth", "fDayofMonth", "fDayOfWeek"};

      final XGBoostModel model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      Scope.track_generic(model);
      assertEquals(5, model._output._cross_validation_models.length);

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testIncludeInteractionConstraints() {
    Scope.enter();
    try {
      final Frame airlinesFrame = Scope.track(TestUtil.parse_test_file("./smalldata/testng/airlines.csv"));
      airlinesFrame.replace(0, airlinesFrame.vecs()[0].toCategoricalVec()).remove();
      
      DKV.put(airlinesFrame);
      final XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
      parms._dmatrix_type = XGBoostModel.XGBoostParameters.DMatrixType.auto;
      parms._response_column = "IsDepDelayed";
      parms._train = airlinesFrame._key;
      parms._backend = XGBoostModel.XGBoostParameters.Backend.cpu;
      parms._interaction_constraints = new String[][]{new String[]{"fYear", "fMonth"}, new String[]{"Origin", "Distance"}};
      parms._tree_method = XGBoostModel.XGBoostParameters.TreeMethod.hist;
      parms._categorical_encoding = Model.Parameters.CategoricalEncodingScheme.AUTO;
      parms._ntrees = 5;

      final XGBoostModel model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
      Scope.track_generic(model);

    } finally {
      Scope.exit();
    }
  }
}
