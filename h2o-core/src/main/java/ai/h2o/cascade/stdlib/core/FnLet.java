package ai.h2o.cascade.stdlib.core;

import ai.h2o.cascade.core.CorporealFrame;
import ai.h2o.cascade.core.IdList;
import ai.h2o.cascade.core.Val;
import ai.h2o.cascade.stdlib.StdlibFunction;

/**
 * Bind value to a variable.
 */
public class FnLet extends StdlibFunction {

  public Val apply(IdList id, Val value) {
    if (id.numIds() != 1 || id.hasVarargId())
      throw new ValueError(0, "Only one id is required");
    String name = id.getId(0);
    return apply(name, value);
  }

  public Val apply(String name, Val value) {
    Val currValue = scope.lookupVariable(name);
    if (currValue != null) {
      // Variable with such name already exists -- need to perform an appropriate cleanup
      if (currValue instanceof CorporealFrame) {
        ((CorporealFrame) currValue).dispose(scope);
      }
    }
    if (value instanceof CorporealFrame) {
      scope.session().untrackCorporealFrame((CorporealFrame) value);
    }
    scope.addVariable(name, value);
    return value;
  }

}
