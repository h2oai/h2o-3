package water.api;

public class UnlockKeysHandler extends Handler<UnlockKeysHandler,UnlockKeysV2> {
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }
  @Override protected UnlockKeysV2 schema(int version) { return new UnlockKeysV2(); }
  @Override public void compute2() {
    new UnlockTask().doAllNodes();
  }
}