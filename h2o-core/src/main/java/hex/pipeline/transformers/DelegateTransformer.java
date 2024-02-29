package hex.pipeline.transformers;

import hex.pipeline.DataTransformer;
import hex.pipeline.PipelineContext;
import water.Key;
import water.fvec.Frame;

public abstract class DelegateTransformer<S extends DelegateTransformer<S, T>, T extends DataTransformer<T>> extends DataTransformer<S> {
  
  Key<T> _transformer;
  
  protected DelegateTransformer() {}

  public DelegateTransformer(Key<T> transformer) {
    this._transformer = transformer;
  }

  public DelegateTransformer(T transformer) {
    _transformer = transformer._key;
  }
  
  private DataTransformer getDelegate() {
    return _transformer.get();
  }
  
  @Override
  public Object getParameter(String name) {
    try {
      return getDelegate().getParameter(name);
    } catch (IllegalArgumentException iae) {
      return super.getParameter(name);
    }
  }

  @Override
  public void setParameter(String name, Object value) {
    try {
      getDelegate().setParameter(name, value);
    } catch (IllegalArgumentException iae) {
      super.setParameter(name, value);
    }
  }

  @Override
  public Object getParameterDefaultValue(String name) {
    try {
      return getDelegate().getParameterDefaultValue(name);
    } catch (IllegalArgumentException iae) {
      return super.getParameterDefaultValue(name);
    }
  }

  @Override
  protected void doPrepare(PipelineContext context) {
    getDelegate().prepare(context);
  }

  @Override
  protected Frame doTransform(Frame fr, FrameType type, PipelineContext context) {
    return getDelegate().transform(fr, type, context);
  }

  @Override
  protected S cloneImpl() throws CloneNotSupportedException {
    S clone = super.cloneImpl();
    clone._transformer = _transformer == null ? null : ((T)getDelegate().clone()).getKey();
    return clone;
  }
}
