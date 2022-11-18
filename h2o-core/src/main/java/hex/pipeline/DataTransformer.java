package hex.pipeline;

import hex.Parameterizable;
import hex.pipeline.TransformerChain.Completer;
import water.Futures;
import water.Iced;
import water.Key;
import water.fvec.Frame;
import water.util.PojoUtils;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class DataTransformer<SELF extends DataTransformer> extends Iced<SELF> implements Parameterizable {
  
  public enum FrameType {
    Training,
    Validation,
    Scoring
  }

  public boolean _enabled = true;  // flag allowing to enable/disable transformers dynamically esp. in pipelines (can be used as a pipeline hyperparam in grids).
  private String _id;
  private AtomicInteger refCount;

  public DataTransformer() {
    this(null);
  }
  
  public DataTransformer(String id) {
    _id = id == null ? getClass().getSimpleName().toLowerCase()+Key.rand() : id;
    reset();
  }
  
  @SuppressWarnings("unchecked")
  public SELF id(String id) {
    _id = id;
    return (SELF) this;
  }
  
  public String id() {
    return _id;
  }

  @SuppressWarnings("unchecked")
  public SELF enable(boolean enabled) {
    _enabled = enabled;
    return (SELF) this;
  }
  
  public boolean enabled() { return _enabled; }

  @Override
  public boolean hasParameter(String name) {
    try {
      getParameter(name);
      return true;
    } catch (Exception e) {
      return false;
    }
  }
  
  @Override
  public Object getParameter(String name) {
    return PojoUtils.getFieldValue(this, name);
  }

  @Override
  public void setParameter(String name, Object value) {
    PojoUtils.setField(this, name, value);
  }

  @Override
  public boolean isParameterSetToDefault(String name) {
    Object val = getParameter(name);
    Object defaultVal = getParameterDefaultValue(name);
    return Objects.deepEquals(val, defaultVal);
  }

  @Override
  public Object getParameterDefaultValue(String name) {
    return getDefaults().getParameter(name);
  }

  @Override
  public boolean isValidHyperParameter(String name) {
    return isParameterSetToDefault(name);
  }
  
  /** private use only to avoid this getting mutated. */
  private transient DataTransformer _defaults;

  /** private use only to avoid this getting mutated. */
  private DataTransformer getDefaults() {
    if (_defaults == null) {
      _defaults = makeDefaults();
    }
    return _defaults;
  }
  
  protected abstract DataTransformer makeDefaults();

  /**
   * @return true iff the transformer needs to be applied in a specific way to training/validation frames during cross-validation.
   */
  public boolean isCVSensitive() {
    return false;
  }
  
  public void prepare(PipelineContext context) {
    if (_enabled) doPrepare(context);
    refCount.incrementAndGet();
  }
  
  protected void doPrepare(PipelineContext context) {} 
  
  void prepare(PipelineContext context, TransformerChain chain) {
    assert chain != null;
    prepare(context);
    chain.nextPrepare(context);
  }
  
  public void cleanup() {
    cleanup(new Futures());
  }

  public void cleanup(Futures futures) {
    if (refCount.decrementAndGet() <= 0) doCleanup(futures);
  }
  protected void doCleanup(Futures futures) {}
  
  protected void reset() {
    refCount = new AtomicInteger(0);
  }
  
  public Frame transform(Frame fr) {
    return transform(fr, FrameType.Scoring, null);
  }
  
  public Frame transform(Frame fr, FrameType type, PipelineContext context) {
    if (!_enabled) return fr;
    return doTransform(fr, type, context);
  }

  /**
   * Transformers must implement this method. They can ignore type and context if they're not context-sensitive.
   * @param fr
   * @param type
   * @param context
   * @return
   */
  protected abstract Frame doTransform(Frame fr, FrameType type, PipelineContext context);
  
  <R> R transform(Frame[] frames, FrameType[] types, PipelineContext context, Completer<R> completer, TransformerChain chain) {
    assert frames != null;
    assert types != null;
    assert frames.length == types.length;
    assert chain != null;
    Frame[] transformed = new Frame[frames.length];
    for (int i=0; i<frames.length; i++) {
      Frame fr = frames[i];
      FrameType type = types[i];
      Frame trfr = fr == null ? null : transform(fr, type, context);
      if (context != null && context._tracker != null) {
        context._tracker.apply(trfr, fr, type, context, this);
      } 
      transformed[i] = trfr;
    }
    return chain.nextTransform(transformed, types, context, completer);
  }

  @Override
  public int hashCode() {
    return 42; // FIXME !!! needed to get the checksum verification right in grids: externalizreuse logic in params.checksum
  }
}
