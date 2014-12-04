package water.api;

import hex.Model;
import water.*;
import water.api.ModelsHandler.Models;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.*;

class FramesHandler<I extends FramesHandler.Frames, S extends FramesBase<I, S>> extends Handler {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

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
  public FramesBase list_or_fetch(int version, FramesV2 f) {
    //if (this.version != 2)
    //  throw H2O.fail("list_or_fetch should not be routed for version: " + this.version + " of route: " + this.route);
    FramesV3 f3 = new FramesV3();
    f3.fillFromImpl(f.createAndFillImpl());

    if (null != f.key) {
      return fetch(version, f3);
    } else {
      return list(version, f3);
    }
  }

  /** Return all the frames. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public FramesBase list(int version, FramesV3 s) {
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
  public FramesBase columns(int version, FramesV3 s) {
    // TODO: return *only* the columns. . .  This may be a different schema.
    return fetch(version, s);
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
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public FramesBase column(int version, FramesV3 s) { // TODO: should return a Vec schema
    Frame frame = getFromDKV(s.key);

    // TODO: We really want to return a different schema here!
    Vec vec = frame.vec(s.column);
    if (null == vec)
      throw new IllegalArgumentException("Did not find column: " + s.column + " in frame: " + s.key.toString());

    Vec[] vecs = { vec };
    String[] names = { s.column };
    Frame new_frame = new Frame(names, vecs);
    s.frames = new FrameV2[1];
    s.frames[0] = new FrameV2().fillFromImpl(new_frame);
    s.frames[0].clearBinsField();
    return s;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public FramesBase columnSummary(int version, FramesV3 s) {
    Frame frame = getFromDKV(s.key);
    Vec vec = frame.vec(s.column);
    if (null == vec)
      throw new IllegalArgumentException("Did not find column: " + s.column + " in frame: " + s.key.toString());

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
  public FramesBase fetch(int version, FramesV3 s) {
    Frames f = s.createAndFillImpl();

    Frame frame = getFromDKV(s.key);
    s.frames = new FrameV2[1];
    s.frames[0] = new FrameV2().fillFromImpl(frame);

    // Summary data is big, and not always there: null it out here.  You have to call columnSummary
    // to force computation of the summary data.
    for (FrameV2 a_frame: s.frames) {
      a_frame.clearBinsField();

    }
    if (s.find_compatible_models) {
      Model[] compatible = Frames.findCompatibleModels(frame, Models.fetchAll(), f.fetchModelCols());
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
  public void delete(int version, FramesV3 frames) {
    Frame frame = getFromDKV(frames.key);
    frame.delete();             // lock & remove
  }

  /**
   * Remove ALL an unlocked frames.  Throws IAE for all deletes that failed
   * (perhaps because the Frames were locked & in-use).
   */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public void deleteAll(int version, FramesV3 frames) {
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
}
