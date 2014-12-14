package water.api;

import water.util.Log;

public class LogAndEchoHandler extends Handler {
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public LogAndEchoV1 echo(int version, LogAndEchoV1 u) { Log.info(u.message); return u; }
}
