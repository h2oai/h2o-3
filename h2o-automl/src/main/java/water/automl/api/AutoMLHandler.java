package water.automl.api;

import ai.h2o.automl.AutoML;
import water.DKV;
import water.api.Handler;
import water.automl.api.schemas3.AutoMLV99;

public class AutoMLHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public AutoMLV99 fetch(int version, AutoMLV99 autoMLV99) {
    AutoML autoML = DKV.getGet(autoMLV99.automl_id.name);
    return autoMLV99.fillFromImpl(autoML);
  }
}