package water.api;

import water.H2O;
import water.MRTask;
import water.api.schemas3.LogAndEchoV3;
import water.util.Log;

public class LogAndEchoHandler extends Handler {

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public LogAndEchoV3 echo(int version, final LogAndEchoV3 u) {
    new MRTask(H2O.MIN_HI_PRIORITY) {
      @Override public void setupLocal() {
        Log.info(u.message);
      }
    }.doAllNodes();

    return u;
  }
}