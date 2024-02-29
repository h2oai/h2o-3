package hex.pipeline.trackers;

import hex.pipeline.DataTransformer;
import hex.pipeline.FrameTracker;
import hex.pipeline.PipelineContext;
import water.fvec.Frame;

/**
 * A {@link FrameTracker} applying multiple trackers sequentially.
 */
public class CompositeFrameTracker extends AbstractFrameTracker<CompositeFrameTracker> {

  private final FrameTracker[] _trackers;
  
  public CompositeFrameTracker() { // for (de)serialization
    _trackers = new FrameTracker[0];
  }

  public CompositeFrameTracker(FrameTracker... trackers) {
    _trackers = trackers;
  }

  @Override
  public void apply(Frame transformed, Frame original, DataTransformer.FrameType type, PipelineContext context, DataTransformer transformer) {
    for (FrameTracker tracker : _trackers) {
      tracker.apply(transformed, original, type, context, transformer);
    }
  }
}
