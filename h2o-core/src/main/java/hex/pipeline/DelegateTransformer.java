package hex.pipeline;

import water.fvec.Frame;

public abstract class DelegateTransformer<T extends DataTransformer> extends DataTransformer<T> {
  
  T _transformer;

  public DelegateTransformer(T transformer) {
    _transformer = transformer;
  }

  @Override
  public void prepare(PipelineContext context) {
    _transformer.prepare(context);
  }

  @Override
  public Frame transform(Frame fr, FrameType type, PipelineContext context) {
    return _transformer.transform(fr, type, context);
  }

}
