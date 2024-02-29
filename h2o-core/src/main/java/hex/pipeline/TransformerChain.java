package hex.pipeline;

import water.DKV;
import water.Futures;
import water.Key;
import water.fvec.Frame;

import java.io.Serializable;
import java.util.Arrays;
import java.util.stream.Stream;

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
  interface Completer<R> extends Serializable {
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
  
  private final Key<DataTransformer>[] _transformers;
  
  private int _index;

  public TransformerChain(DataTransformer[] transformers) {
    assert transformers != null;
    _transformers = Stream.of(transformers)
            .map(DataTransformer::init)
            .map(DataTransformer::getKey)
            .toArray(Key[]::new);
  }
  
  public TransformerChain(Key<DataTransformer>[] transformers) {
    assert transformers!= null;
    _transformers = transformers.clone();
  }

  @Override
  public TransformerChain init() {
    super.init();
    Arrays.stream(getTransformers()).forEach(DataTransformer::init);
    return this;
  }

  private DataTransformer[] getTransformers() {
    for (Key<DataTransformer> key : _transformers) {
      DKV.prefetch(key);
    }
    return Arrays.stream(_transformers).map(Key::get).toArray(DataTransformer[]::new);
  }

  @Override
  public boolean isCVSensitive() {
    return Arrays.stream(getTransformers()).anyMatch(DataTransformer::isCVSensitive);
  }

  @Override
  protected DataTransformer makeDefaults() {
    return new TransformerChain(new Key[0]);
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
    return _transformers[_index++].get();
  }
  
  private void resetIteration() {
    _index = 0;
  }

  @Override
  protected Futures remove_impl(Futures fs, boolean cascade) {
    if (cascade) {
      if (_transformers != null) {
        for (DataTransformer dt : getTransformers()) {
          if (dt != null) dt.cleanup(fs);
        }
      }
    }
    return super.remove_impl(fs, cascade);
  }
}
