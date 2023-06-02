package water.persist;

import water.H2O;
import water.Key;
import water.Value;
import water.util.FrameUtils;
import water.util.Log;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;

public class PersistDrive extends EagerPersistBase {

  private static final Object _lock = new Object();
  private static volatile DriveClient _driveClient;

  @Override
  public final byte[] load(Value v) {
    throw new UnsupportedOperationException("PersistDrive#load should never be called");
  }

  @Override
  public void importFiles(String path, String pattern,
                          /*OUT*/ ArrayList<String> files, ArrayList<String> keys, ArrayList<String> fails, ArrayList<String> dels) {
    final boolean useHttp = getClient().supportsPresignedUrls();
    if (useHttp) {
      importFileHttp(path, files, keys, fails);
    } else {
      importFileDiskCache(path, files, keys, fails);
    }
  }

  void importFileHttp(String path,
                      /*OUT*/ ArrayList<String> files, ArrayList<String> keys, ArrayList<String> fails) {
    try {
      String presigned = getClient().generatePresignedUrls(path);
      Key<?> destKey = FrameUtils.eagerLoadFromURL(path, new URL(presigned));
      files.add(path);
      keys.add(destKey.toString());
    } catch (Exception e) {
      Log.err("Generating pre-signed URL for `" + path + "` failed.", e);
      fails.add(path);
    }
  }

  void importFileDiskCache(String path,
                           /*OUT*/ ArrayList<String> files, ArrayList<String> keys, ArrayList<String> fails) {
    File temp = null;
    try {
      temp = Files.createTempFile("drive", ".data").toFile();
      getClient().downloadFile(path, temp.getAbsolutePath());
      assert temp.isFile();
      Key<?> destKey = FrameUtils.eagerLoadFromURL(path, temp.toURI().toURL());
      files.add(path);
      keys.add(destKey.toString());
    } catch (Exception e) {
      Log.err("Loading from `" + path + "` failed.", e);
      fails.add(path);
    } finally {
      if (temp != null) {
        if (!temp.delete()) {
          Log.warn("Unable to delete temporary file " + temp);
        }
      }
    }
  }

  @Override
  public List<String> calcTypeaheadMatches(String filter, int limit) {
    return Arrays.asList(getClient().calcTypeaheadMatches(filter, limit));
  }

  static DriveClient getClient() {
    if (_driveClient == null) {
      synchronized (_lock) {
        if (_driveClient == null) {
          String venvExePath = H2O.getSysProperty("persist.drive.venv", lookupDefaultVenv());
          try {
            _driveClient = DriveClientFactory.createDriveClient(venvExePath);
          } catch (IOException e) {
            throw new RuntimeException("Failed creating a Drive client", e);
          }
          assert _driveClient != null;
        }
      }
    }
    return _driveClient;
  }

  private static String lookupDefaultVenv() {
    String def = "venv/bin/graalpython";
    for (int i = 0; i < 2; i++) {
      if (new File(def).isFile())
        return def;
      def = "../" + def;
    }
    return def;
  }
  
}
