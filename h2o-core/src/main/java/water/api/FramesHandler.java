package water.api;

import hex.Model;
import jsr166y.CountedCompleter;
import water.*;
import water.api.ModelsHandler.Models;
import water.exceptions.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.persist.PersistManager;
import water.util.KeyedVoid;
import water.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

/*
 * FramesHandler deals with all REST API endpoints that start with /Frames.
 * <p>
 * GET /3/Frames/(?<frameid>.*)/export/(?<path>.*)/overwrite/(?<force>.*)
 * <p> export(): Export a Frame to the given path with optional overwrite.
 * <p>
 * GET /3/Frames/(?<frameid>.*)/columns/(?<column>.*)/summary
 * <p> columnSummary(): Return the summary metrics for a column, e.g. mins, maxes, mean, sigma, percentiles, etc.
 * <p>
 * GET /3/Frames/(?<frameid>.*)/columns/(?<column>.*)/domain
 * <p> columnDomain(): Return the domains for the specified column. \"null\" if the column is not an categorical.
 * <p>
 * GET /3/Frames/(?<frameid>.*)/columns/(?<column>.*)
 * <p> column(): Return the specified column from a Frame.
 * <p>
 * TODO: deleteme?
 * GET /3/Frames/(?<frameid>.*)/columns
 * <p> columns(): Return all the columns from a Frame.
 * <p>
 * GET /3/Frames/(?<frameid>.*)/summary
 * <p> summary(): Return a Frame, including the histograms, after forcing computation of rollups.
 * <p>
 * GET /3/Frames/(?<frameid>.*)
 * <p> fetch(): Return the specified Frame.
 * <p>
 * GET /3/Frames
 * <p> list(): Return all Frames in the H2O distributed K/V store.
 * <p>
 * DELETE /3/Frames/(?<frameid>.*)
 * <p> delete(): Delete the specified Frame from the H2O distributed K/V store.
 * <p>
 * DELETE /3/Frames
 * <p> deleteAll(): Delete all Frames from the H2O distributed K/V store.
 * <p>
 */
class FramesHandler<I extends FramesHandler.Frames, S extends FramesBase<I, S>> extends Handler {

  /** Class which contains the internal representation of the frames list and params. */
  protected static final class Frames extends Iced {
    Key frame_id;
    long row_offset;
    int row_count;
    long column_offset;
    int column_count;
    Frame[] frames;
    String column;
    public boolean find_compatible_models = false;

    /**
     * Fetch all Frames from the KV store.
     */
    protected static Frame[] fetchAll() {
      // Get all the frames.
      final Key[] frameKeys = KeySnapshot.globalKeysOfClass(Frame.class);
      List<Frame> frames = new ArrayList<Frame>(frameKeys.length);
      for (int i = 0; i < frameKeys.length; i++) {
        Frame frame = getFromDKV("(none)", frameKeys[i]);
        // Weed out frames with vecs that are no longer in DKV
        Vec[] vs = frame.vecs();
        boolean skip = false;
        for (int j=0; j < vs.length; j++) {
          if (DKV.get(vs[j]._key) == null) {
            Log.warn("Leaked frame: Frame "+frame._key+" points to one or more deleted vecs.");
            skip = true;
            break;
          }
        }
        if (!skip) frames.add(frame);
      }
      return frames.toArray(new Frame[frames.size()]);
    }

    /**
     * Fetch all the Models so we can see if they are compatible with our Frame(s).
     */
    static protected Map<Model, Set<String>> fetchModelCols(Model[] all_models) {
      Map<Model, Set<String>> all_models_cols = new HashMap<>();
      for (Model m : all_models)
        all_models_cols.put(m, new HashSet<>(Arrays.asList(m._output._names)));
      return all_models_cols;
    }

