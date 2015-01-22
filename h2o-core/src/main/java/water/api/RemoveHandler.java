package water.api;

import water.DKV;
import water.Keyed;
import water.Lockable;

public class RemoveHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public RemoveV1 remove(int version, RemoveV1 u) {
    Keyed val = DKV.getGet(u.key.key());
    if (val != null) {
      if (val instanceof Lockable) ((Lockable) val).delete(); // Fails if object already locked
      else val.remove(); // Unconditional delete
    }
    return u;
  }
}
