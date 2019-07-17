package water.api;

import water.*;
import water.api.schemas3.LogsV3;
import water.util.*;

import java.io.*;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;

import static water.api.RequestServer.HTTP_OK;

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
            if (!Log.isLoggingFor(name)) {
              log = "Logging for " + name.toUpperCase() + " is not enabled as the log level is set to " + Log.LVLS[Log.getLogLevel()] + ".";
            } else {
              try {
                logPathFilename = Log.getLogFilePath(name);
              } catch (Exception e) {
                log = "H2O logging not configured.";
              }
            }
            break;
          case "httpd":
            try {
              logPathFilename = Log.getLogFilePath(name);
            } catch (Exception e) {
              log = "H2O logging not configured.";
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
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void compute2() {
      doIt();
      tryComplete();
    }
  }

  private static H2ONode getH2ONode(String nodeIdx) {
    try {
      int numNodeIdx = Integer.parseInt(nodeIdx);

      if ((numNodeIdx < -1) || (numNodeIdx >= H2O.CLOUD.size())) {
        throw new IllegalArgumentException("H2O node with the specified index does not exist!");
      } else if (numNodeIdx == -1) {
        return H2O.SELF;
      } else {
        return H2O.CLOUD._memary[numNodeIdx];
      }
    } catch (NumberFormatException nfe) {
      // not a number, try to parse for ipPort
      if (nodeIdx.equals("self")) {
        return H2O.SELF;
      } else {
        H2ONode node = H2O.CLOUD.getNodeByIpPort(nodeIdx);
        if (node != null) {
          return node;
        } else {
          // it still can be client
          H2ONode client = H2O.getClientByIPPort(nodeIdx);
          if (client != null) {
            return client;
          } else {
            // the ipport does not represent any existing h2o cloud member or client
            throw new IllegalArgumentException("No H2O node running as part of this cloud on " + nodeIdx + " does not exist!");
          }
        }
      }
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
      } catch (Exception e) {
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

  public static URI downloadLogs(String destinationDir, LogArchiveContainer logContainer) {
    String outputFileStem = getOutputLogStem();
    String outputFileName = outputFileStem + "." + logContainer.getFileExtension();
    byte[] logBytes = downloadLogs(logContainer, outputFileStem);
    File destination = new File(destinationDir, outputFileName);

    try (FileOutputStream fileOutputStream = new FileOutputStream(destination)) {
      fileOutputStream.write(logBytes);
    } catch (IOException e) {
      Log.err("Can't write logs to " + destinationDir + ", reason: \n" + e);
    }
    return destination.toURI();
  }

  static NanoResponse downloadLogsViaRestAPI(LogArchiveContainer logContainer) {
    String outputFileStem = getOutputLogStem();
    byte[] finalArchiveByteArray = downloadLogs(logContainer, outputFileStem);

    NanoResponse res = new NanoResponse(HTTP_OK, logContainer.getMimeType(), new ByteArrayInputStream(finalArchiveByteArray));
    res.addHeader("Content-Length", Long.toString(finalArchiveByteArray.length));
    res.addHeader("Content-Disposition", "attachment; filename=" + outputFileStem + "." + logContainer.getFileExtension());
    return res;
  }
  
  private static byte[] downloadLogs(LogArchiveContainer logContainer, String outputFileStem) {
    Log.info("\nCollecting logs.");

    byte[][] workersLogs = getWorkersLogs(logContainer);
    byte[] clientLogs = getClientLogs(logContainer);

    try {
      return archiveLogs(logContainer, new Date(), workersLogs, clientLogs, outputFileStem);
    } catch (Exception e) {
      return StringUtils.toBytes(e);
    }
  }

  private static String getOutputLogStem() {
    String pattern = "yyyyMMdd_hhmmss";
    SimpleDateFormat formatter = new SimpleDateFormat(pattern);
    String now = formatter.format(new Date());
    return "h2ologs_" + now;
  }

  private static byte[][] getWorkersLogs(LogArchiveContainer logContainer) {
    H2ONode[] members = H2O.CLOUD.members();
    byte[][] perNodeArchive = new byte[members.length][];

    for (int i = 0; i < members.length; i++) {
      try {
        // Skip nodes that aren't healthy, since they are likely to cause the entire process to hang.
        if (members[i].isHealthy()) {
          GetLogsFromNode g = new GetLogsFromNode(i, logContainer);
          g.doIt();
          perNodeArchive[i] = g.bytes;
        } else {
          perNodeArchive[i] = StringUtils.bytesOf("Node not healthy");
        }
      } catch (Exception e) {
        perNodeArchive[i] = StringUtils.toBytes(e);
      }
    }
    return perNodeArchive;
  }

  private static byte[] getClientLogs(LogArchiveContainer logContainer) {
    if (H2O.ARGS.client) {
      try {
        GetLogsFromNode g = new GetLogsFromNode(-1, logContainer);
        g.doIt();
        return g.bytes;
      } catch (Exception e) {
        return StringUtils.toBytes(e);
      }
    }
    return null;
  }

  private static byte[] archiveLogs(LogArchiveContainer container, Date now,
                                    byte[][] results, byte[] clientResult, String topDir) throws IOException {
    int l = 0;
    assert H2O.CLOUD._memary.length == results.length : "Unexpected change in the cloud!";
    for (byte[] result : results) l += result.length;
    ByteArrayOutputStream baos = new ByteArrayOutputStream(l);

    try (LogArchiveWriter archive = container.createLogArchiveWriter(baos)) {

      // Add top-level directory.
      LogArchiveWriter.ArchiveEntry entry = new LogArchiveWriter.ArchiveEntry(topDir + File.separator, now);
      archive.putNextEntry(entry);

      // Archive directory from each cloud member.
      for (int i = 0; i < results.length; i++) {
        String filename =
            topDir + File.separator +
                "node" + i + "_" +
                H2O.CLOUD._memary[i].getIpPortString().replace(':', '_').replace('/', '_') +
                "." + container.getFileExtension();
        LogArchiveWriter.ArchiveEntry ze = new LogArchiveWriter.ArchiveEntry(filename, now);
        archive.putNextEntry(ze);
        archive.write(results[i]);
        archive.closeEntry();
      }

      // Archive directory from the client node.  Name it 'driver' since that's what Sparking Water users see.
      if (clientResult != null) {
        String filename =
            topDir + File.separator +
                "driver." + container.getFileExtension();
        LogArchiveWriter.ArchiveEntry ze = new LogArchiveWriter.ArchiveEntry(filename, now);
        archive.putNextEntry(ze);
        archive.write(clientResult);
        archive.closeEntry();
      }

      // Close the top-level directory.
      archive.closeEntry();
    }

    return baos.toByteArray();
  }

}
