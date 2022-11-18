package hex.pipeline.transformers;

import hex.pipeline.DataTransformer;
import hex.pipeline.PipelineContext;
import water.fvec.Frame;

public class UnionTransformer extends DataTransformer<UnionTransformer> {
  
  public enum UnionStrategy {
    append, 
    replace
  }
  
  private DataTransformer[] _transformers;
  private UnionStrategy _strategy;

  public UnionTransformer(DataTransformer[] transformers, UnionStrategy strategy) {
    _transformers = transformers;
    _strategy = strategy;
  }

  @Override
  public Object getParameter(String name) {
    return super.getParameter(name); //TODO similar logic as in PipelineParameters (use ModelParametersAccessor)
  }

  @Override
  public void setParameter(String name, Object value) {
    super.setParameter(name, value);
  }

  @Override
  protected DataTransformer makeDefaults() {
    return new UnionTransformer(null, null);
  }

  @Override
  protected Frame doTransform(Frame fr, FrameType type, PipelineContext context) {
    Frame result = null;
    switch (_strategy) {
      case append:
        result = new Frame(fr);
        break;
      case replace:
        result = new Frame();
        break;
    }
    for (DataTransformer dt : _transformers) {
      result.add(dt.transform(fr, type, context));
    }
    return result;
  }
  
}
