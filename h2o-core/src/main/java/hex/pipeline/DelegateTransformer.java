package hex.pipeline;

import water.fvec.Frame;

public abstract class DelegateTransformer<T extends DataTransformer> extends DataTransformer<T> {
  
  T _transformer;

  public DelegateTransformer(T transformer) {
    super();
    _transformer = transformer;
  }

  @Override
  protected void doPrepare(PipelineContext context) {
    _transformer.prepare(context);
  }

  @Override
  protected Frame doTransform(Frame fr, FrameType type, PipelineContext context) {
    return _transformer.doTransform(fr, type, context);
  }

}
