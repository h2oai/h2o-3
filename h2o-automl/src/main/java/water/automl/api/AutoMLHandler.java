package water.automl.api;

import ai.h2o.automl.AutoML;
import water.DKV;
import water.api.Handler;
import water.automl.api.schemas3.AutoMLV3;

public class AutoMLHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public AutoMLV3 fetch(int version, AutoMLV3 autoMLV3) {
    AutoML autoML = DKV.getGet(autoMLV3.automl_id.name);
    return autoMLV3.fillFromImpl(autoML);
  }
}