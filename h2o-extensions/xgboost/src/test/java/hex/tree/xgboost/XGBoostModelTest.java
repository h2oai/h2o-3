package hex.tree.xgboost;

import org.junit.Assert;
import org.junit.Test;
import water.H2O;

import java.util.Map;

import static org.junit.Assert.*;

public class XGBoostModelTest {

  @Test
  public void testCreateParamsNThreads() throws Exception {
    // default
    XGBoostModel.XGBoostParameters pDefault = new XGBoostModel.XGBoostParameters();
    pDefault._backend = XGBoostModel.XGBoostParameters.Backend.cpu; // to disable the GPU check
    BoosterParms bpDefault = XGBoostModel.createParams(pDefault, 2, null);
    assertEquals((int) H2O.ARGS.nthreads, bpDefault.get().get("nthread"));
    // user specified
    XGBoostModel.XGBoostParameters pUser = new XGBoostModel.XGBoostParameters();
    pUser._backend = XGBoostModel.XGBoostParameters.Backend.cpu; // to disable the GPU check
    pUser._nthread = H2O.ARGS.nthreads - 1;
    BoosterParms bpUser = XGBoostModel.createParams(pUser, 2, null);
    assertEquals((int) H2O.ARGS.nthreads - 1, bpUser.get().get("nthread"));
    // user specified (over the limit)
    XGBoostModel.XGBoostParameters pOver = new XGBoostModel.XGBoostParameters();
    pOver._backend = XGBoostModel.XGBoostParameters.Backend.cpu; // to disable the GPU check
    pOver._nthread = H2O.ARGS.nthreads + 1;
    BoosterParms bpOver = XGBoostModel.createParams(pOver, 2, null);
    assertEquals((int) H2O.ARGS.nthreads, bpOver.get().get("nthread"));
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
  public void gpuIncompatibleParametersGrowPolicy(){
    XGBoostModel.XGBoostParameters xgBoostParameters = new XGBoostModel.XGBoostParameters();
    xgBoostParameters._grow_policy = XGBoostModel.XGBoostParameters.GrowPolicy.lossguide;

    Map<String, Object> incompatibleParams = xgBoostParameters.gpuIncompatibleParams();
    assertEquals(incompatibleParams.size(), 1);
    assertEquals(incompatibleParams.get("grow_policy"), XGBoostModel.XGBoostParameters.GrowPolicy.lossguide);
  }

}
