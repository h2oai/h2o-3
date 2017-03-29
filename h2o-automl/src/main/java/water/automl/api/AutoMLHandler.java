package water.automl.api;

import ai.h2o.automl.AutoML;
import water.*;
import water.api.Handler;
import water.automl.api.schemas3.AutoMLV99;
import water.automl.api.schemas3.AutoMLsV99;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OKeyNotFoundArgumentException;
import water.exceptions.H2OKeyWrongTypeArgumentException;

public class AutoMLHandler extends Handler {
  /** Class which contains the internal representation of the leaderboards list and params. */
  public static final class AutoMLs extends Iced {
    public AutoML[] auto_ml_runs;

    public static AutoML[] fetchAll() {
      final Key<AutoML>[] autoMLKeys = KeySnapshot.globalSnapshot().filter(new KeySnapshot.KVFilter() {
        @Override
        public boolean filter(KeySnapshot.KeyInfo k) {
          return Value.isSubclassOf(k._type, AutoML.class);
        }
      }).keys();

      AutoML[] autoMLs = new AutoML[autoMLKeys.length];
      for (int i = 0; i < autoMLKeys.length; i++) {
        AutoML autoML = getFromDKV("(none)", autoMLKeys[i]);
        autoMLs[i] = autoML;
      }

      return autoMLs;
    }

  } // public class AutoMLs

  @SuppressWarnings("unused") // called through reflection by RequestServer
  /** Return an AutoML object by ID. */
  public AutoMLV99 fetch(int version, AutoMLV99 autoMLV99) {
    AutoML autoML = DKV.getGet(autoMLV99.automl_id.name);
    return autoMLV99.fillFromImpl(autoML);
  }

  /** Return all the AutoML objects. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public AutoMLsV99 list(int version, AutoMLsV99 s) {
    AutoMLs m = s.createAndFillImpl();
    m.auto_ml_runs = AutoMLs.fetchAll();
    return s.fillFromImpl(m);
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
