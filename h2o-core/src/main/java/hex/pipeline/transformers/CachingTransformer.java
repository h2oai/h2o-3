package hex.pipeline.transformers;

import hex.pipeline.DataTransformer;
import hex.pipeline.PipelineContext;
import water.DKV;
import water.Key;
import water.fvec.Frame;
import water.nbhm.NonBlockingHashMap;
import water.util.FrameUtils;

import java.util.Collection;

/**
 * WIP: not ready for production usage for now due to memory + frame lifecycle issues.
 * If a Frame is cached, then returning a shallow copy is not enough as the individual Vecs could then be removed from DKV.
 * Deep copy would however increase the memory cost of caching.
 */
public class CachingTransformer<S extends CachingTransformer<S, T>, T extends DataTransformer<T>> extends DelegateTransformer<S, T> {
  
  boolean _cacheEnabled = true;
  private final NonBlockingHashMap<Object, Key<Frame>> _cache = new NonBlockingHashMap<>();

  protected CachingTransformer() {}

  public CachingTransformer(T transformer) {
    super(transformer);
  }

  void enableCache(boolean enabled) {
    _cacheEnabled = enabled;
  }
  
  boolean isCacheEnabled() {
    return _cacheEnabled;
  }

  private Object makeCachingKey(Frame fr, FrameType type, PipelineContext context) {
    // this way, works only for simple transformations, not if it is type/context-sensitive. 
    // The most important is to have something that would prevent transformation again and again:
    // - for each model using the main training frame in AutoML.
    // - for each model scoring the validation or leaderboard frame in AutoML.
    // - for each cv-training frame based on the same main training frame for each model in AutoML. 
    // this makes about max 3+nfolds frame to cache, which is not much.
    // We can probably rely on the frame checksum instead of the frame key as a caching key.
    // 
//    return fr.getKey();
    return fr.checksum();
  }
  
  private Frame copy(Frame fr, Key<Frame> newKey) {
    Frame cp = new Frame(fr);
    cp._key = newKey;
    return cp;
  }

  @Override
  protected Frame doTransform(Frame fr, FrameType type, PipelineContext context) {
    if (!isCacheEnabled()) return super.doTransform(fr, type, context);
    
    final Object cachingKey = makeCachingKey(fr, type, context);
    if (_cache.containsKey(cachingKey)) { 
      Frame cached = _cache.get(cachingKey).get();
      if (cached == null) {
        _cache.remove(cachingKey);
      } else {
        return copy(cached, Key.make(cached.getKey()+".copy"));
      }
    }
    Frame transformed = super.doTransform(fr, type, context);
    Frame cached = copy(transformed, Key.make(fr.getKey()+".cached"));
    DKV.put(cached); //??? how can we guarantee that cached Frame(s)/Vec(s) won't be deleted at the end of a single model training?
                     // we want those to remain accessible as long as the cache is "alive",
                     // they should only be removed from DKV when cache (or DKV) is cleared explicitly.
                     // Could we flag some objects in DKV as "cached"/"persistent" and can be removed from DKV only through a special call to `DKV.remove`? Use special key?
    _cache.put(cachingKey, cached.getKey());
    return transformed;
  }
  
  public void clearCache() {
    FrameUtils.cleanUp((Collection)_cache.values());
    _cache.clear();
  }
}
