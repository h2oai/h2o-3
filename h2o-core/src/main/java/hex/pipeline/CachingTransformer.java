package hex.pipeline;

import water.Key;
import water.fvec.Frame;

import java.util.HashMap;
import java.util.Map;

public class CachingTransformer<T extends DataTransformer> extends DelegateTransformer<T> {
  
  boolean _cacheEnabled = true;
  transient Map<Key<Frame>, Key<Frame>> _cache = new HashMap<>();

  public CachingTransformer(T transformer) {
    super(transformer);
  }

  void enableCache(boolean enabled) {
    _cacheEnabled = enabled;
  }
  
  boolean isCacheEnabled() {
    return _cacheEnabled;
  }

  @Override
  protected Frame doTransform(Frame fr, FrameType type, PipelineContext context) {
    if (isCacheEnabled() && _cache.containsKey(fr.getKey())) { 
      // works only for simple transformations, not if it is type/context-sensitive
      return _cache.get(fr.getKey()).get();
    }
    Frame transformed = super.doTransform(fr, type, context);
    _cache.put(fr.getKey(), transformed.getKey());
    return transformed;
  }
  
  public void clearCache() {
    _cache.clear();
  }
}
