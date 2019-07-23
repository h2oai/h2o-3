package water.api;

import water.*;
import water.api.schemas3.RemoveV3;

public class RemoveHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public RemoveV3 remove(int version, RemoveV3 u) {
    Keyed val = DKV.getGet(u.key.key());
    if (val != null) {
      if (val instanceof Lockable) ((Lockable) val).delete(u.cascade); // Fails if object already locked
      else val.remove(u.cascade); // Unconditional delete
    }
    H2O.updateNotIdle();
    return u;
  }
}
