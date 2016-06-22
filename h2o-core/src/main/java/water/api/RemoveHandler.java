package water.api;

import water.DKV;
import water.Keyed;
import water.Lockable;
import water.api.schemas3.RemoveV3;

public class RemoveHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public RemoveV3 remove(int version, RemoveV3 u) {
    Keyed val = DKV.getGet(u.key.key());
    if (val != null) {
      if (val instanceof Lockable) ((Lockable) val).delete(); // Fails if object already locked
      else val.remove(); // Unconditional delete
    }
    return u;
  }
}
