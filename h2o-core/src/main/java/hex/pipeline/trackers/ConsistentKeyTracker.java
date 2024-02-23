package hex.pipeline.trackers;

import hex.pipeline.DataTransformer;
import hex.pipeline.FrameTracker;
import hex.pipeline.PipelineContext;
import water.KeyGen;
import water.fvec.Frame;

import static hex.pipeline.PipelineHelper.reassignInplace;

/**
 * A {@link FrameTracker} ensuring that all transformed frames in the pipeline are named consistently}, 
 * facilitating debugging and obtaining the origin of frames in the DKV.
 */
public class ConsistentKeyTracker extends AbstractFrameTracker<ConsistentKeyTracker> {

  private static final String SEP = "@@"; // anything that doesn't contain Key.MAGIC_CHAR
  private static final KeyGen DEFAULT_FRAME_KEY_GEN = new KeyGen.PatternKeyGen("{0}"+SEP+"{1}_trf_by_{2}_{rstr}");
  private final KeyGen _frameKeyGen;

  private final Frame _refFrame;

  public ConsistentKeyTracker() {
    this(null, DEFAULT_FRAME_KEY_GEN);
  }

  public ConsistentKeyTracker(Frame origin) {
    this(origin, DEFAULT_FRAME_KEY_GEN);
  }

  public ConsistentKeyTracker(Frame origin, KeyGen frameKeyGen) {
    _refFrame = origin;
    _frameKeyGen = frameKeyGen;
  }

  private Frame getReference(DataTransformer.FrameType type, PipelineContext context) {
    if (_refFrame != null) return _refFrame;
    switch (type) {
      case Training:
        return context.getTrain();
      case Validation:
        return context.getValid();
      case Test:
      default:
        return null;
    }
  }

  @Override
  public void apply(Frame transformed, Frame original, DataTransformer.FrameType type, PipelineContext context, DataTransformer transformer) {
    if (transformed == null) return;
    Frame ref = getReference(type, context);
    if (ref == null) return;
    String refName = ref.getKey().toString();
    String frName = original.getKey().toString();
    if (!frName.startsWith(refName))
      return; // all successive frames must have the same naming pattern when using this tracker -> doesn't apply to this frame.

    if (transformed != original) {
      String baseName = frName.contains(SEP) ? frName.substring(0, frName.lastIndexOf(SEP)) : frName;
      reassignInplace(transformed, _frameKeyGen.make(baseName, type, transformer.name()));
    }
  }
}
