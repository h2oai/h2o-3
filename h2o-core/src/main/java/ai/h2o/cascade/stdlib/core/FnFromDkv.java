package ai.h2o.cascade.stdlib.core;

import ai.h2o.cascade.vals.IdList;
import ai.h2o.cascade.stdlib.StdlibFunction;
import ai.h2o.cascade.stdlib.frame.FnClone;
import ai.h2o.cascade.vals.Val;
import ai.h2o.cascade.vals.ValFrame;
import water.DKV;
import water.Value;
import water.fvec.Frame;


/**
 * Retrieve object with given key from the DKV, and make it available in the
 * current scope under the provided name. This import has "copy" semantics,
 * in the sense that a deep copy of the DKV object is created, and Cascade
 * doesn't assume ownership of the original object.
 */
public class FnFromDkv extends StdlibFunction {

  public Val apply(IdList ids, String key) {
    if (ids.numIds() != 1)
      throw new ValueError(0, "Only one id should be supplied");
    String id = ids.getId(0);

    Value value = DKV.get(key);
    if (value == null)
      throw new ValueError(1, "Key not found in the DKV");
    if (!value.isFrame())
      throw new ValueError(1, "Key refers to an object of type " + value.theFreezableClass().getSimpleName());

    Frame originalFrame = value.get();
    Frame clonedFrame = FnClone.cloneFrame(originalFrame, scope.session().<Frame>mintKey());
    Val val = new ValFrame(clonedFrame);
    val.getFrame().makeReadonly();  // set the readonly flag on the frame before storing it in the scope
    scope.addVariable(id, val);

    return val;
  }

}
