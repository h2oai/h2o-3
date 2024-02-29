package hex.pipeline;

import hex.Parameterizable;
import hex.pipeline.TransformerChain.Completer;
import water.*;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.Checksum;
import water.util.IcedLong;
import water.util.PojoUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public abstract class DataTransformer<SELF extends DataTransformer<SELF>> extends Lockable<SELF> implements Parameterizable<SELF> {
  
  public enum FrameType {
    Training,
    Validation,
    Test
  }
  
  private static final Set<String> IGNORED_FIELDS_FOR_CHECKSUM = new HashSet<String>(Arrays.asList(
          "_key"
  ));

  public boolean _enabled = true;  // flag allowing to enable/disable transformers dynamically esp. in pipelines (can be used as a pipeline hyperparam in grids).
  private String _name;
  private String _description;
  private Key _refCountKey;
  private KeyGen _keyGen;

  protected DataTransformer() {
    this(null);
  }
  
  public DataTransformer(String name) {
    this(name, null);
  }

  public DataTransformer(String name, String description) {
    super(null);
    _name = name == null ? getClass().getSimpleName().toLowerCase()+Key.rand() : name;
    _description = description == null ? getClass().getSimpleName().toLowerCase() : description;
  }
  
  @SuppressWarnings("unchecked")
  public SELF name(String name) {
    _name = name;
    return (SELF) this;
  }
  
  public String name() {
    return _name;
  }

  @SuppressWarnings("unchecked")
  public SELF description(String description) {
    _description = description;
    return (SELF) this;
  }
  
  public String description() {
    return _description;
  }

  @SuppressWarnings("unchecked")
  public SELF enable(boolean enabled) {
    _enabled = enabled;
    return (SELF) this;
  }
  
  public SELF init() {
    assert _name != null;
    if (_keyGen == null) {
       _keyGen = new KeyGen.PatternKeyGen("{0}_{n}");
    }
    if (_refCountKey == null) {
      _refCountKey = Key.make(_name+ "_refCount");
      DKV.put(_refCountKey, new IcedLong(0));
    }
    if (_key == null || _key.get() == null) {
      _key = _keyGen.make(_name);
      DKV.put(this);
    }
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
  public boolean isParameterAssignable(String name) {
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
  
  protected DataTransformer makeDefaults() {
    try {
      return getClass().newInstance();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * @return true iff the transformer needs to be applied in a specific way to training/validation frames during cross-validation.
   */
  public boolean isCVSensitive() {
    return false;
  }
  
  public final void prepare(PipelineContext context) {
    if (_enabled) {
      if (_key != null) {
        Key jobKey = context == null ? null : context._jobKey;
        write_lock(jobKey);
        doPrepare(context);
        update(jobKey);
        unlock(jobKey);
      } else {
        doPrepare(context);
      }
    }
  }

  /**
   * Transformers can implement this method if it needs to preparation before being able to do the transformations.
   * @param context
   */
  protected void doPrepare(PipelineContext context) {} 
  
  protected void prepare(PipelineContext context, TransformerChain chain) {
    assert chain != null;
    prepare(context);
    chain.nextPrepare(context);
  }
  
  public final void cleanup() {
    cleanup(new Futures());
  }

  public final void cleanup(Futures futures) {
    if (_refCountKey == null || IcedLong.decrementAndGet(_refCountKey) <= 0) doCleanup(futures);
    if (_key != null) DKV.remove(_key);
  }
  protected void doCleanup(Futures futures) {
    remove(futures);
  }
  
  public final Frame transform(Frame fr) {
    return transform(fr, FrameType.Test, null);
  }
  
  public final Frame transform(Frame fr, FrameType type, PipelineContext context) {
    if (!_enabled || fr == null) return fr;
    Frame trfr = doTransform(fr, type, context);
    if (context != null && context._tracker != null) {
      context._tracker.apply(trfr, fr, type, context, this);
    } 
    return trfr;
  }
  
  protected final Frame[] transform(Frame[] frames, FrameType[] types, PipelineContext context) {
    assert frames != null;
    assert types != null;
    assert frames.length == types.length;
    Frame[] transformed = new Frame[frames.length];
    for (int i=0; i<frames.length; i++) {
      Frame fr = frames[i];
      FrameType type = types[i];
      transformed[i] = transform(fr, type, context);
    }
    return transformed;
  }
  
  /**
   * Transformers that need to clean up upon chain completion or failure, can override this method and wrap call 
   * to {@code chain.nextTransform(Frame[], FrameType[], PipelineContext, Completer)} in a {@code try-catch-finally} block.
   * @param frames
   * @param types
   * @param context
   * @param completer
   * @param chain
   * @return
   */
  protected <R> R transform(Frame[] frames, FrameType[] types, PipelineContext context, Completer<R> completer, TransformerChain chain) {
    assert chain != null;
    Frame[] transformed = transform(frames, types, context);
    return chain.nextTransform(transformed, types, context, completer);
  }
  
  /**
   * Transformers must implement this method. They can ignore type and context if they're not context-sensitive.
   * @param fr
   * @param type
   * @param context
   * @return
   */
  protected abstract Frame doTransform(Frame fr, FrameType type, PipelineContext context);

  @Override
  public SELF freshCopy() {
    SELF copy = clone();
    copy._key = _keyGen.make(_name);
    DKV.put(copy);
    IcedLong.incrementAndGet(_refCountKey);
    return copy;
  }

  @Override
  protected Futures remove_impl(Futures fs, boolean cascade) {
    if (_refCountKey != null) {
      DKV.remove(_refCountKey);
      _refCountKey = null;
    }
    return super.remove_impl(fs, cascade);
  }

  @Override
  public long checksum_impl() {
    return Checksum.checksum(this, ignoredFieldsForChecksum());
  }
  
  protected Set<String> ignoredFieldsForChecksum() {
    return IGNORED_FIELDS_FOR_CHECKSUM;
  }
}
