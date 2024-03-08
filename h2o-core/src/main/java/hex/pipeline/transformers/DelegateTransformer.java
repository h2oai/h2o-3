package hex.pipeline.transformers;

import hex.pipeline.DataTransformer;
import hex.pipeline.PipelineContext;
import water.fvec.Frame;

public abstract class DelegateTransformer<S extends DelegateTransformer, T extends DataTransformer> extends DataTransformer<S> {
  
  T _transformer;
  
  protected DelegateTransformer() {}

  public DelegateTransformer(T transformer) {
    _transformer = transformer;
  }

  @Override
  public Object getParameter(String name) {
    try {
      return _transformer.getParameter(name);
    } catch (IllegalArgumentException iae) {
      return super.getParameter(name);
    }
  }

  @Override
  public void setParameter(String name, Object value) {
    try {
      _transformer.setParameter(name, value);
    } catch (IllegalArgumentException iae) {
      super.setParameter(name, value);
    }
  }

  @Override
  public Object getParameterDefaultValue(String name) {
    try {
      return _transformer.getParameterDefaultValue(name);
    } catch (IllegalArgumentException iae) {
      return super.getParameterDefaultValue(name);
    }
  }

  @Override
  protected void doPrepare(PipelineContext context) {
    _transformer.prepare(context);
  }

  @Override
  protected Frame doTransform(Frame fr, FrameType type, PipelineContext context) {
    return _transformer.transform(fr, type, context);
  }

  @Override
  protected S cloneImpl() throws CloneNotSupportedException {
    S clone = super.cloneImpl();
    clone._transformer = (T)_transformer.clone();
    return clone;
  }
}
