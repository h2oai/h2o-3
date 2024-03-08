package hex.pipeline;

import hex.pipeline.PipelineModel.PipelineParameters;
import water.fvec.Frame;

/**
 * A context object passed to the {@link DataTransformer}s (usually through a {@link TransformerChain} 
 * and providing useful information, especially to help some transformers 
 * to configure themselves/initialize during the {@link DataTransformer#prepare(PipelineContext)} phase.
 */
public class PipelineContext implements Cloneable {

  public final PipelineParameters _params;
  
  public final FrameTracker _tracker;
  
  private Frame _train;
  private Frame _valid;
  
  public PipelineContext(PipelineParameters params) {
    this(params, null);
  }
  
  public PipelineContext(PipelineParameters params, FrameTracker tracker) {
    assert params != null;
    _params = (PipelineParameters) params.clone(); // cloning this here as those _params can be mutated during transformers' preparation.
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