    /**
     * For a given frame return an array of the compatible models.
     *
     * @param frame The frame for which we should fetch the compatible models.
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
            if( model.adaptTestForTrain(new Frame(frame), false, false).length == 0 )
              compatible_models.add(model);
          } catch( IllegalArgumentException e ) {
            // skip
          }
        }
      }
      return compatible_models.toArray(new Model[0]);
    }
  }

  /**
   * Return all the frames. The Frames list will be instances of FrameSynopsisV3,
   * which only contains a few fields, for performance reasons.
   * @see FrameSynopsisV3
   */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public FramesV3 list(int version, FramesV3 s) {
    Frames f = s.createAndFillImpl();
    f.frames = Frames.fetchAll();

    s.fillFromImplWithSynopsis(f);

    return s;
  }

  // TODO: in /4 return a schema with just a list of column names.
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

  // TODO: return VecV4
  /** Return a single column from the frame. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public FramesV3 column(int version, FramesV3 s) { // TODO: should return a Vec schema
    Frame frame = getFromDKV("key", s.frame_id.key());

    Vec vec = frame.vec(s.column);
    if (null == vec)
      throw new H2OColumnNotFoundArgumentException("column", s.frame_id.toString(), s.column);

    Vec[] vecs = { vec };
    String[] names = { s.column };
    Frame new_frame = new Frame(names, vecs);
    s.frames = new FrameV3[1];
    s.frames[0] = new FrameV3().fillFromImpl(new_frame);
    ((FrameV3)s.frames[0]).clearBinsField();
    return s;
  }

  // TODO: return VecDomainV4
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public FramesV3 columnDomain(int version, FramesV3 s) {
    Frame frame = getFromDKV("key", s.frame_id.key());
    Vec vec = frame.vec(s.column);
    if (vec == null)
      throw new H2OColumnNotFoundArgumentException("column", s.frame_id.toString(), s.column);
    s.domain = new String[1][];
    s.domain[0] = vec.domain();
    return s;
  }

  // TODO: return VecSummaryV4
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public FramesV3 columnSummary(int version, FramesV3 s) {
    Frame frame = getFromDKV("key", s.frame_id.key()); // safe
    Vec vec = frame.vec(s.column);
    if (null == vec)
      throw new H2OColumnNotFoundArgumentException("column", s.frame_id.toString(), s.column);

    // Compute second pass of rollups: the histograms.
    vec.bins();

    // Cons up our result
    s.frames = new FrameV3[1];
    s.frames[0] = new FrameV3().fillFromImpl(new Frame(new String[]{s.column}, new Vec[]{vec}), s.row_offset, s.row_count, s.column_offset, s.column_count);
    return s;
  }

  /** Docs for column summary. */
  public StringBuffer columnSummaryDocs(int version, StringBuffer docs) {
    return null; // doc(this, version, docs, "docs/columnSummary.md");
  }

  // TODO: return everything but the second level of rollups (histograms); currently mins and maxes are missing
  /** Return a single frame. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public FramesV3 fetch(int version, FramesV3 s) {
    FramesV3 frames = doFetch(version, s);

    // Summary data is big, and not always there: null it out here.  You have to call columnSummary
    // to force computation of the summary data.
    for (FrameBase a_frame: frames.frames) {
      ((FrameV3)a_frame).clearBinsField();
    }

    return frames;
  }

  private FramesV3 doFetch(int version, FramesV3 s) {
    Frames f = s.createAndFillImpl();

    Frame frame = getFromDKV("key", s.frame_id.key()); // safe
    s.frames = new FrameV3[1];
    s.frames[0] = new FrameV3(frame, s.row_offset, s.row_count).fillFromImpl(frame, s.row_offset, s.row_count, s.column_offset, s.column_count);  // TODO: Refactor with FrameBase

    if (s.find_compatible_models) {
      Model[] compatible = Frames.findCompatibleModels(frame, Models.fetchAll());
      s.compatible_models = new ModelSchema[compatible.length];
      ((FrameV3)s.frames[0]).compatible_models = new String[compatible.length];
      int i = 0;
      for (Model m : compatible) {
        s.compatible_models[i] = (ModelSchema)Schema.schema(version, m).fillFromImpl(m);
        ((FrameV3)s.frames[0]).compatible_models[i] = m._key.toString();
        i++;
      }
    }
    return s;
  }

  /** Export a single frame to the specified path. */
  public FramesV3 export(int version, FramesV3 s) {
    Frame fr = getFromDKV("key", s.frame_id.key());
    Log.info("ExportFiles processing (" + s.path + ")");
    s.job =  (JobV3) Schema.schema(version, Job.class).fillFromImpl(ExportDatasetJob.export(fr, s.path, s.frame_id.key().toString(),s.force));
    return s;
  }


