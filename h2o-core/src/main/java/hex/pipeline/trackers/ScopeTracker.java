package hex.pipeline.trackers;

import hex.pipeline.DataTransformer;
import hex.pipeline.FrameTracker;
import hex.pipeline.PipelineContext;
import water.Scope;
import water.fvec.Frame;

/**
 * a {@link FrameTracker} ensuring that all transformed framed are added to current {@link Scope}.
 */
public class ScopeTracker implements FrameTracker {
  @Override
  public void apply(Frame transformed, Frame original, DataTransformer.FrameType type, PipelineContext context, DataTransformer transformer) {
    if (transformed == null) return;
    Scope.track(transformed);
  }
}
