package water.api;

import water.H2O;

import java.util.ArrayList;

/**
 * The handler provides import capabilities.
 *
 * <p>
 *   Currently import from local filesystem, hdfs and s3 is supported.
 * </p>
 */
public class ImportFilesHandler extends Handler {

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ImportFilesV3 importFiles(int version, ImportFilesV3 importFiles) {
    ArrayList<String> files = new ArrayList();
    ArrayList<String> keys = new ArrayList();
    ArrayList<String> fails = new ArrayList();
    ArrayList<String> dels = new ArrayList();

    H2O.getPM().importFiles(importFiles.path, files, keys, fails, dels);

    importFiles.files = files.toArray(new String[files.size()]);
    importFiles.destination_frames = keys.toArray(new String[keys.size()]);
    importFiles.fails = fails.toArray(new String[fails.size()]);
    importFiles.dels = dels.toArray(new String[dels.size()]);
    return importFiles;
  }
}

