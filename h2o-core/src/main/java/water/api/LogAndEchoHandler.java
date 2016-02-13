package water.api;

import water.MRTask;
import water.util.Log;

public class LogAndEchoHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public LogAndEchoV3 echo(int version, final LogAndEchoV3 u) {
    new MRTask() { @Override public void setupLocal() { Log.info(u.message); } }.doAllNodes();
    return u;
  }
}