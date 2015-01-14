package water.api;

import water.*;
import water.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class LogsHandler extends Handler {
  private static class GetLogTask extends DTask<GetLogTask> {
    public String filename;
    public String log;

    public boolean success = false;

    public GetLogTask() {
      log = null;
    }

    @Override public void compute2() {
      String logPathFilename;
      try {
        logPathFilename = Log.getLogDir() + File.separator + filename;
        File f = new File(logPathFilename);
        if (! f.exists()) {
          throw new IllegalArgumentException("File does not exist");
        }
        if (! f.canRead()) {
          throw new IllegalArgumentException("File is not readable");
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
        success = true;
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }

      tryComplete();
    }

    @Override public byte priority() {
      return H2O.MIN_HI_PRIORITY;
    }
  }

  @Override protected int min_ver() { return 3; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public LogsV3 fetch(int version, LogsV3 s) {
    int nodeidx = s.nodeidx;
    if ((nodeidx < 0) || (nodeidx >= H2O.CLOUD.size())) {
      throw new IllegalArgumentException("node does not exist");
    }

    String filename;
    try {
      filename = (s.filename != null) ? s.filename : water.util.Log.getLogFileName();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }

    if (filename.contains(File.separator)) {
      throw new IllegalArgumentException("filename may not contain File.separator character");
    }

    H2ONode node = H2O.CLOUD._memary[nodeidx];
    GetLogTask t = new GetLogTask();
    t.filename = filename;
    Log.trace("GetLogTask starting to node " + nodeidx + "...");
    new RPC<>(node, t).call().get();
    Log.trace("GetLogTask completed to node " + nodeidx);

    if (! t.success) {
      throw new RuntimeException("GetLogTask failed");
    }

    s.log = t.log;

    return s;
  }
}
