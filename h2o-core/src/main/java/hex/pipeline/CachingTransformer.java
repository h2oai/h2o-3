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
  public Frame transform(Frame fr, FrameType type, PipelineContext context) {
    if (isCacheEnabled() && _cache.containsKey(fr.getKey())) {
      return _cache.get(fr.getKey()).get();
    }
    Frame transformed = super.transform(fr, type, context);
    if (transformed.getKey() == null) {
      //todo: create one
    }
    _cache.put(fr.getKey(), transformed.getKey());
    return transformed;
  }
  
  public void clearCache() {
    _cache.clear();
  }
}
