package hex.pipeline;

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
      result.add(dt.doTransform(fr, type, context));
    }
    return result;
  }
  
}
