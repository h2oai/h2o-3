package hex.segments;

import hex.Model;
import water.*;
import water.fvec.Frame;
import water.rapids.ast.prims.mungers.AstGroup;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SegmentModelsBuilder {

  private static final AtomicLong nextSegmentModelsNum = new AtomicLong(0);

  private final SegmentModelsParameters _parms;
  private final Model.Parameters _blueprint_parms;

  public SegmentModelsBuilder(SegmentModelsParameters parms, Model.Parameters blueprintParms) {
    _parms = parms;
    _blueprint_parms = blueprintParms;
  }

  public Job<SegmentModels> buildSegmentModels() {
    final Frame segments;
    if (_parms._segments != null) {
      segments = validateSegmentsFrame(_parms._segments, _parms._segment_columns);
    } else {
      segments = makeSegmentsFrame(_blueprint_parms._train, _parms._segment_columns);
    }
    final Job<SegmentModels> job = new Job<>(makeDestKey(), SegmentModels.class.getName(), _blueprint_parms.algoName());
    SegmentModelsBuilderTask segmentBuilder = new SegmentModelsBuilderTask(
            job, segments, _blueprint_parms._train, _blueprint_parms._valid);
    return job.start(segmentBuilder, segments.numRows());
  }

  private Frame makeSegmentsFrame(Key<Frame> trainKey, String[] segmentColumns) {
    Frame train = validateSegmentsFrame(trainKey, segmentColumns);
    return new AstGroup()
            .performGroupingWithAggregations(train, train.find(segmentColumns), new AstGroup.AGG[0])
            .getFrame();
  }

  private Key<SegmentModels> makeDestKey() {
    if (_parms._segment_models_id != null)
      return _parms._segment_models_id;
    String id = H2O.calcNextUniqueObjectId("segment_models", nextSegmentModelsNum, _blueprint_parms.algoName());
    return Key.make(id);
  }

  private static Frame validateSegmentsFrame(Key<Frame> segmentsKey, String[] segmentColumns) {
    Frame segments = segmentsKey.get();
    if (segments == null) {
      throw new IllegalStateException("Frame `" + segmentsKey + "` doesn't exist.");
    }
    List<String> invalidColumns = Stream.of(segmentColumns != null ? segmentColumns : segments.names())
            .filter(name -> !segments.vec(name).isCategorical() && !segments.vec(name).isInt())
            .collect(Collectors.toList());
    if (!invalidColumns.isEmpty()) {
      throw new IllegalStateException(
              "Columns to segment-by can only be categorical and integer of type, invalid columns: " + invalidColumns);
    }
    return segments;
  }
  
  private class SegmentModelsBuilderTask extends H2O.H2OCountedCompleter<SegmentModelsBuilderTask> {
    private final Job<SegmentModels> _job;
    private final Frame _segments;
    private final Frame _full_train;
    private final Frame _full_valid;
    private final Key _counter_key;

    private SegmentModelsBuilderTask(Job<SegmentModels> job, Frame segments,
                                     Key<Frame> train, Key<Frame> valid) {
      _job = job;
      _segments = segments;
      _full_train = reorderColumns(train);
      _full_valid = reorderColumns(valid);
      _counter_key = Key.make();
    }

    @Override
    public void compute2() {
      try {
        _blueprint_parms.read_lock_frames(_job);
        SegmentModels segmentModels = SegmentModels.make(_job._result, _segments);
        WorkAllocator allocator = new WorkAllocator(_counter_key, _segments.numRows());
        LocalSequentialSegmentModelsBuilder localBuilder = new LocalSequentialSegmentModelsBuilder(
                _job, _blueprint_parms, _segments, _full_train, _full_valid, allocator);
        new MultiNodeRunner(localBuilder, segmentModels).doAllNodes();
      } finally {
        _blueprint_parms.read_unlock_frames(_job);
        if (_segments._key == null) { // segments frame was auto-generated 
          _segments.remove();
        }
        DKV.remove(_counter_key);
      }
      tryComplete();
    }
    
    private Frame reorderColumns(Key<Frame> key) {
      if (key == null)
        return null;
      Frame f = key.get();
      if (f == null) {
        throw new IllegalStateException("Key " + key + " doesn't point to an existing Frame.");
      }
      Frame mutating = new Frame(f);
      Frame reordered = new Frame(_segments.names(), mutating.vecs(_segments.names()))
              .add(mutating.remove(_segments.names()));
      reordered._key = f._key;
      return reordered;
    }

  }

  private static class MultiNodeRunner extends MRTask<MultiNodeRunner> {
    LocalSequentialSegmentModelsBuilder _builder;
    SegmentModels _segment_models;

    private MultiNodeRunner(LocalSequentialSegmentModelsBuilder builder, SegmentModels segmentModels) {
      _builder = builder;
      _segment_models = segmentModels;
    }

    @Override
    protected void setupLocal() {
      _builder.buildModels(_segment_models);
    }
  }

  public static class SegmentModelsParameters extends Iced<SegmentModelsParameters> {
    Key<SegmentModels> _segment_models_id;
    Key<Frame> _segments;
    String[] _segment_columns;
  }

}
