package water.api;

public class UnlockKeysHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public UnlockKeysV2 unlock(int version, UnlockKeysV2 u) {
    new UnlockTask().doAllNodes();
    return u;
  }
}
