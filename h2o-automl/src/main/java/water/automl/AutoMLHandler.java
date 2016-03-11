package water.automl;

import ai.h2o.automl.AutoML;
import water.DKV;
import water.api.Handler;


// essentially the AutoMLBuilderHandler
public class AutoMLHandler extends Handler {
  public AutoMLV3 refresh(int version, AutoMLV3 args) {
    args.leader = ((AutoML)DKV.getGet(args.key)).getLeaderKey();
    return args;
  }
}