package hex.pipeline;

import water.fvec.Frame;

import java.util.function.BiFunction;

public class TransformerChain extends DataTransformer<TransformerChain> {
  
  interface Completer<R> extends BiFunction<Frame[], PipelineContext, R> {}
  
  static abstract class UnaryCompleter<R> implements Completer<R> {
    @Override
    public R apply(Frame[] frames, PipelineContext context) {
      assert frames.length == 1;
      return apply(frames[0], context);
    }

    public abstract R apply(Frame frame, PipelineContext context);
  }
  
  private final DataTransformer[] _transformers;
  
  private int _index;

  public TransformerChain(DataTransformer[] transformers) {
    _transformers = transformers;
  }
  
  @Override
  public void prepare(PipelineContext context) {
    reset();
    nextPrepare(context);
  }
  
  void nextPrepare(PipelineContext context) {
    DataTransformer dt = next();
    if (dt != null) {
      dt.prepare(context, this);
    }
  }
  
  @Override
  public Frame transform(Frame fr, FrameType type, PipelineContext context) {
    return transform(new Frame[] { fr }, new FrameType[] { type }, context)[0];
  }

  Frame[] transform(Frame[] frames, FrameType[] types, PipelineContext context) {
    return transform(frames, types, context, (f, c) -> f);
  }

  <R> R transform(Frame fr, FrameType type, PipelineContext context, Completer<R> completer) {
    return transform(new Frame[] { fr }, new FrameType[] { type }, context, completer);
  }
  
  <R> R transform(Frame[] frames, FrameType[] types, PipelineContext context, Completer<R> completer) {
    reset();
    return nextTransform(frames, types, context, completer);
  }
  
  <R> R nextTransform(Frame[] frames, FrameType[] types, PipelineContext context, Completer<R> completer) {
    DataTransformer<?> dt = next();
    if (dt != null) {
      return dt.transform(frames, types, context, completer, this);
    } else if (completer != null) {
      return completer.apply(frames, context);
    } else {
      return null;
    }
  }
  
  private DataTransformer next() {
    if (_index >= _transformers.length) return null;
    return _transformers[_index++];
  }
  
  private void reset() {
    _index = 0;
  }
  
}
