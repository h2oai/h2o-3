package water.api;

import hex.Model;
import water.*;
import water.api.ModelsHandler.Models;
import water.exceptions.*;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.*;

class FramesHandler<I extends FramesHandler.Frames, S extends FramesBase<I, S>> extends Handler {

  /** Class which contains the internal representation of the frames list and params. */
  protected static final class Frames extends Iced {
    Key key;
    long offset;
    int len;
    Frame[] frames;
    String column;
    public boolean find_compatible_models = false;

    /**
     * Fetch all Frames from the KV store.
     */
    protected static Frame[] fetchAll() {
      // Get all the frames.
      final Key[] frameKeys = KeySnapshot.globalKeysOfClass(Frame.class);
      Frame[] frames = new Frame[frameKeys.length];
      for (int i = 0; i < frameKeys.length; i++) {
        Frame frame = getFromDKV("(none)", frameKeys[i]);
        frames[i] = frame;
      }
      return frames;
    }

    /**
     * Fetch all the Models so we can see if they are compatible with our Frame(s).
     */
    static protected Map<Model, Set<String>> fetchModelCols(Model[] all_models) {
      Map<Model, Set<String>> all_models_cols = null;

      all_models_cols = new HashMap<Model, Set<String>>();

      for (Model m : all_models) {
        all_models_cols.put(m, new HashSet<String>(Arrays.asList(m._output._names)));
      }
      return all_models_cols;
    }

    /**
     * For a given frame return an array of the compatible models.
     *
     * @param frame The frame to fetch the compatible models for.
     * @param all_models An array of all the Models in the DKV.
     * @return
     */
    private static Model[] findCompatibleModels(Frame frame, Model[] all_models) {
      Map<Model, Set<String>> all_models_cols = Frames.fetchModelCols(all_models);
      List<Model> compatible_models = new ArrayList<Model>();

      Set<String> frame_column_names = new HashSet(Arrays.asList(frame._names));

      for (Map.Entry<Model, Set<String>> entry : all_models_cols.entrySet()) {
        Model model = entry.getKey();
        Set<String> model_cols = entry.getValue();

        if (frame_column_names.containsAll(model_cols)) {
          // See if adapt throws an exception or not.
          try {
            if( model.adaptTestForTrain(new Frame(frame), false).length == 0 )
              compatible_models.add(model);
          } catch( IllegalArgumentException e ) {
            // skip
          }
        }
      }
      return compatible_models.toArray(new Model[0]);
    }
  }

  /** /2/Frames backward compatibility: uses ?key parameter and returns either a single frame or all. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public FramesV2 list_or_fetch(int version, FramesV2 f) {
    //if (this.version != 2)
    //  throw H2O.fail("list_or_fetch should not be routed for version: " + this.version + " of route: " + this.route);
    FramesV3 f3 = new FramesV3();
    f3.fillFromImpl(f.createAndFillImpl());

    if (null != f.key) {
      f3 = fetch(version, f3);
    } else {
      f3 = list(version, f3);
    }
    FramesV2 f2 = new FramesV2().fillFromImpl(f3.createAndFillImpl());
    return f2;
  }

  /** Return all the frames. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public FramesV3 list(int version, FramesV3 s) {
    Frames f = s.createAndFillImpl();
    f.frames = Frames.fetchAll();

    s.fillFromImpl(f);

    // Summary data is big, and not always there: null it out here.  You have to call columnSummary
    // to force computation of the summary data.
    for (FrameV2 a_frame: s.frames) {
      a_frame.clearBinsField();
    }

    return s;
  }

  /** NOTE: We really want to return a different schema here! */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public FramesV3 columns(int version, FramesV3 s) {
    // TODO: return *only* the columns. . .  This may be a different schema.
    return fetch(version, s);
  }

  // TODO: almost identical to ModelsHandler; refactor
  public static Frame getFromDKV(String param_name, String key_str) {
    return getFromDKV(param_name, Key.make(key_str));
  }

