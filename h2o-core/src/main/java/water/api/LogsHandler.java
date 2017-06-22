package water.api;

import water.*;
import water.api.schemas3.LogsV3;
import water.util.LinuxProcFileReader;
import water.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class LogsHandler extends Handler {
  private static class GetLogTask extends DTask<GetLogTask> {
    public String name;
    public String log;

    public boolean success = false;

    public GetLogTask() {
      super(H2O.GUI_PRIORITY);
      log = null;
    }

    public void doIt() {
      String logPathFilename = "/undefined";        // Satisfy IDEA inspection.
      try {
        if (name == null || name.equals("default")) {
          name = "debug";
        }

        switch (name) {
          case "stdout":
          case "stderr":
            LinuxProcFileReader lpfr = new LinuxProcFileReader();
            lpfr.read();
            if (!lpfr.valid()) {
              log = "This option only works for Linux hosts";
            } else {
              String pid = lpfr.getProcessID();
              String fdFileName = "/proc/" + pid + "/fd/" + (name.equals("stdout") ? "1" : "2");
              File f = new File(fdFileName);
              logPathFilename = f.getCanonicalPath();
              if (logPathFilename.startsWith("/dev")) {
                log = "Unsupported when writing to console";
              }
              if (logPathFilename.startsWith("socket")) {
                log = "Unsupported when writing to a socket";
              }
              if (logPathFilename.startsWith("pipe")) {
                log = "Unsupported when writing to a pipe";
              }
              if (logPathFilename.equals(fdFileName)) {
                log = "Unsupported when writing to a pipe";
              }
              Log.trace("LogPathFilename calculation: " + logPathFilename);
            }
            break;
          case "trace":
          case "debug":
          case "info":
          case "warn":
          case "error":
          case "fatal":
            Log.Level level = Log.Level.fromString(name);
            if(!Log.isLoggingFor(level)){
              log = "Logging for "+ name.toUpperCase() + " is not enabled as the log level is set to " + level + ".";
            } else {
              if (!Log.isLoggerInitialized()) {
                log = "H2O logging not configured.";
              } else {
                logPathFilename = Log.getLogFilePath(level);
              }
            }
            break;
          case "httpd":
            if (!Log.isLoggerInitialized()) {
              log = "H2O logging not configured.";
            } else {
              logPathFilename = Log.getLogFilePath(Log.Level.fromString(name));
            }
            break;
          default:
            throw new IllegalArgumentException("Illegal log file name requested (try 'default')");
        }

        if (log == null) {
          File f = new File(logPathFilename);
          if (!f.exists()) {
            throw new IllegalArgumentException("File " + f + " does not exist");
          }
          if (!f.canRead()) {
            throw new IllegalArgumentException("File " + f + " is not readable");
          }

          BufferedReader reader = new BufferedReader(new FileReader(f));
          String line;
          StringBuilder sb = new StringBuilder();

          line = reader.readLine();
          while (line != null) {
            sb.append(line);
            sb.append("\n");
            line = reader.readLine();
          }
          reader.close();

          log = sb.toString();
        }

        success = true;
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override public void compute2() {
      doIt();
      tryComplete();
    }
  }


  private static H2ONode getH2ONodeFromIdx(int nodeIdx){
      if ((nodeIdx < -1) || (nodeIdx >= H2O.CLOUD.size())) {
        throw new IllegalArgumentException("H2O node with the specified index does not exist!");
      } else if(nodeIdx == -1) {
        return H2O.SELF;
      } else {
        return H2O.CLOUD._memary[nodeIdx];
      }
  }

  private static H2ONode getH2ONodeFromIPPort(String ipPort) {
    // not a number, try to parse ipPort
    if (ipPort.equals("self")) {
      return H2O.SELF;
    } else {
      H2ONode node = H2O.CLOUD.getNodeByIpPort(ipPort);
      if (node != null) {
        return node;
      } else {
        // it still can be client
        H2ONode client = H2O.getClientByIPPort(ipPort);
        if (client != null) {
          return client;
        } else {
          // the ipport does not represent any existing h2o cloud member or client
          throw new IllegalArgumentException("No H2O node running as part of this cloud on " + ipPort + " does not exist!");
        }
      }
    }
  }

  private static boolean isNodeIndex(String nodeRef){
    try {
      int numNodeIdx = Integer.parseInt(nodeRef);
      return true;
    }catch (NumberFormatException nfe) {
      return false;
    }
  }

  private static H2ONode getH2ONode(String nodeRef) {
   if(isNodeIndex(nodeRef)) {
    return getH2ONodeFromIdx(Integer.parseInt(nodeRef));
   } else {
      return getH2ONodeFromIPPort(nodeRef);
    }
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public LogsV3 fetch(int version, LogsV3 s) {


    H2ONode node = getH2ONode(s.nodeidx);
    String filename = s.name;
    if (filename != null) {
      if (filename.contains(File.separator)) {
        throw new IllegalArgumentException("Filename may not contain File.separator character.");
      }
    }

    GetLogTask t = new GetLogTask();
    t.name = filename;
    if (H2O.SELF.equals(node)) {
      // Local node.
      try {
        t.doIt();
      }
      catch (Exception e) {
        Log.err(e);
      }
    } else {
      // Remote node.
      Log.trace("GetLogTask starting to node  " + node._key + " ...");
      new RPC<>(node, t).call().get();
      Log.trace("GetLogTask completed to node " + node._key);
    }

    if (!t.success) {
      throw new RuntimeException("GetLogTask failed");
    }

    s.log = t.log;

    return s;
  }
}
