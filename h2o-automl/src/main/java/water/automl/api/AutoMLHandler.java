package water.automl.api;

import ai.h2o.automl.AutoML;
import water.*;
import water.api.Handler;
import water.automl.api.schemas3.AutoMLV99;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OKeyNotFoundArgumentException;
import water.exceptions.H2OKeyWrongTypeArgumentException;

public class AutoMLHandler extends Handler {

  @SuppressWarnings("unused") // called through reflection by RequestServer
  /** Return an AutoML object by ID. */
  public AutoMLV99 fetch(int version, AutoMLV99 autoMLV99) {
    AutoML autoML = DKV.getGet(autoMLV99.automl_id.name);
    return autoMLV99.fillFromImpl(autoML);
  }

  // TODO: almost identical to ModelsHandler; refactor
  public static AutoML getFromDKV(String param_name, String key_str) {
    return getFromDKV(param_name, Key.make(key_str));
  }

  // TODO: almost identical to ModelsHandler; refactor
  public static AutoML getFromDKV(String param_name, Key key) {
    if (key == null)
      throw new H2OIllegalArgumentException(param_name, "AutoML.getFromDKV()", null);

    Value v = DKV.get(key);
    if (v == null)
      throw new H2OKeyNotFoundArgumentException(param_name, key.toString());

    Iced ice = v.get();
    if (! (ice instanceof AutoML))
      throw new H2OKeyWrongTypeArgumentException(param_name, key.toString(), AutoML.class, ice.getClass());

    return (AutoML) ice;
  }
}
