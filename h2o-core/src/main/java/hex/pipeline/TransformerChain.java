package hex.pipeline;

import water.fvec.Frame;

public class TransformerChain extends DataTransformer<TransformerChain> {
  
  @FunctionalInterface
  interface Completer<R> {
    public R apply(Frame[] frames, PipelineContext context);
  }
  
  public static abstract class UnaryCompleter<R> implements Completer<R> {
    @Override
    public R apply(Frame[] frames, PipelineContext context) {
      assert frames.length == 1;
      return apply(frames[0], context);
    }

    public abstract R apply(Frame frame, PipelineContext context);
  }
  
  public static class AsFramesCompleter implements Completer<Frame[]> {
    @Override
    public Frame[] apply(Frame[] frames, PipelineContext context) {
      return frames;
    }
  } 
  
  public static class AsSingleFrameCompleter implements Completer<Frame> {

    public Frame apply(Frame[] frames, PipelineContext context) {
      assert frames.length == 1;
      return frames[0];
    }
  }
  
  private final DataTransformer[] _transformers;
  
  private int _index;

  public TransformerChain(DataTransformer[] transformers) {
    assert transformers != null;
    _transformers = transformers;
  }
  
  @Override
  protected void doPrepare(PipelineContext context) {
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
  protected Frame doTransform(Frame fr, FrameType type, PipelineContext context) {
    return doTransform(new Frame[] { fr }, new FrameType[] { type }, context)[0];
  }

  Frame[] doTransform(Frame[] frames, FrameType[] types, PipelineContext context) {
    return transform(frames, types, context, (fs, c) -> fs);
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
