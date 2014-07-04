package water.api;

import water.H2O;
import water.api.UnlockKeysHandler.UnlockKeys;
import water.Iced;

public class UnlockKeysHandler extends Handler<UnlockKeys,UnlockKeysV2> {
  protected static final class UnlockKeys extends Iced {

  }
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }
  @Override protected UnlockKeysV2 schema(int version) { return new UnlockKeysV2(); }
  @Override public void compute2() { throw H2O.unimpl(); }
  public UnlockKeysV2 unlock(int version, UnlockKeys u) {
    new UnlockTask().doAllNodes();
    return new UnlockKeysV2();
  }
}