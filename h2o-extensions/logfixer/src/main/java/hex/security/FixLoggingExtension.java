package hex.security;

import water.AbstractH2OExtension;
import water.H2O;
import water.Paxos;
import water.util.Log;
import water.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;


/**
 * Extension used for checking failed nodes
 */
public class FixLoggingExtension extends AbstractH2OExtension {

  private static final int POLLING_INTERVAL_MS = 1000;

  @Override
  public String getExtensionName() {
    return "FixLogging";
  }

  @Override
  public void onLocalNodeStarted() {
    new WaitForClusterThread().start();
  }

  private class WaitForClusterThread extends Thread {
    public WaitForClusterThread() {
      super("WaitForClusterThread");
    }

    @Override
    public void run() {
      try {
        while (true) {
          sleep(POLLING_INTERVAL_MS);
          if (Paxos._cloudLocked) {
            Object init_msgs = ReflectionUtils.getFieldValue(Log.class, "INIT_MSGS");
            if (init_msgs == null || true) {
              Field f = ReflectionUtils.findNamedField(Log.class, "INIT_MSGS");
              f.set(Log.class, new ArrayList<String>());
              Log.info("Logging prefix, we should see logging messages with a correct header " + H2O.SELF_ADDRESS + ":" + H2O.H2O_PORT);
              break;
            }
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

}
