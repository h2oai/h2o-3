package water.automl;

import water.api.Handler;
import water.api.KeyV3;


// essentially the AutoMLBuilderHandler
public class AutoMLHandler extends Handler {
  public AutoMLV3 refresh(int version, AutoMLV3 args) {
    args.leader = new KeyV3.ModelKeyV3(args.automl_id.key().get().getLeaderKey());
    return args;
  }
}