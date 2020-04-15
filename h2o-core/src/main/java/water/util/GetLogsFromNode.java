package water.util;

import water.*;

import java.io.*;

/**
 * Get zipped log directory data from a node.
 * The intent here is to return a binary blob with the data for saving as a file.
 * This is used as part of the "/3/Logs/download" REST API.
 */
public class GetLogsFromNode extends Iced {
  static final int MB = 1 << 20;
  static final int MAX_SIZE = 25 * MB;

  public GetLogsFromNode(int nodeidx, LogArchiveContainer container) {
    this.nodeidx = nodeidx;
    this.container = container;
  }

  // Input
  /**
   * Node number to get logs from (starting at 0).
   */
  private final int nodeidx;

  private final LogArchiveContainer container;
  
  // Output
  /**
   * Byte array containing a archived file with the entire log directory.
   */
  public byte[] bytes;

  /**
   * Do the work.
   */
  public void doIt() {
    if (nodeidx == -1) {
      GetLogsTask t = new GetLogsTask(container);
      t.doIt();
      bytes = t._bytes;
    }
    else {
      H2ONode node = H2O.CLOUD._memary[nodeidx];
      GetLogsTask t = new GetLogsTask(container);
      Log.trace("GetLogsTask starting to node " + nodeidx + "...");
      // Synchronous RPC call to get ticks from remote (possibly this) node.
      new RPC<>(node, t).call().get();
      Log.trace("GetLogsTask completed to node " + nodeidx);
      bytes = t._bytes;
    }
  }

  private static class GetLogsTask extends DTask<GetLogsTask> {
    // IN
    private final LogArchiveContainer _container;
    // OUT
    private byte[] _bytes;

    public GetLogsTask(LogArchiveContainer container) { 
      super(H2O.MIN_HI_PRIORITY);
      _container = container;
      _bytes = null;
    }

    public void doIt() {
      try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
           LogArchiveWriter archiveWriter = _container.createLogArchiveWriter(baos)) {
        String archiveRoot = String.format("h2ologs_node%d_%s_%d", H2O.SELF.index(),  H2O.SELF_ADDRESS.getHostAddress(), H2O.API_PORT);
        archiveDir(Log.getLogDir(), archiveRoot, baos, archiveWriter);
        archiveWriter.close(); // need to close before we extract the bytes
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

    //here is the code for the method
    private void archiveDir(String dir, String pathInArchive, ByteArrayOutputStream baos, LogArchiveWriter writer) {
      try
      {
        //convert paths represented as strings into File instances
        File sourceDir = new File(dir);
        File destinationDir = new File(pathInArchive);
        //get a listing of the directory content
        String[] dirList = sourceDir.list();
        if (dirList == null)
          return;
        byte[] readBuffer = new byte[4096]; 
        //loop through dirList, and archive the files
        for(int i=0; i<dirList.length; i++)
        {
          File sourceFile = new File(sourceDir, dirList[i]);
          File destinationFile = new File(destinationDir, dirList[i]);
          if(sourceFile.isDirectory())
          {
            //if the File object is a directory, call this
            archiveDir(sourceFile.getPath(), destinationFile.getPath(), baos, writer);
            //loop again
            continue;
          }

          // In the Sparkling Water case, when running in the local-cluster configuration,
          // there are jar files in the log directory too.  Ignore them.
          if (sourceFile.toString().endsWith(".jar")) {
            continue;
          }

          //if we reached here, the File object f was not a directory
          //create a FileInputStream on top of f
          FileInputStream fis = new FileInputStream(sourceFile.getPath());
          //create a new archive entry
          LogArchiveWriter.ArchiveEntry anEntry = new LogArchiveWriter.ArchiveEntry(destinationFile.getPath(), sourceFile.lastModified());
          //place the archive entry
          writer.putNextEntry(anEntry);

          //now add the content of the file to the archive
          boolean stopEarlyBecauseTooMuchData = false;
          int bytesIn;
          while((bytesIn = fis.read(readBuffer)) != -1)
          {
            writer.write(readBuffer, 0, bytesIn);
            if (baos.size() > MAX_SIZE) {
              stopEarlyBecauseTooMuchData = true;
              break;
            }
          }
          //close the Stream
          fis.close();
          writer.closeEntry();

          if (stopEarlyBecauseTooMuchData) {
            Log.warn("GetLogsTask stopEarlyBecauseTooMuchData");
            break;
          }
        }
      }
      catch(Exception e) {
        Log.warn(e);
      }
    }
  }
}
