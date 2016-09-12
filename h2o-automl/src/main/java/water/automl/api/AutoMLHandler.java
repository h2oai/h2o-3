package water.automl.api;

import water.api.Handler;
import water.api.schemas3.KeyV3;
import water.automl.api.schemas3.AutoMLV3;


// essentially the AutoMLBuilderHandler
public class AutoMLHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public AutoMLV3 refresh(int version, AutoMLV3 args) {
    args.leader = new KeyV3.ModelKeyV3(args.automl_id.key().get().getLeaderKey());
    return args;
  }
}