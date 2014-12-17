package water.api;

import water.DKV;
import water.Lockable;

public class RemoveHandler extends Handler {
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public RemoveV1 remove(int version, RemoveV1 u) {
    try {
      Lockable.delete(u.key.key());
    } catch (ClassCastException e) {
      DKV.remove(u.key.key());
  }
    return u;
  }
}
