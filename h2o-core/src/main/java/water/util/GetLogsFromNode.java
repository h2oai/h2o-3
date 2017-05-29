package water.util;

import water.*;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipOutputStream;

/**
 * Get zipped log directory data from a node.
 * The intent here is to return a binary blob with the data for saving as a file.
 * This is used as part of the "/3/Logs/download" REST API.
 */
public class GetLogsFromNode extends Iced {
  /**
   * Node number to get logs from (starting at 0).
   */
  public int nodeidx;

  /**
   * Byte array containing a zipped file with the entire log directory.
   */
  public byte[] bytes;

  /**
   * Do the work.
   */
  public void doIt() {
    if (nodeidx == -1) {
      GetLogsTask t = new GetLogsTask();
      t.doIt();
      bytes = t._bytes;
    } else {
      H2ONode node = H2O.CLOUD._memary[nodeidx];
      GetLogsTask t = new GetLogsTask();
      Log.trace("GetLogsTask starting to node " + nodeidx + "...");
      // Synchronous RPC call to get ticks from remote (possibly this) node.
      new RPC<>(node, t).call().get();
      Log.trace("GetLogsTask completed to node " + nodeidx);
      bytes = t._bytes;
    }
  }

  private static class GetLogsTask extends DTask<GetLogsTask> {
    private byte[] _bytes;

    GetLogsTask() { super(H2O.MIN_HI_PRIORITY); _bytes = null; }

    public void doIt() {
      try {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);
        Set<String> filter = new HashSet<>(Arrays.asList(Log.getLogFileNames()));
        FileUtils.zipDir(Log.getLogDir(), filter, baos, zos);
        zos.close();
        baos.close();
        _bytes = baos.toByteArray();
      }
      catch (Exception e) {
        _bytes = StringUtils.toBytes(e);
      }
    }

    @Override public void compute2() {
      doIt();
      tryComplete();
    }
  }
}
