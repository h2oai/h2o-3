package water.api;

import water.H2O;
import water.Key;
import water.Lockable;
import water.api.RemoveHandler.Remove;
import water.Iced;

public class RemoveHandler extends Handler<Remove,RemoveV1> {
  protected static final class Remove extends Iced {
    //Input
    Key key;
  }
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }
  @Override protected RemoveV1 schema(int version) { return new RemoveV1(); }
  @Override public void compute2() { throw H2O.unimpl(); }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public RemoveV1 remove(int version, Remove u) {
    Lockable.delete(u.key);
    return schema(version).fillFromImpl(u);
  }
}
