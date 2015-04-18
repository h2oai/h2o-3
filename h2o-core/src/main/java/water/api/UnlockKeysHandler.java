package water.api;

public class UnlockKeysHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public UnlockKeysV3 unlock(int version, UnlockKeysV3 u) {
    new UnlockTask().doAllNodes();
    return u;
  }
}
