package water.api;

import water.Key;
import water.api.schemas3.InitIDV3;
import water.rapids.Session;

import java.util.HashMap;

public class InitIDHandler extends Handler {
  // For now, only 1 active Rapids session-per-cloud.  Needs to be per-session
  // id... but clients then need to announce which session with each rapids call

  static HashMap<String, Session> SESSIONS = new HashMap<>();

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public InitIDV3 issue(int version, InitIDV3 p) {
    p.session_key = "_sid" + Key.make().toString().substring(0,5);
    return p;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public InitIDV3 endSession(int version, InitIDV3 p) {
    if( SESSIONS.get(p.session_key) != null ) {
      try { SESSIONS.get(p.session_key).end(null); SESSIONS.remove(p.session_key); }
      catch( Throwable ex ) { throw SESSIONS.get(p.session_key).endQuietly(ex); }
    }
    return p;
  }
}
