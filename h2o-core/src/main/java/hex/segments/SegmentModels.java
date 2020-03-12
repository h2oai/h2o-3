package hex.segments;

import hex.Model;
import hex.ModelBuilder;
import water.*;
import water.api.schemas3.KeyV3;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.util.ArrayUtils;
import water.util.StringUtils;

/**
 * Collection of Segment Models
 */
public class SegmentModels extends Keyed<SegmentModels> {

  private final Frame _segments;
  private final Vec _results;

  /**
   * Initialize the Segment Models structure, allocates keys for each SegmentModelResult
   * 
   * @param key destination key
   * @param segments segments
   * @return instance of SegmentModels
   */
  public static SegmentModels make(Key<SegmentModels> key, Frame segments) {
    SegmentModels segmentModels = new SegmentModels(key, segments);
    DKV.put(segmentModels);
    return segmentModels;
  }
  
  private SegmentModels(Key<SegmentModels> key, Frame segments) {
    super(key);
    _results = new MakeResultKeys().doAll(Vec.T_STR, segments).outputFrame().vec(0);
    _segments = segments.deepCopy(Key.makeUserHidden(Key.make().toString()).toString());
  }

  SegmentModelResult addResult(long segmentIdx, ModelBuilder mb, Exception e) {
    Key<SegmentModelResult> resultKey = Key.make(_results.atStr(new BufferedString(), segmentIdx).toString());
    SegmentModelResult result = new SegmentModelResult(resultKey, mb, e);
    DKV.put(result);
    return result;
  }

  /**
   * Converts the collection of Segment Models to a Frame representation
   * 
   * @return Frame with segment column, followed by job status, model key, error and warning columns 
   */
  public Frame toFrame() {
    Frame result = _segments.deepCopy(null); // never expose the underlying Segments Frame (someone could delete it)
    Frame models = new ToFrame().doAll(new byte[]{Vec.T_CAT, Vec.T_STR, Vec.T_STR, Vec.T_STR}, new Frame(_results))
            .outputFrame(
                    new String[]{"Status", "Model", "Errors", "Warnings"},
                    new String[][]{Job.JobStatus.domain(), null, null, null}
            );
    result.add(models);
    return result;
  }
  
  @Override
  public Class<? extends KeyV3> makeSchema() {
    return KeyV3.SegmentModelsKeyV3.class;
  }

  static class SegmentModelResult extends Keyed<SegmentModelResult> {
    final Key<Model> _model;
    final Job.JobStatus _status;
    final String[] _errors;
    final String[] _warns;

    @SuppressWarnings("unchecked")
    SegmentModelResult(Key<SegmentModelResult> selfKey, ModelBuilder mb, Exception e) {
      this(selfKey, mb.dest(), mb._job.getStatus(), getErrors(mb, e), mb._job.warns());
    }

    SegmentModelResult(Key<SegmentModelResult> key, Key<Model> model, Job.JobStatus status, String[] errors, String[] warns) {
      super(key);
      _model = model;
      _status = status;
      _errors = errors;
      _warns = warns;
    }

    private static String[] getErrors(ModelBuilder mb, Exception e) {
      if (mb.error_count() == 0 && e == null)
        return null;
      String[] errors = new String[0];
      if (mb.error_count() > 0)
        errors = ArrayUtils.append(errors, mb.validationErrors());
      if (e != null)
        errors = ArrayUtils.append(errors, StringUtils.toString(e));
      return errors;
    }

    @Override
    public String toString() {
      return "model=" + _model + ", status=" + _status;
    }
  }

  private static class MakeResultKeys extends MRTask<MakeResultKeys> {
    @Override
    public void map(Chunk[] cs, NewChunk nc) {
      for (int i = 0; i < cs[0]._len; i++)
        nc.addStr(Key.makeUserHidden(Key.make().toString()).toString());
    }
  }

  static class ToFrame extends MRTask<ToFrame> {
    @Override
    public void map(Chunk[] cs, NewChunk[] ncs) {
      assert cs.length == 1;
      Chunk c = cs[0];
      BufferedString bs = new BufferedString();
      for (int i = 0; i < c._len; i++) {
        SegmentModelResult result = DKV.getGet(Key.make(c.atStr(bs, i).toString()));
        if (result == null) {
          for (NewChunk nc : ncs)
            nc.addNA();
        } else {
          int col = 0;
          ncs[col++].addNum(result._status.ordinal());
          ncs[col++].addStr(result._model.toString());
          if (result._errors != null)
            ncs[col++].addStr(String.join("\n", result._errors));
          else
            ncs[col++].addNA();
          if (result._warns != null)
            ncs[col++].addStr(String.join("\n", result._warns));
          else
            ncs[col++].addNA();
          assert col == ncs.length;
        }
      }
    }
  }

  @Override
  protected Futures remove_impl(Futures fs, boolean cascade) {
    if (_segments != null) {
      _segments.remove(fs, cascade);
    }
    if (_results != null) {
      fs.add(new CleanUpSegmentResults().dfork(_results));
    }
    return fs;
  }

  static class CleanUpSegmentResults extends MRTask<CleanUpSegmentResults> {
    @Override
    public void map(Chunk c) {
      BufferedString bs = new BufferedString();
      Futures fs = new Futures();
      for (int i = 0; i < c._len; i++)
        Keyed.remove(Key.make(c.atStr(bs, i).toString()), fs, true);
      fs.blockForPending();
    }
    @Override
    protected void postGlobal() {
      _fr.remove();
    }
  }
  
}
