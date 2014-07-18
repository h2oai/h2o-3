package water.api;

import water.*;
import water.Model;

class ModelsHandler extends Handler<ModelsHandler.Models, ModelsBase> {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  /** Class which contains the internal representation of the models list and params. */
  protected static final class Models extends Iced {
    Key key;
    Model[] models;
  }

  /** Return all the models. */
  protected Schema list(int version, Models m) {
    final Key[] modelKeys = KeySnapshot.globalSnapshot().filter(new KeySnapshot.KVFilter() {
        @Override
        public boolean filter(KeySnapshot.KeyInfo k) {
          return Value.isSubclassOf(k._type, Model.class);
        }
      }).keys();

    m.models = new Model[modelKeys.length];
    for (int i = 0; i < modelKeys.length; i++) {
      Model model = getFromDKV(modelKeys[i]);
      m.models[i] = model;
    }
    return this.schema(version).fillFromImpl(m);
  }

  // TODO: almost identical to ModelsHandler; refactor
  public static Model getFromDKV(String key_str) {
    return getFromDKV(Key.make(key_str));
  }

  // TODO: almost identical to ModelsHandler; refactor
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

  /** Return a single model. */
  protected Schema fetch(int version, Models m) {
    Model model = getFromDKV(m.key);
    m.models = new Model[1];
    m.models[0] = model;
    return this.schema(version).fillFromImpl(m);
  }

  // Remove an unlocked model.  Fails if model is in-use
  protected void delete(int version, Models models) {
    Model model = getFromDKV(models.key);
    model.delete();             // lock & remove
  }

  // Remove ALL an unlocked models.  Throws IAE for all deletes that failed
  // (perhaps because the Models were locked & in-use).
  protected void deleteAll(int version, Models models) {
    final Key[] modelKeys = KeySnapshot.globalSnapshot().filter(new KeySnapshot.KVFilter() {
        @Override public boolean filter(KeySnapshot.KeyInfo k) {
          return Value.isSubclassOf(k._type, Model.class);
        }
      }).keys();

    String err=null;
    Futures fs = new Futures();
    for( int i = 0; i < modelKeys.length; i++ ) {
      try {
        getFromDKV(modelKeys[i]).delete(null,fs);
      } catch( IllegalArgumentException iae ) {
        err += iae.getMessage();
      }
    }
    fs.blockForPending();
    if( err != null ) throw new IllegalArgumentException(err);
  }


  @Override protected ModelsBase schema(int version) {
    switch (version) {
      // TODO: remove this hack; needed because of the frameChoices hack in RequestServer.  Ugh.
    case 2:   return new ModelsV3();
    case 3:   return new ModelsV3();
    default:  throw H2O.fail("Bad version for Models schema: " + version);
    }
  }

  // Need to stub this because it's required by H2OCountedCompleter:
  @Override public void compute2() { throw H2O.fail(); }

}
