package hex.pipeline.transformers;

import hex.Model;
import hex.ModelBuilder;
import hex.pipeline.PipelineContext;
import water.*;
import water.KeyGen.PatternKeyGen;
import water.fvec.Frame;
import water.util.IcedHashMap;
import water.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ModelAsFeatureTransformer<S extends ModelAsFeatureTransformer<S, M, MP>, M extends Model<M, MP, ?>, MP extends Model.Parameters> extends FeatureTransformer<S> {
  
  private static final Set<String> IGNORED_FIELDS_FOR_CHECKSUM = new HashSet<String>(Arrays.asList(
          "_modelKey", "_modelsCacheKey"
  ));
  
  protected MP _params;
  private Key<M> _modelKey;
  
  private boolean _cacheEnabled;
  private Key<ModelsCache<M>> _modelsCacheKey;
  private final KeyGen _modelKeyGen;
  private final int _model_type;
  
  

  protected ModelAsFeatureTransformer() {
    this(null);
  }

  public ModelAsFeatureTransformer(MP params) {
    this(params, null);
  }

  public ModelAsFeatureTransformer(MP params, Key<M> modelKey) {
    _params = params;
    _modelKey = modelKey;
    _modelKeyGen = modelKey == null 
            ? new PatternKeyGen("{0}_{n}_model")    // if no modelKey provided, then a new key and its corresponding model is trained for each 
            : new KeyGen.ConstantKeyGen(modelKey);  // if modelKey provided, only use that one
    _model_type = params == null ? TypeMap.NULL : TypeMap.getIcedId(params.javaName());
  }
  
  public S enableCache() {
    _cacheEnabled = true;
    return (S) this;
  }

  @Override
  public S init() {
    S self = super.init();
    if (_cacheEnabled && _modelsCacheKey == null) {
      _modelsCacheKey = Key.make(name()+"_cache");
      DKV.put(new ModelsCache<M>(_modelsCacheKey));
    }
    return self;
  }

  public M getModel() {
    return _modelKey == null ? null : _modelKey.get();
  }
  
  private ModelsCache<M> getCache() {
    return _modelsCacheKey == null ? null : _modelsCacheKey.get();
  }

  @SuppressWarnings("unchecked")
  protected Key<M> lookupModel(MP params) {
    long cs = params.checksum();
    ModelsCache<M> cache = getCache();
    Key<M> k = cache == null ? null : cache.get(cs);
    if (k != null && k.get() != null) return k;
    return KeySnapshot.globalSnapshot().findFirst(ki -> {
      if (ki._type != _model_type) return false;
      M m = (M) ki._key.get();
      return m != null && m._parms != null && m._output != null && m._output._end_time > 0 && cs == m._parms.checksum();
    });
  }


  @Override
  public Object getParameter(String name) {
    try {
      return _params.getParameter(name);
    } catch (IllegalArgumentException iae) {
      return super.getParameter(name);
    }
  }

  @Override
  public void setParameter(String name, Object value) {
    try {
      _modelKey = null; //consider this as a completely new transformer as soon as we're trying new hyper-parameters.
      _params.setParameter(name, value);
    } catch (IllegalArgumentException iae) {
      super.setParameter(name, value);
    }
  }

  @Override
  public Object getParameterDefaultValue(String name) {
    try {
      return _params.getParameterDefaultValue(name);
    } catch (IllegalArgumentException iae) {
      return super.getParameterDefaultValue(name);
    }
  }

  protected void doPrepare(PipelineContext context) {
    if (getModel() != null) return; // if modelKey was provided, use it immediately. 
    prepareModelParams(context);
    excludeColumns(_params.getNonPredictors());
    Key<M> km = lookupModel(_params);
    if (km == null || (_modelKey != null && !km.equals(_modelKey))) {
      if (_modelKey == null) _modelKey = _modelKeyGen.make(name());
      ModelBuilder<M, MP, ?> mb = ModelBuilder.make(_params, _modelKey);
      mb.trainModel().get();
      ModelsCache<M> cache = getCache();
      if (cache != null) cache.put(_params.checksum(), _modelKey);
    } else {
      _modelKey = km;
    }
  }
  
  protected void prepareModelParams(PipelineContext context) {
    assert context != null;
    // to train the model, we use the train frame from context by default

    if (_params._train == null) { // do not propagate params from context otherwise as they were defined for the default training frame
      assert context.getTrain() != null;
      _params._train = context.getTrain().getKey();
      if (_params._valid == null && context.getValid() != null) _params._valid = context.getValid().getKey();
      if (_params._response_column == null) _params._response_column = context._params._response_column;
      if (_params._weights_column == null) _params._weights_column = context._params._weights_column;
      if (_params._offset_column == null) _params._offset_column = context._params._offset_column;
      if (_params._ignored_columns == null) _params._ignored_columns = context._params._ignored_columns;
      if (isCVSensitive()) {
        MP defaults = ModelBuilder.makeParameters(_params.algoName());
        if (_params._fold_column == null) _params._fold_column = context._params._fold_column;
        if (_params._fold_assignment == defaults._fold_assignment)
          _params._fold_assignment = context._params._fold_assignment;
        if (_params._nfolds == defaults._nfolds) _params._nfolds = context._params._nfolds;
      }
    }
  }

  @Override
  protected Frame doTransform(Frame fr, FrameType type, PipelineContext context) {
    validateTransform();
    return fr == null ? null : getModel().transform(fr);
  }
  
  protected void validateTransform() {
    assert getModel() != null;
  }

  @Override
  protected S cloneImpl() throws CloneNotSupportedException {
    ModelAsFeatureTransformer<S, M, MP> clone = super.cloneImpl();
    clone._params = _params == null ? null : (MP) _params.clone();
    return (S) clone;
  }

  @Override
  protected Futures remove_impl(Futures fs, boolean cascade) {
    if (cascade) {
      Keyed.removeQuietly(_modelKey); _modelKey = null;
      Keyed.removeQuietly(_modelsCacheKey); _modelsCacheKey = null;
    }
    return super.remove_impl(fs, cascade);
  }

  @Override
  protected Set<String> ignoredFieldsForChecksum() {
    Set<String> ignored = new HashSet<>(super.ignoredFieldsForChecksum());
    ignored.addAll(IGNORED_FIELDS_FOR_CHECKSUM);
    return ignored;
  }
  
  private static class ModelsCache<M extends Model> extends Keyed<ModelsCache<M>> {
    private IcedHashMap<Long, Key<M>> _modelsCache;

    public ModelsCache(Key<ModelsCache<M>> key) {
      super(key);
      _modelsCache = new IcedHashMap<>();
    }

    @Override
    protected Futures remove_impl(Futures fs, boolean cascade) {
      if (cascade) {
        Log.info("Clearing models cache "+_key+": "+_modelsCache.values());
        for (Key k : _modelsCache.values()) {
          Keyed.removeQuietly(k);
        }
        _modelsCache.clear();
      }
      return super.remove_impl(fs, cascade);
    }
    
    private Key<M> get(Long cacheKey) {
      return _modelsCache.get(cacheKey);
    }
    
    private void put(Long cacheKey, Key<M> key) {
      new PutInCache<>(cacheKey, key).invoke(getKey());
    }
    
    private static class PutInCache<M extends Model> extends TAtomic<ModelsCache<M>> {
      
      Long cacheKey;
      Key<M> modelKey;
      public PutInCache(Long cacheKey, Key<M> key) {
        this.cacheKey = cacheKey;
        this.modelKey = key;
      }

      @Override
      protected ModelsCache<M> atomic(ModelsCache<M> old) {
        old._modelsCache.put(cacheKey, modelKey);
        return old;
      }
    }
  }
}
