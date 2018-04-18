package hex.tree.xgboost;

import org.junit.Test;
import water.H2O;

import static org.junit.Assert.*;

public class XGBoostModelTest {

  @Test
  public void testCreateParamsNThreads() throws Exception {
    // default
    XGBoostModel.XGBoostParameters pDefault = new XGBoostModel.XGBoostParameters();
    pDefault._backend = XGBoostModel.XGBoostParameters.Backend.cpu; // to disable the GPU check
    BoosterParms bpDefault = XGBoostModel.createParams(pDefault, 2);
    assertEquals((int) H2O.ARGS.nthreads, bpDefault.get().get("nthread"));
    // user specified
    XGBoostModel.XGBoostParameters pUser = new XGBoostModel.XGBoostParameters();
    pUser._backend = XGBoostModel.XGBoostParameters.Backend.cpu; // to disable the GPU check
    pUser._nthread = H2O.ARGS.nthreads - 1;
    BoosterParms bpUser = XGBoostModel.createParams(pUser, 2);
    assertEquals((int) H2O.ARGS.nthreads - 1, bpUser.get().get("nthread"));
    // user specified (over the limit)
    XGBoostModel.XGBoostParameters pOver = new XGBoostModel.XGBoostParameters();
    pOver._backend = XGBoostModel.XGBoostParameters.Backend.cpu; // to disable the GPU check
    pOver._nthread = H2O.ARGS.nthreads + 1;
    BoosterParms bpOver = XGBoostModel.createParams(pOver, 2);
    assertEquals((int) H2O.ARGS.nthreads, bpOver.get().get("nthread"));
  }

}