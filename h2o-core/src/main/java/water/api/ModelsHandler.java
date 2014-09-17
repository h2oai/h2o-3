package water.api;

import hex.Model;
import water.*;
import water.fvec.Frame;
import water.api.FramesHandler.Frames;

import java.util.*;

class ModelsHandler extends Handler<ModelsHandler.Models, ModelsBase> {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  /** Class which contains the internal representation of the models list and params. */
  protected static final class Models extends Iced {
    public Key key;
    public Model[] models;
    public boolean find_compatible_frames = false;

    public static Model[] fetchAll() {
      final Key[] modelKeys = KeySnapshot.globalSnapshot().filter(new KeySnapshot.KVFilter() {
        @Override
        public boolean filter(KeySnapshot.KeyInfo k) {
          return Value.isSubclassOf(k._type, Model.class);
        }
      }).keys();

      Model[] models = new Model[modelKeys.length];
      for (int i = 0; i < modelKeys.length; i++) {
        Model model = getFromDKV(modelKeys[i]);
        models[i] = model;
      }

      return models;
    }

    /**
     * Fetch all the Frames so we can see if they are compatible with our Model(s).
     */
    protected Map<Frame, Set<String>> fetchFrameCols() {
      Frame[] all_frames = null;
      Map<Frame, Set<String>> all_frames_cols = null;

      if (this.find_compatible_frames) {
        // caches for this request
        all_frames = Frames.fetchAll();
        all_frames_cols = new HashMap<Frame, Set<String>>();

        for (Frame f : all_frames) {
          all_frames_cols.put(f, new HashSet<String>(Arrays.asList(f._names)));
        }
      }
      return all_frames_cols;
    }

    /**
     * For a given model return an array of the compatible frames.
     *
     * @param model The model to fetch the compatible frames for.
     * @param all_frames An array of all the Frames in the DKV.
     * @param all_frames_cols A Map of Frame to a Set of its column names.
     * @return
     */
    private static Frame[] findCompatibleFrames(Model model, Frame[] all_frames, Map<Frame, Set<String>> all_frames_cols) {
      List<Frame> compatible_frames = new ArrayList<Frame>();

      Set<String> model_column_names = new HashSet(Arrays.asList(model._output._names));

      for (Map.Entry<Frame, Set<String>> entry : all_frames_cols.entrySet()) {
        Frame frame = entry.getKey();
        Set<String> frame_cols = entry.getValue();

        if (frame_cols.containsAll(model_column_names)) {
          /// See if adapt throws an exception or not.
          try {
            Frame[] outputs = model.adapt(frame, false); // TODO: this does too much work; write canAdapt()
            Frame adapted = outputs[0];
            Frame trash = outputs[1];
            // adapted.delete();  // TODO: shouldn't we clean up adapted vecs?  But we can't delete() the frame as a whole. . .
            trash.delete();

            // A-Ok
            compatible_frames.add(frame);
          }
          catch (Exception e) {
            // skip
          }
        }
      }

      return compatible_frames.toArray(new Frame[0]);
    }
  }

  /** Return all the models. */
  public Schema list(int version, Models m) {
    m.models = Models.fetchAll();
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
  public Schema fetch(int version, Models m) {
    Model model = getFromDKV(m.key);
    m.models = new Model[1];
    m.models[0] = model;
    ModelsBase schema = this.schema(version).fillFromImpl(m);
    if (m.find_compatible_frames) {
      Frames compatible = new Frames();
      compatible.frames = Models.findCompatibleFrames(model, Frames.fetchAll(), m.fetchFrameCols());
      schema.models[0].compatible_frames = (new FramesHandler()).schema(version).fillFromImpl(compatible);
    }
    return schema;
  }

  // Remove an unlocked model.  Fails if model is in-use
  public Schema delete(int version, Models models) {
    Model model = getFromDKV(models.key);
    if (null == model)
      throw new IllegalArgumentException("Model key not found: " + models.key);
    model.delete();             // lock & remove
    // TODO: Hm, which Schema should we use here?  Surely not a hardwired InspectV1. . .
    InspectV1 s = new InspectV1();
    s.key = models.key;
    return s;
  }

  // Remove ALL an unlocked models.  Throws IAE for all deletes that failed
  // (perhaps because the Models were locked & in-use).
  public Schema deleteAll(int version, Models models) {
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

    // TODO: Hm, which Schema should we use here?  Surely not a hardwired InspectV1. . .
    InspectV1 s = new InspectV1();
    return s;
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
