package water.api;

import water.*;
import water.fvec.Frame;
import water.Model;

class ModelsHandler extends Handler<ModelsHandler, ModelsBase> {
  // TODO: handlers should return an object that has the result as well as the needed http headers including status code
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  Key key;
  Model[] models;

  // /2/Models backward compatibility
  protected void list_or_fetch() {
    //if (this.version != 2)
    //  throw H2O.fail("list_or_fetch should not be routed for version: " + this.version + " of route: " + this.route);

    if (null != key) {
      fetch();
    } else {
      list();
    }
  }

  protected void list() {
    final Key[] modelKeys = KeySnapshot.globalSnapshot().filter(new KeySnapshot.KVFilter() {
        @Override
        public boolean filter(KeySnapshot.KeyInfo k) {
          return Value.isSubclassOf(k._type, Model.class);
        }
      }).keys();

    models = new Model[modelKeys.length];
    for (int i = 0; i < modelKeys.length; i++) {
      Model model = getFromDKV(modelKeys[i]);
      models[i] = model;
    }
  }

  // TODO: almost identical to FramesHandler; refactor
  public static Model getFromDKV(String key_str) {
    return getFromDKV(Key.make(key_str));
  }

  // TODO: almost identical to FramesHandler; refactor
  public static Model getFromDKV(Key key) {
    if (null == key)
      throw new IllegalArgumentException("Got null key.");

    Value v = DKV.get(key);
    if (null == v)
      throw new IllegalArgumentException("Did not find key: " + key.toString());

    Iced ice = v.get();
    if (! (ice instanceof Model))
      throw new IllegalArgumentException("Expected a Model for key: " + key.toString() + "; got a: " + ice.getClass());

    return (Model)ice;
  }

  protected void fetch() {
    Model model = getFromDKV(key);
    models = new Model[1];
    models[0] = model;
  }

  @Override protected ModelsBase schema(int version) {
    switch (version) {
    case 2:   return new ModelsV2();
    case 3:   return new ModelsV3();
    default:  throw H2O.fail("Bad version for Models schema: " + version);
    }
  }

  // Need to stub this because it's required by H2OCountedCompleter:
  @Override public void compute2() { throw H2O.fail(); }

}
