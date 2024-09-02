package water.api;

import hex.Model;
import water.*;
import water.api.schemas3.*;
import water.exceptions.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.fvec.persist.FramePersist;
import water.util.ExportFileFormat;
import water.util.Log;

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
public class FramesHandler<I extends FramesHandler.Frames, S extends SchemaV3<I,S>> extends Handler {

  /** Class which contains the internal representation of the frames list and params. */
  public static final class Frames extends Iced {
    public Key<Frame> frame_id;
    public long row_offset;
    public int row_count;
    public Frame[] frames;
    public String column;
    public boolean find_compatible_models = false;

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
     * @return An array of compatible models
     */
    private static Model[] findCompatibleModels(Frame frame, Model[] all_models) {
      Map<Model, Set<String>> all_models_cols = Frames.fetchModelCols(all_models);
      List<Model> compatible_models = new ArrayList<>();

      HashSet<String> frame_column_names = new HashSet<>(Arrays.asList(frame._names));

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
      return compatible_models.toArray(new Model[compatible_models.size()]);
    }
  }

  /**
   * Return all the frames. The Frames list will be instances of FrameSynopsisV3,
   * which only contains a few fields, for performance reasons.
   * @see FrameSynopsisV3
   */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public FramesListV3 list(int version, FramesListV3 s) {
    Frames f = s.createAndFillImpl();
    f.frames = Frame.fetchAll();

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
      throw new H2OIllegalArgumentException(param_name, "Frames.getFromDKV()", null);

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
    s.frames[0] = new FrameV3(new_frame);
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
    s.frames[0] = new FrameV3(new Frame(new String[]{s.column}, new Vec[]{vec}), s.row_offset, s.row_count, s.column_offset, s.column_count);
    return s;
  }

  // TODO: return everything but the second level of rollups (histograms); currently mins and maxes are missing
  /** Return a single frame. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public FramesV3 fetch(int version, FramesV3 s) {
    FramesV3 frames = doFetch(version, s);

    // Summary data is big, and not always there: null it out here.  You have to call columnSummary
    // to force computation of the summary data.
    for (FrameBaseV3 a_frame: frames.frames) {
      ((FrameV3)a_frame).clearBinsField();
    }

    return frames;
  }

  public FramesV3 fetchLight(int version, FramesV3 s) {
    FramesV3 frames = doFetch(version, s, false);
    for (FrameBaseV3 a_frame: frames.frames) {
      ((FrameV3)a_frame).clearBinsField();
    }

    return frames;
  }

  private FramesV3 doFetch(int version, FramesV3 s) {
    return doFetch(version, s, true);
  }
  private FramesV3 doFetch(int version, FramesV3 s, boolean expensive) {
    s.createAndFillImpl();

    Frame frame = getFromDKV("key", s.frame_id.key()); // safe
    s.frames = new FrameV3[1];
    s.frames[0] = new FrameV3(frame, s.row_offset, s.row_count, s.column_offset, s.column_count, s.full_column_count, expensive);

    if (s.find_compatible_models) {
      Model[] compatible = Frames.findCompatibleModels(frame, Model.fetchAll());
      s.compatible_models = new ModelSchemaV3[compatible.length];
      ((FrameV3)s.frames[0]).compatible_models = new String[compatible.length];
      int i = 0;
      for (Model m : compatible) {
        s.compatible_models[i] = (ModelSchemaV3)SchemaServer.schema(version, m).fillFromImpl(m);
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

    if (ExportFileFormat.parquet.equals(s.format)) { // format is optional (can be null, eg. from Flow)
      Log.warn("Format is 'parquet', csv parameter values: separator, header, quote_header will be ignored!");
      Log.warn("Format is 'parquet', H2O itself determines the optimal number of files (1 file per chunk). Parts parameter value will be ignored!");
      if (s.parallel) {
        Log.warn("Parallel export to a single file is not supported for parquet format! Export will continue with a parquet-specific setup.");
      }
      s.job = new JobV3(Frame.exportParquet(fr, s.path, s.force, s.compression, s.write_checksum, s.tz_adjust_from_local));
    } else {
      Frame.CSVStreamParams csvParms = new Frame.CSVStreamParams()
              .setSeparator(s.separator)
              .setHeaders(s.header)
              .setQuoteColumnNames(s.quote_header);
      s.job = new JobV3(Frame.export(fr, s.path, s.frame_id.key().toString(),
              s.force, s.num_parts, s.parallel, s.compression, csvParms));
    }

    return s;
  }

  public FrameSaveV3 save(int version, FrameSaveV3 req) {
    Frame fr = getFromDKV("frame_id", req.frame_id.key());
    FramePersist persist = new FramePersist(fr);
    req.job = new JobV3(persist.saveTo(req.dir, req.force));
    return req;
  }

  public FrameLoadV3 load(int version, FrameLoadV3 req) {
    Value v = DKV.get(req.frame_id.key());
    if (v != null) {
      if (req.force) {
        ((Frame) v.get()).remove();
      } else {
        throw new IllegalArgumentException("Frame " + req.frame_id + " already exists.");
      }
    }
    req.job = new JobV3(FramePersist.loadFrom(req.frame_id.key(), req.dir));
    return req;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  // TODO: return list of FrameSummaryV3 that has histograms et al.
  public FramesV3 summary(int version, FramesV3 s) {
    Frame frame = getFromDKV("key", s.frame_id.key()); // safe

    if( null != frame) {
      Futures fs = new Futures();
      int i = 0;
      for( Vec v : frame.vecs() ) {
        if (null == DKV.get(v._key))
          Log.warn("For Frame: " + frame._key + ", Vec number: " + i + " (" + frame.name(i)+ ") is missing; not returning it.");
        else
          v.startRollupStats(fs, Vec.DO_HISTOGRAMS);
        i++;
      }
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
    for (Key key : keys) {
      try {
        getFromDKV("(none)", key).delete(null, fs, true);
      } catch (IllegalArgumentException iae) {
        missing.add(key.toString());
      }
    }
    fs.blockForPending();
    if( missing.size() != 0 ) throw new H2OKeysNotFoundArgumentException("(none)", missing.toArray(new String[missing.size()]));
    return frames;
  }
}
