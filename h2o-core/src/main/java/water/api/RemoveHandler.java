package water.api;

import water.*;
import water.api.schemas3.DKVCleanV3;
import water.api.schemas3.RemoveV3;

public class RemoveHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public RemoveV3 remove(int version, RemoveV3 u) {
    Keyed val = DKV.getGet(u.key.key());
    if (val != null) {
      if (val instanceof Lockable) ((Lockable) val).delete(); // Fails if object already locked
      else val.remove(); // Unconditional delete
    }
    H2O.updateNotIdle();
    return u;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public DKVCleanV3 retainKeys(final int version, final DKVCleanV3 removeAllV3) {

    final Key[] retainedKeys;
    if (removeAllV3.retained_keys == null) {
      retainedKeys = new Key[0];
    } else {
      retainedKeys = new Key[removeAllV3.retained_keys.length];
      for (int i = 0; i < retainedKeys.length; i++) {
        if (removeAllV3.retained_keys[i] == null) continue; // Protection against null keys from the client - ignored
        retainedKeys[i] = removeAllV3.retained_keys[i].key();
      }
    }

    new DKV.ClearDKVTask(retainedKeys)
            .doAllNodes();

    return removeAllV3;
  }
}
