package water.api;

public class UnlockKeysHandler extends Handler {
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public UnlockKeysV2 unlock(int version, UnlockKeysV2 u) {
    new UnlockTask().doAllNodes();
    return u;
  }
}
