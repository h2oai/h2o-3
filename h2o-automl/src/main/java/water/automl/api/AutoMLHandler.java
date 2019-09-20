package water.automl.api;

import ai.h2o.automl.AutoML;
import water.*;
import water.api.Handler;
import water.automl.api.schemas3.AutoMLV99;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OKeyNotFoundArgumentException;
import water.exceptions.H2OKeyWrongTypeArgumentException;

import java.util.stream.Stream;

public class AutoMLHandler extends Handler {

  @SuppressWarnings("unused") // called through reflection by RequestServer
  /** Return an AutoML object by ID. */
  public AutoMLV99 fetch(int version, AutoMLV99 autoMLV99) {
    AutoML autoML = DKV.getGet(autoMLV99.automl_id.name);
    if (autoML == null) {
      AutoML[] amls = fetchAllForProject(autoMLV99.automl_id.name);
      if (amls.length > 0) {
        autoML = Stream.of(amls).max(AutoML.byStartTime).get();
      }
    }
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

  public static AutoML[] fetchAllForProject(final String project_name) {
    final Key[] automlKeys = KeySnapshot.globalSnapshot().filter(new KeySnapshot.KVFilter() {
      @Override
      public boolean filter(KeySnapshot.KeyInfo k) {
        return Value.isSubclassOf(k._type, AutoML.class) && k._key.toString().startsWith(project_name+"@");
      }
    }).keys();
    AutoML[] amls = new AutoML[automlKeys.length];
    for (int i = 0; i < automlKeys.length; i++) {
      AutoML aml = getFromDKV("(none)", automlKeys[i]);
      amls[i] = aml;
    }
    return amls;
  }
}
