package hex.pipeline.transformers;

import hex.pipeline.DataTransformer;
import hex.pipeline.PipelineContext;
import water.fvec.Frame;

/**
 * WiP: not used for now.
 * An abstract transformer to sample/filter the input the frame.
 */
public abstract class FilteringTransformer<S extends DataTransformer> extends DataTransformer<S> {
  
  boolean _filterEnabled = true;
  
  @Override
  protected Frame doTransform(Frame fr, FrameType type, PipelineContext context) {
    if (_filterEnabled) {
      return filterRows(fr);
    }
    return fr;
  }
  
  public abstract Frame filterRows(Frame fr);
}
