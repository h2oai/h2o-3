package water.api;

import water.Lockable;

public class RemoveHandler extends Handler {
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public RemoveV1 remove(int version, RemoveV1 u) {
    Lockable.delete(u.key);
    return u;
  }
}
