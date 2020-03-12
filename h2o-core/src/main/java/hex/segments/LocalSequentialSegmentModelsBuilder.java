package hex.segments;

import hex.Model;
import hex.ModelBuilder;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.Log;

import java.util.Arrays;

class LocalSequentialSegmentModelsBuilder extends Iced<LocalSequentialSegmentModelsBuilder> {

  private final Job<SegmentModels> _job;
  private final Model.Parameters _blueprint_parms;
  private final Frame _segments;
  private final Frame _full_train;
  private final Frame _full_valid;
  private final WorkAllocator _allocator;

  LocalSequentialSegmentModelsBuilder(Job<SegmentModels> job, Model.Parameters blueprint_parms,
                                             Frame segments, Frame fullTrain, Frame fullValid,
                                             WorkAllocator allocator) {
    _job = job;
    _blueprint_parms = blueprint_parms;
    _segments = segments;
    _full_train = fullTrain;
    _full_valid = fullValid;
    _allocator = allocator;
  }

  void buildModels(SegmentModels segmentModels) {
    Vec.Reader[] segmentVecReaders = new Vec.Reader[_segments.numCols()];
    for (int i = 0; i < segmentVecReaders.length; i++)
      segmentVecReaders[i] = _segments.vec(i).new Reader();
    for (long segmentIdx = _allocator.getNextWorkItem(); segmentIdx < _allocator.getMaxWork(); segmentIdx = _allocator.getNextWorkItem()) {
      if (_job.stop_requested())
        throw new Job.JobCancelledException();  // Handle end-user cancel request

      double[] segmentVals = readRow(segmentVecReaders, segmentIdx);
      final ModelBuilder builder = makeBuilder(segmentIdx, segmentVals);

      Exception failure = null;
      try {
        builder.init(false);
        if (builder.error_count() == 0)
          builder.trainModel().get();
      } catch (Exception e) {
        failure = e;
      } finally {
        _job.update(1);
        SegmentModels.SegmentModelResult result = segmentModels.addResult(segmentIdx, builder, failure);
        Log.info("Finished building a model for segment id=", segmentIdx, ", result: ", result);
        cleanUp(builder);
      }
    }
  }

  private void cleanUp(ModelBuilder builder) {
    Futures fs = new Futures();
    Keyed.remove(builder._parms._train, fs, true);
    Keyed.remove(builder._parms._valid, fs, true);
    fs.blockForPending();
  }

  private ModelBuilder makeBuilder(long segmentIdx, double[] segmentVals) {
    ModelBuilder builder = ModelBuilder.make(_blueprint_parms);
    builder._parms._train = makeSegmentFrame(_full_train, segmentIdx, segmentVals);
    builder._parms._valid = makeSegmentFrame(_full_valid, segmentIdx, segmentVals);
    return builder;
  }

  private Key<Frame> makeSegmentFrame(Frame f, long segmentIdx, double[] segmentVals) {
    if (f == null)
      return null;
    Key<Frame> segmentFrameKey = Key.make(f.toString() + "_segment_" + segmentIdx);
    Frame segmentFrame = new MakeSegmentFrame(segmentVals)
            .doAll(f.types(), f)
            .outputFrame(segmentFrameKey, f.names(), f.domains());
    assert segmentFrameKey.equals(segmentFrame._key);
    return segmentFrameKey;
  }

  private static double[] readRow(Vec.Reader[] vecReaders, long r) {
    double[] row = new double[vecReaders.length];
    for (int i = 0; i < row.length; i++)
      row[i] = vecReaders[i].isNA(r) ? Double.NaN : vecReaders[i].at(r);
    return row;
  }

  private static class MakeSegmentFrame extends MRTask<MakeSegmentFrame> {
    private final double[] _match_row;

    MakeSegmentFrame(double[] matchRow) {
      _match_row = matchRow;
    }

    @Override
    public void map(Chunk[] cs, NewChunk[] ncs) {
      int cnt = 0;
      int rows[] = new int[cs[0]._len];
      each_row: for (int row = 0; row < rows.length; row++) {
        for (int i = 0; i < _match_row.length; i++) {
          if (Double.isNaN(_match_row[i]) && !cs[i].isNA(row))
            continue each_row;
          if (_match_row[i] != cs[i].atd(row))
            continue each_row;
        }
        rows[cnt++] = row;
      }
      if (cnt == 0)
        return;
      rows = cnt == rows.length ? rows : Arrays.copyOf(rows, cnt);
      for (int i = 0; i < cs.length; i++) {
        cs[i].extractRows(ncs[i], rows);
      }
    }
  }

}
