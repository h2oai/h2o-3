package water.api;

import hex.Model;
import water.*;
import water.api.ModelsHandler.Models;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.*;

class FramesHandler extends Handler<FramesHandler.Frames, FramesBase> {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  /** Class which contains the internal representation of the frames list and params. */
  protected static final class Frames extends Iced {
    Key key;
    Frame[] frames;
    String column;
    public boolean find_compatible_models = false;

    /**
     * Fetch all Frames from the KV store.
     */
    protected static Frame[] fetchAll() {
      // Get all the frames.
      final Key[] frameKeys = KeySnapshot.globalSnapshot().filter(new KeySnapshot.KVFilter() {
        @Override
        public boolean filter(KeySnapshot.KeyInfo k) {
          return k.isSubclassOf(Frame.class);
        }
      }).keys();
      Frame[] frames = new Frame[frameKeys.length];
      for (int i = 0; i < frameKeys.length; i++) {
        Frame frame = getFromDKV(frameKeys[i]);
        frames[i] = frame;
      }
      return frames;
    }

    /**
     * Fetch all the Models so we can see if they are compatible with our Frame(s).
     */
    protected Map<Model, Set<String>> fetchModelCols() {
      Model[] all_models = null;
      Map<Model, Set<String>> all_models_cols = null;

      if (this.find_compatible_models) {
        // caches for this request
        all_models = Models.fetchAll();
        all_models_cols = new HashMap<Model, Set<String>>();

        for (Model m : all_models) {
          all_models_cols.put(m, new HashSet<String>(Arrays.asList(m._output._names)));
        }
      }
      return all_models_cols;
    }

    /**
     * For a given frame return an array of the compatible models.
     *
     * @param frame The frame to fetch the compatible models for.
     * @param all_models An array of all the Models in the DKV.
     * @param all_models_cols A Map of Model to a Set of its column names.
     * @return
     */
    private static Model[] findCompatibleModels(Frame frame, Model[] all_models, Map<Model, Set<String>> all_models_cols) {
      List<Model> compatible_models = new ArrayList<Model>();

      Set<String> frame_column_names = new HashSet(Arrays.asList(frame._names));

      for (Map.Entry<Model, Set<String>> entry : all_models_cols.entrySet()) {
        Model model = entry.getKey();
        Set<String> model_cols = entry.getValue();

        if (model_cols.containsAll(frame_column_names)) {
          /// See if adapt throws an exception or not.
          try {
            Frame[] outputs = model.adapt(frame, false); // TODO: this does too much work; write canAdapt()
            Frame adapted = outputs[0];
            Frame trash = outputs[1];
            // adapted.delete();  // TODO: shouldn't we clean up adapted vecs?  But we can't delete() the model as a whole. . .
            trash.delete();

            // A-Ok
            compatible_models.add(model);
          }
          catch (Exception e) {
            // skip
          }
        }
      }
      return compatible_models.toArray(new Model[0]);
    }
  }

  /* /2/Frames backward compatibility: uses ?key parameter and returns either a single frame or all. */
  public Schema list_or_fetch(int version, Frames f) {
    //if (this.version != 2)
    //  throw H2O.fail("list_or_fetch should not be routed for version: " + this.version + " of route: " + this.route);

    if (null != f.key) {
      return fetch(version, f);
    } else {
      return list(version, f);
    }
  }

  /** Return all the frames. */
  public Schema list(int version, Frames f) {
    f.frames = Frames.fetchAll();
    return this.schema(version).fillFromImpl(f);
  }

  /** NOTE: We really want to return a different schema here! */
  public Schema columns(int version, Frames f) {
    // TODO: return *only* the columns. . .  This may be a different schema.
    return fetch(version, f);
  }

  // TODO: almost identical to ModelsHandler; refactor
  public static Frame getFromDKV(String key_str) {
    return getFromDKV(Key.make(key_str));
  }

