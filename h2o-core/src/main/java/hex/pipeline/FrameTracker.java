package hex.pipeline;

import hex.pipeline.DataTransformer.FrameType;
import water.fvec.Frame;

import java.io.Serializable;

/**
 * {@link FrameTracker}s are called after each transformation and can be used 
 * for consistent logging, debugging, renaming,... and various other tracking logic.
 */
@FunctionalInterface
public interface FrameTracker extends Serializable {
  void apply(Frame transformed, Frame original, FrameType type, PipelineContext context, DataTransformer transformer);
}
