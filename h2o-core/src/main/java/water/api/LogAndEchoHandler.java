package water.api;

import water.util.Log;

public class LogAndEchoHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public LogAndEchoV3 echo(int version, LogAndEchoV3 u) { Log.info(u.message); return u; }
}
