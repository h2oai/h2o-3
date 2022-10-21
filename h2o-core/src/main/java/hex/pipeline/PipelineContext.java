package hex.pipeline;

import hex.pipeline.DataTransformer.FrameType;
import hex.pipeline.PipelineModel.PipelineParameters;
import water.DKV;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.util.Countdown;

import static hex.pipeline.PipelineHelper.reassignInplace;

public class PipelineContext implements Cloneable {
  
  @FunctionalInterface
  interface FrameTracker {
    /**
     * 
     * @param transformed
     * @param frame
     * @param type
     * @param context
     * @param transformer
     */
    void apply(Frame transformed, Frame frame, FrameType type, PipelineContext context, DataTransformer transformer);
  }
  
  public static class CompositeFrameTracker implements FrameTracker {
    
    private final FrameTracker[] _trackers;
    
    public CompositeFrameTracker(FrameTracker... trackers) {
      _trackers = trackers;
    }

    @Override
    public void apply(Frame transformed, Frame frame, FrameType type, PipelineContext context, DataTransformer transformer) {
      for (FrameTracker tracker : _trackers) {
        tracker.apply(transformed, frame, type, context, transformer);
      }
    }
  } 
  
  public static class ConsistentKeyTracker implements FrameTracker {
    
    private static final String SEP = "@@"; // anything that doesn't contain Key.MAGIC_CHAR
    private static final String TRF_BY = "_trf_by_"; 
    
    private final Frame _refFrame;

    public ConsistentKeyTracker() {
      this(null);
    }

    public ConsistentKeyTracker(Frame origin) {
      _refFrame = origin;
    }
    
    private Frame getReference(FrameType type, PipelineContext context) {
      if (_refFrame != null) return _refFrame;
      switch (type) {
        case Training:
          return context.getTrain();
        case Validation:
          return context.getValid();
        case Scoring:
        default:
          return null;
      }
    }

    @Override
    public void apply(Frame transformed, Frame frame, FrameType type, PipelineContext context, DataTransformer transformer) {
      if (transformed == null) return;
      Frame ref = getReference(type, context);
      if (ref == null) return;
      String refName = ref.getKey().toString();
      String frName = frame.getKey().toString();
//      String trfName = transformed.getKey().toString();
      if (!frName.startsWith(refName)) return; // all successive frames must have the same naming pattern when using this tracker -> doesn't apply to this frame.
      
      if (transformed != frame) {
        String baseName = frName.contains(SEP) ? frName.substring(0, frName.lastIndexOf(SEP)) : frName;
        reassignInplace(transformed, Key.make(baseName+SEP+type+TRF_BY+transformer.id()));
      }
    }
  }
  
  public static class ScopeTracker implements FrameTracker {
    @Override
    public void apply(Frame transformed, Frame frame, FrameType type, PipelineContext context, DataTransformer transformer) {
      if (transformed == null) return;
      Scope.track(transformed);
    }
  }

  public final PipelineParameters _params;
  
  public final FrameTracker _tracker;
  
  private Frame _train;
  private Frame _valid;
  
  private Countdown _countdown;

  public PipelineContext(PipelineParameters params) {
    this(params, null);
  }
  
  public PipelineContext(PipelineParameters params, FrameTracker tracker) {
    _params = params;
    _tracker = tracker;
  }
  
  public Frame getTrain() {
    return _train != null ? _train 
            : _params != null ? _params.train() 
            : null;
  }
  
  public void setTrain(Frame train) {
    _train = train;
  }
  
  public Frame getValid() {
    return _valid != null ? _valid 
            : _params != null ? _params.valid() 
            : null;
  }
  
  public void setValid(Frame valid) {
    _valid = valid;
  }

}
