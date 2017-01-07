package ai.h2o.cascade.stdlib.core;

import ai.h2o.cascade.core.CorporealFrame;
import ai.h2o.cascade.core.IdList;
import ai.h2o.cascade.core.Val;
import ai.h2o.cascade.stdlib.StdlibFunction;

/**
 * Remove a variable from the current scope. If the variable doesn't exist,
 * this is a noop.
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnDel extends StdlibFunction {

  public void apply(IdList ids) {
    if (ids.hasVarargId())
      throw new ValueError(0, "Vararg is not supported here");
    int count = ids.numIds();
    for (int i = 0; i < count; ++i) {
      apply(ids.getId(i));
    }
  }

  public void apply(String name) {
    Val value = scope.lookupVariable(name);
    if (value != null) {
      // Variable with such name already exists -- need to perform a cleanup
      if (value instanceof CorporealFrame) {
        ((CorporealFrame) value).dispose(scope);
      }
    }
    scope.removeVariable(name);
  }

}
