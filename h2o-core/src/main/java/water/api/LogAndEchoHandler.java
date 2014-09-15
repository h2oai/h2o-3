package water.api;

import water.H2O;
import water.Iced;
import water.util.Log;

public class LogAndEchoHandler extends Handler<LogAndEchoHandler.LogAndEcho, LogAndEchoV1>{
  protected static final class LogAndEcho extends Iced {
    //Input
    String _message = "";
  }
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }
  @Override protected LogAndEchoV1 schema(int version) { return new LogAndEchoV1(); }
  @Override public void compute2() { throw H2O.unimpl(); }
  public LogAndEchoV1 echo(int version, LogAndEcho u) { Log.info(u._message); return schema(version).fillFromImpl(u); }
}