package ai.h2o.cascade.stdlib.core;

import ai.h2o.cascade.core.IdList;
import ai.h2o.cascade.stdlib.StdlibFunction;
import ai.h2o.cascade.vals.Val;
import water.Key;


/**
 * Load an object (probably a Frame) from DKV into the current scope.
 */
public class FnFromdkv extends StdlibFunction {

  public Val apply(IdList ids, String key) {
    if (ids.numIds() != 1)
      throw new ValueError(0, "Only one id should be supplied");
    String id = ids.getId(0);
    try {
      scope.importFromDkv(id, Key.make(key));
    } catch (IllegalArgumentException e) {
      throw new ValueError(1, e.getMessage());
    }
    return scope.lookup(id);
  }

}