  // TODO: almost identical to ModelsHandler; refactor
  public static Frame getFromDKV(String param_name, Key key) {
    if (null == key)
      throw new H2OIllegalArgumentException(param_name, "Frames.getFromDKV()", key);

    Value v = DKV.get(key);
    if (null == v)
      throw new H2OKeyNotFoundArgumentException(param_name, key.toString());

    Iced ice = v.get();
    if( ice instanceof Vec )
      return new Frame((Vec)ice);

    if (! (ice instanceof Frame))
      throw new H2OKeyWrongTypeArgumentException(param_name, key.toString(), Frame.class, ice.getClass());

    return (Frame)ice;
  }

  /** Return a single column from the frame. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public FramesV3 column(int version, FramesV3 s) { // TODO: should return a Vec schema
    Frame frame = getFromDKV("key", s.key.key());

    Vec vec = frame.vec(s.column);
    if (null == vec)
      throw new H2OColumnNotFoundArgumentException("column", s.key.toString(), s.column);

    Vec[] vecs = { vec };
    String[] names = { s.column };
    Frame new_frame = new Frame(names, vecs);
    s.frames = new FrameV2[1];
    s.frames[0] = new FrameV2().fillFromImpl(new_frame);
    s.frames[0].clearBinsField();
    return s;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public FramesV3 columnSummary(int version, FramesV3 s) {
    Frame frame = getFromDKV("key", s.key.key()); // safe
    Vec vec = frame.vec(s.column);
    if (null == vec)
      throw new H2OColumnNotFoundArgumentException("column", s.key.toString(), s.column);

    // Compute second pass of rollups: the histograms.
    vec.bins();

    // Cons up our result
    s.frames = new FrameV2[1];
    s.frames[0] = new FrameV2().fillFromImpl(new Frame(new String[] {s.column }, new Vec[] { vec }));
    return s;
  }

  /** Docs for column summary. */
  public StringBuffer columnSummaryDocs(int version, StringBuffer docs) {
    return null; // doc(this, version, docs, "docs/columnSummary.md");
  }

  /** Return a single frame. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public FramesV3 fetch(int version, FramesV3 s) {
    Frames f = s.createAndFillImpl();

    Frame frame = getFromDKV("key", s.key.key()); // safe
    s.frames = new FrameV2[1];
    s.frames[0] = new FrameV2().fillFromImpl(frame);

    // Summary data is big, and not always there: null it out here.  You have to call columnSummary
    // to force computation of the summary data.
    for (FrameV2 a_frame: s.frames) {
      a_frame.clearBinsField();

    }
    if (s.find_compatible_models) {
      Model[] compatible = Frames.findCompatibleModels(frame, Models.fetchAll());
      s.compatible_models = new ModelSchema[compatible.length];
      s.frames[0].compatible_models = new String[compatible.length];
      int i = 0;
      for (Model m : compatible) {
        s.compatible_models[i] = m.schema().fillFromImpl(m);
        s.frames[0].compatible_models[i] = m._key.toString();
        i++;
      }
    }
    return s;
  }

  /** Remove an unlocked frame.  Fails if frame is in-use. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public FramesV3 delete(int version, FramesV3 frames) {
    Frame frame = getFromDKV("key", frames.key.key()); // safe
    frame.delete();             // lock & remove
    return frames;
  }

  /**
   * Remove ALL an unlocked frames.  Throws IAE for all deletes that failed
   * (perhaps because the Frames were locked & in-use).
   */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public FramesV3 deleteAll(int version, FramesV3 frames) {
    final Key[] keys = KeySnapshot.globalKeysOfClass(Frame.class);

    ArrayList<String> missing = new ArrayList<>();
    Futures fs = new Futures();
    for( int i = 0; i < keys.length; i++ ) {
      try {
        getFromDKV("(none)", keys[i]).delete(null, fs);
      } catch( IllegalArgumentException iae ) {
        missing.add(keys[i].toString());
      }
    }
    fs.blockForPending();
    if( missing.size() != 0 ) throw new H2OKeysNotFoundArgumentException("(none)", missing.toArray(new String[missing.size()]));
    return frames;
  }
}