  // TODO: export a collection of columns as a single Frame.
//  public FrameV3 exportCols(int version, FramesV3 s) {
//    // have a collection of frames and column indices, cbind them, export, drop the ephemeral Frame
//
//  }

  private static class ExportDatasetJob extends Job<KeyedVoid> {

    private ExportDatasetJob(String path) {
      super(Key.<KeyedVoid>make(path), "Export frame");
    }

    private static ExportDatasetJob export(Frame fr, String path, String frameName, boolean overwrite) {
      // Validate input
      boolean fileExists = H2O.getPM().exists(path);
      if (overwrite && fileExists) {
        Log.warn("File " + path + " exists, but will be overwritten!");
      } else if (!overwrite && fileExists) {
        throw new H2OIllegalArgumentException(path, "exportFrame", "File " + path + " already exists!");
      }
      InputStream is = (fr).toCSV(true, false);
      ExportDatasetJob job = new ExportDatasetJob(path);
      ExportTask t = new ExportTask(is, path, frameName, overwrite, job);
      job.start(t, fr.anyVec().nChunks(), true);
      return job;
    }

    private static class ExportTask extends H2O.H2OCountedCompleter<ExportTask> {

      final InputStream _csv;
      final String _path;
      final String _frameName;
      final boolean _overwrite;
      final Job _j;

      ExportTask(InputStream csv, String path, String frameName, boolean overwrite, Job j) {
        _csv = csv;
        _path = path;
        _frameName = frameName;
        _overwrite = overwrite;
        _j = j;
      }

      private void copyStream(OutputStream os, final int buffer_size) {
        int curIdx = 0;
        try {
          byte[] bytes = new byte[buffer_size];
          for (; ; ) {
            int count = _csv.read(bytes, 0, buffer_size);
            if (count <= 0) {
              break;
            }
            os.write(bytes, 0, count);
            int workDone = ((Frame.CSVStream) _csv)._curChkIdx;
            if (curIdx != workDone) {
              _j.update(workDone - curIdx);
              curIdx = workDone;
            }
          }
        } catch (Exception ex) {
          throw new RuntimeException(ex);
        }
      }

      @Override
      public void compute2() {
        PersistManager pm = H2O.getPM();
        OutputStream os = null;
        try {
          os = pm.create(_path, _overwrite);
          copyStream(os, 4 * 1024 * 1024);
        } finally {
          if (os != null) {
            try {
              os.close();
              Log.info("Key '" + _frameName + "' was written to " + _path + ".");
            } catch (Exception e) {
              Log.err(e);
            }
          }
        }
        tryComplete();
      }

      // Took a crash/NPE somewhere in the parser.  Attempt cleanup.
      @Override
      public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller) {
        if (_j != null) {
          _j.cancel();
          if (ex instanceof H2OParseException) {
            throw (H2OParseException) ex;
          } else {
            _j.failed(ex);
          }
        }
        return true;
      }

      @Override
      public void onCompletion(CountedCompleter caller) {
        _j.done();
      }
    }
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  // TODO: return list of FrameSummaryV3 that has histograms et al.
  public FramesV3 summary(int version, FramesV3 s) {
    Frame frame = getFromDKV("key", s.frame_id.key()); // safe

    if( null != frame) {
      Futures fs = new Futures();
      for( Vec v : frame.vecs() )
        v.startRollupStats(fs, Vec.DO_HISTOGRAMS);
      fs.blockForPending();
    }

    return doFetch(version, s);
  }

  /** Remove an unlocked frame.  Fails if frame is in-use. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public FramesV3 delete(int version, FramesV3 frames) {
    Frame frame = getFromDKV("key", frames.frame_id.key()); // safe
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
