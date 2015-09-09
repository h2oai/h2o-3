package water.api;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import hex.Model;
import water.DKV;
import water.Futures;
import water.Iced;
import water.Key;
import water.KeySnapshot;
import water.Value;
import water.api.FramesHandler.Frames;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OKeyNotFoundArgumentException;
import water.exceptions.H2OKeyWrongTypeArgumentException;
import water.exceptions.H2OKeysNotFoundArgumentException;
import water.fvec.Frame;
import water.serial.ObjectTreeBinarySerializer;
import water.util.FileUtils;

class ModelsHandler<I extends ModelsHandler.Models, S extends ModelsBase<I, S>> extends Handler {
  /** Class which contains the internal representation of the models list and params. */
  protected static final class Models extends Iced {
    public Key model_id;
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
        Model model = getFromDKV("(none)", modelKeys[i]);
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
          // See if adapt throws an exception or not.
          try {
            if( model.adaptTestForTrain(new Frame(frame), false, false).length == 0 )
              compatible_frames.add(frame);
          } catch( IllegalArgumentException e ) {
            // skip
          }
        }
      }
      return compatible_frames.toArray(new Frame[0]);
    }
  }

  /** Return all the models. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ModelsV3 list(int version, ModelsV3 s) {
    Models m = s.createAndFillImpl();
    m.models = Models.fetchAll();
    return (ModelsV3) s.fillFromImplWithSynopsis(m);
  }

  // TODO: almost identical to ModelsHandler; refactor
  public static Model getFromDKV(String param_name, String key_str) {
    return getFromDKV(param_name, Key.make(key_str));
  }

  // TODO: almost identical to ModelsHandler; refactor
  public static Model getFromDKV(String param_name, Key key) {
    if (null == key)
      throw new H2OIllegalArgumentException(param_name, "Models.getFromDKV()", key);

    Value v = DKV.get(key);
    if (null == v)
      throw new H2OKeyNotFoundArgumentException(param_name, key.toString());

    Iced ice = v.get();
    if (! (ice instanceof Model))
      throw new H2OKeyWrongTypeArgumentException(param_name, key.toString(), Model.class, ice.getClass());

    return (Model)ice;
  }

  /** Return a single model. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ModelsV3 fetchPreview(int version, ModelsV3 s) {
    s.preview = true;
    return fetch(version, s);
  }

  /** Return a single model. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ModelsV3 fetch(int version, ModelsV3 s) {
    Model model = getFromDKV("key", s.model_id.key());
    s.models = new ModelSchema[1];
    s.models[0] = (ModelSchema)Schema.schema(version, model).fillFromImpl(model);

    if (s.find_compatible_frames) {
      // TODO: refactor fetchFrameCols so we don't need this Models object
      Models m = new Models();
      m.models = new Model[1];
      m.models[0] = model;
      m.find_compatible_frames = true;
      Frame[] compatible = Models.findCompatibleFrames(model, Frames.fetchAll(), m.fetchFrameCols());
      s.compatible_frames = new FrameV3[compatible.length]; // TODO: FrameBase
      ((ModelSchema)s.models[0]).compatible_frames = new String[compatible.length];
      int i = 0;
      for (Frame f : compatible) {
        s.compatible_frames[i] = new FrameV3(f).fillFromImpl(f); // TODO: FrameBase
        ((ModelSchema)s.models[0]).compatible_frames[i] = f._key.toString();
        i++;
      }
    }

    return s;
  }

  /** Remove an unlocked model.  Fails if model is in-use. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ModelsV3 delete(int version, ModelsV3 s) {
    Model model = getFromDKV("key", s.model_id.key());
    model.delete();             // lock & remove
    return s;
  }

  /**
   * Remove ALL an unlocked models.  Throws IAE for all deletes that failed
   * (perhaps because the Models were locked & in-use).
   */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ModelsV3 deleteAll(int version, ModelsV3 models) {
    final Key[] keys = KeySnapshot.globalKeysOfClass(Model.class);

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
    return models;
  }

  public ModelsV3 importModel(int version, ModelImportV3 mimport) {
    ModelsV3 s = (ModelsV3) Schema.newInstance(ModelsV3.class);

    try {
      List<Key> importedKeys = new ObjectTreeBinarySerializer().load(FileUtils.getURI(mimport.dir));
      Model model = (Model) importedKeys.get(0).get();
      s.models = new ModelSchema[1];
      s.models[0] = (ModelSchema) Schema.schema(version, model).fillFromImpl(model);
    } catch (IOException e) {
      throw new H2OIllegalArgumentException("dir", "importModel", e);
    }

    return s;
  }

  public ModelExportV3 exportModel(int version, ModelExportV3 mexport) {
    Model model = getFromDKV("model_id", mexport.model_id.key());
    List<Key> keysToExport = new LinkedList<>();
    keysToExport.add(model._key);
    keysToExport.addAll(model.getPublishedKeys());
    try {
      URI targetUri = FileUtils.getURI(mexport.dir);
      new ObjectTreeBinarySerializer(mexport.force).save(keysToExport, targetUri);
      // Send back
      mexport.dir = "file".equals(targetUri.getScheme()) ? new File(targetUri).getCanonicalPath() : targetUri.toString();
    } catch (IOException e) {
      throw new H2OIllegalArgumentException("dir", "exportModel", e);
    }
    return mexport;
  }
}