  // TODO: almost identical to ModelsHandler; refactor
  public static Frame getFromDKV(Key key) {
    if (null == key)
      throw new IllegalArgumentException("Got null key.");

    Value v = DKV.get(key);
    if (null == v)
      throw new IllegalArgumentException("Did not find key: " + key.toString());

    Iced ice = v.get();
    if (! (ice instanceof Frame))
      throw new IllegalArgumentException("Expected a Frame for key: " + key.toString() + "; got a: " + ice.getClass());

    return (Frame)ice;
  }

  /** Return a single column from the frame. */
  public Schema column(int version, Frames f) { // TODO: should return a Vec schema
    Frame frame = getFromDKV(f.key);

    // TODO: We really want to return a different schema here!
    Vec vec = frame.vec(f.column);
    if (null == vec)
      throw new IllegalArgumentException("Did not find column: " + f.column + " in frame: " + f.key.toString());

    Vec[] vecs = { vec };
    String[] names = { f.column };
    Frame new_frame = new Frame(names, vecs);
    f.frames = new Frame[1];
    f.frames[0] = new_frame;
    return this.schema(version).fillFromImpl(f);
  }

  public FramesBase columnSummary(int version, Frames frames) {
    Frame frame = getFromDKV(frames.key);
    Vec vec = frame.vec(frames.column);
    if (null == vec)
      throw new IllegalArgumentException("Did not find column: " + frames.column + " in frame: " + frames.key.toString());

    // Compute second pass of rollups: the histograms.
    vec.bins();

    // Cons up our result
    frames.frames = new Frame[1];
    frames.frames[0] = new Frame(new String[] {frames.column }, new Vec[] { vec });
    return schema(version).fillFromImpl(frames);
  }

  /** Return a single frame. */
  public Schema fetch(int version, Frames f) {
    Frame frame = getFromDKV(f.key);
    f.frames = new Frame[1];
    f.frames[0] = frame;

    FramesBase schema = this.schema(version).fillFromImpl(f);
    if (f.find_compatible_models) {
      Model[] compatible = Frames.findCompatibleModels(frame, Models.fetchAll(), f.fetchModelCols());
      schema.compatible_models = new ModelSchema[compatible.length];
      schema.frames[0].compatible_models = new String[compatible.length];
      int i = 0;
      for (Model m : compatible) {
        schema.compatible_models[i] = m.schema().fillFromImpl(m);
        schema.frames[0].compatible_models[i] = m._key.toString();
        i++;
      }
    }
    return schema;
  }

  // Remove an unlocked frame.  Fails if frame is in-use
  public void delete(int version, Frames frames) {
    Frame frame = getFromDKV(frames.key);
    frame.delete();             // lock & remove
  }

  // Remove ALL an unlocked frames.  Throws IAE for all deletes that failed
  // (perhaps because the Frames were locked & in-use).
  public void deleteAll(int version, Frames frames) {
    final Key[] frameKeys = KeySnapshot.globalSnapshot().filter(new KeySnapshot.KVFilter() {
        @Override public boolean filter(KeySnapshot.KeyInfo k) {
          return k._type == TypeMap.FRAME;
        }
      }).keys();

    String err=null;
    Futures fs = new Futures();
    for( int i = 0; i < frameKeys.length; i++ ) {
      try {
        getFromDKV(frameKeys[i]).delete(null,fs);
      } catch( IllegalArgumentException iae ) {
        err += iae.getMessage();
      }
    }
    fs.blockForPending();
    if( err != null ) throw new IllegalArgumentException(err);
  }


  @Override protected FramesBase schema(int version) {
    switch (version) {
    case 2:   return new FramesV2();
    case 3:   return new FramesV3();
    default:  throw H2O.fail("Bad version for Frames schema: " + version);
    }
  }

  // Need to stub this because it's required by H2OCountedCompleter:
  @Override public void compute2() { throw H2O.fail(); }

}
