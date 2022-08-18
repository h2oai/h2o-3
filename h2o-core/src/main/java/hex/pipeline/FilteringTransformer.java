package hex.pipeline;

import water.fvec.Frame;

public abstract class FilteringTransformer<T extends DataTransformer> extends DataTransformer<T> {
  
  //same logic as with caching but there we want to be able to transform full frame once, 
  // and then just filter rows if frame is a subframe of the full one (can we determine this quickly?)
  //filtering can be enabled/disabled (mainly during CV)
  boolean _filterEnabled = true;
  
  @Override
  public Frame transform(Frame fr, FrameType type, PipelineContext context) {
    if (_filterEnabled) {
      return filterRows(fr);
    }
    return fr;
  }
  
  public abstract Frame filterRows(Frame fr);
}
