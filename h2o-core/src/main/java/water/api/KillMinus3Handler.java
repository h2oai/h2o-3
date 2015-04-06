package water.api;

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

  public KillMinus3V1 killm3(int version, KillMinus3V1 u) {
    try {
      String cmd = "/bin/kill -3 " + getProcessId();
      java.lang.Runtime.getRuntime().exec(cmd);
    }
    catch (Exception xe) {
      xe.printStackTrace();
      throw new H2OIllegalArgumentException("");
    }
    return u;
  }
}
