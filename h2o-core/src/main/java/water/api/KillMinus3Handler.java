package water.api;

import water.H2O;
import water.MRTask;
import water.api.schemas3.KillMinus3V3;
import water.exceptions.H2OIllegalArgumentException;

public class KillMinus3Handler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer

  private static String getProcessId() throws Exception {
    // Note: may fail in some JVM implementations
    // therefore fallback has to be provided

    // something like '<pid>@<hostname>', at least in SUN / Oracle JVMs
    final String jvmName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
    final int index = jvmName.indexOf('@');

    if (index < 1) {
      // part before '@' empty (index = 0) / '@' not found (index = -1)
      throw new Exception ("Can't get process Id");
    }

    return Long.toString(Long.parseLong(jvmName.substring(0, index)));
  }

  public KillMinus3V3 killm3(int version, KillMinus3V3 u) {
      new MRTask((byte)(H2O.MIN_HI_PRIORITY - 1)) {
        @Override public void setupLocal() {
          try {
            String cmd = "/bin/kill -3 " + getProcessId();
            java.lang.Runtime.getRuntime().exec(cmd);
          } catch( java.io.IOException ioe ) {
            // Silently ignore if, e.g. /bin/kill does not exist on windows
          } catch (Exception xe) {
            xe.printStackTrace();
            throw new H2OIllegalArgumentException("");
          }
        }
      }.doAllNodes();
    return u;
  }
}
