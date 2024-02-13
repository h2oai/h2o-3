package hex.pipeline;

import water.fvec.Frame;

import java.util.Arrays;

/**
 * A {@link DataTransformer} that calls multiple transformers as a chain of transformations, 
 * that can be optionally completed by a {@link Completer} whose result will be the result of the chain.
 * <br/>
 * The chain accepts one or multiple {@link Frame}s as input, all going through the transformations at the same time, 
 * and all transformed frames can feed the final {@link Completer}.
 * <br/>
 * The chain logic also allows transformers to create temporary resources and close/clean them only after the {@link Completer} has been evaluated.
 */
public class TransformerChain extends DataTransformer<TransformerChain> {

  /**
   * The last operation applied to all the transformed frames.
   * @param <R> type of the final result
   */
  @FunctionalInterface
  interface Completer<R> {
    R apply(Frame[] frames, PipelineContext context);
  }

  /**
   * A {@link Completer} accepting a single {@link Frame}.
   * @param <R> type of the final result
   */
  public static abstract class UnaryCompleter<R> implements Completer<R> {
    @Override
    public R apply(Frame[] frames, PipelineContext context) {
      assert frames.length == 1;
      return apply(frames[0], context);
    }

    public abstract R apply(Frame frame, PipelineContext context);
  }

  /**
   * A default {@link Completer} simply returning all transformed frames.
   */
  public static class AsFramesCompleter implements Completer<Frame[]> {
    public static final AsFramesCompleter INSTANCE = new AsFramesCompleter();
    
    @Override
    public Frame[] apply(Frame[] frames, PipelineContext context) {
      return frames;
    }
  }

  /**
   * A default {@link Completer} simply returning the transformed frame (when used in context of a single transformed frame).
   */
  public static class AsSingleFrameCompleter implements Completer<Frame> {
    public static final AsSingleFrameCompleter INSTANCE = new AsSingleFrameCompleter();

    public Frame apply(Frame[] frames, PipelineContext context) {
      assert frames.length == 1;
      return frames[0];
    }
  }
  
  private final DataTransformer[] _transformers;
  
  private int _index;

  public TransformerChain(DataTransformer[] transformers) {
    assert transformers != null;
    _transformers = transformers.clone();
  }

  @Override
  public boolean isCVSensitive() {
    return Arrays.stream(_transformers).anyMatch(DataTransformer::isCVSensitive);
  }

  @Override
  protected DataTransformer makeDefaults() {
    return new TransformerChain(new DataTransformer[0]);
  }

  @Override
  protected void doPrepare(PipelineContext context) {
    resetIteration();
    nextPrepare(context);
  }
  
  final void nextPrepare(PipelineContext context) {
    DataTransformer dt = next();
    if (dt != null) {
      dt.prepare(context, this);
    }
  }
  
  @Override
  protected Frame doTransform(Frame fr, FrameType type, PipelineContext context) {
    return transform(fr, type, context, AsSingleFrameCompleter.INSTANCE);
  }

  Frame[] doTransform(Frame[] frames, FrameType[] types, PipelineContext context) {
    return transform(frames, types, context, AsFramesCompleter.INSTANCE);
  }

  final <R> R transform(Frame fr, FrameType type, PipelineContext context, Completer<R> completer) {
    return transform(new Frame[] { fr }, new FrameType[] { type }, context, completer);
  }
  
  final <R> R transform(Frame[] frames, FrameType[] types, PipelineContext context, Completer<R> completer) {
    resetIteration();
    return nextTransform(frames, types, context, completer);
  }
  
  final <R> R nextTransform(Frame[] frames, FrameType[] types, PipelineContext context, Completer<R> completer) {
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
  
  private void resetIteration() {
    _index = 0;
  }
  
}
