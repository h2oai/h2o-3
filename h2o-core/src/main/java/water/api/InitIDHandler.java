package water.api;

import water.Key;

public class InitIDHandler extends Handler {
  // For now, only 1 active Rapids session-per-cloud.  Needs to be per-session
  // id... but clients then need to announce which session with each rapids call

  static water.rapids.Session SESSION = null;

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public InitIDV3 issue(int version, InitIDV3 p) {
    p.session_key = "_sid" + Key.make().toString();
    return p;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public InitIDV3 endSession(int version, InitIDV3 p) {
    if( SESSION != null ) {
      try { SESSION.end(null); }
      catch( Throwable ex ) { throw SESSION.endQuietly(ex); }
    }
    SESSION = null;
    return p;
  }
}
