package hex.pipeline;

import hex.pipeline.TransformerChain.Completer;
import water.Iced;
import water.fvec.Frame;

public abstract class DataTransformer<T extends DataTransformer> extends Iced<T> {
  
  public enum FrameType {
    Training,
    Validation,
    Scoring
  }
  
  public void prepare(PipelineContext context) {}
  
  void prepare(PipelineContext context, TransformerChain chain) {
    assert chain != null;
    prepare(context);
    chain.nextPrepare(context);
  }
  
  
  public Frame transform(Frame fr) {
    return transform(fr, FrameType.Scoring, null);
  }

  /**
   * Transformers must implement this method. They can ignore type and context if they're not context-sensitive.
   * @param fr
   * @param type
   * @param context
   * @return
   */
  public abstract Frame transform(Frame fr, FrameType type, PipelineContext context);
  
  <R> R transform(Frame[] frames, FrameType[] types, PipelineContext context, Completer<R> completer, TransformerChain chain) {
    assert frames != null;
    assert types != null;
    assert frames.length == types.length;
    assert chain != null;
    Frame[] transformed = new Frame[frames.length];
    for (int i=0; i<frames.length; i++) {
      Frame fr = frames[i];
      transformed[i] = fr == null ? null : transform(fr, types[i], context);
    }
    return chain.nextTransform(transformed, types, context, completer);
  }

}
