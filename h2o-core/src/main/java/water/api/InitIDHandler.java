package water.api;

import water.Key;

public class InitIDHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public InitIDV3 issue(int version, InitIDV3 p) {
    p.session_key = "_sid" + Key.make().toString();
    return p;
  }
}
