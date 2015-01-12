package water.api;

import water.Key;

public class InitIDHandler extends Handler {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public InitIDV1 issue(int version, InitIDV1 p) {
    p.session_key = "_sid" + Key.make().toString();
    return p;
  }
}