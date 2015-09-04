package water.init;

import water.H2O;
import water.Iced;
import water.persist.Persist.PersistEntry;
import water.persist.PersistManager;
import water.util.FileUtils;
import water.util.Log;

import java.io.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

public class NodePersistentStorage {
  final String NPS_DIR;
  final String NPS_SEPARATOR;

  public static class NodePersistentStorageEntry extends Iced {
    public String _category;
    public String _name;
    public long _size;
    public long _timestamp_millis;
  }

  public NodePersistentStorage(String npsDir) {
    if (npsDir == null) {
      NPS_DIR = null;
      NPS_SEPARATOR = null;
      return;
    }

    if (PersistManager.isHdfsPath(npsDir)) {
      NPS_SEPARATOR = "/";
    }
    else {
      NPS_SEPARATOR = File.separator;
    }

    String s = npsDir.toString();
    if (s.startsWith("file://")) {
      s = s.substring(7);
    }
    else if (s.startsWith("file:")) {
      s = s.substring(5);
    }

    NPS_DIR = s;
  }

  private void validateGeneral() {
    if (NPS_DIR == null) {
      throw new IllegalArgumentException("NodePersistentStorage directory not specified (try setting -flow_dir)");
    }
  }

  private void validateCategoryName(String categoryName) {
    if (categoryName == null) {
      throw new IllegalArgumentException("NodePersistentStorage category not specified");
    }

    if (! Pattern.matches("[\\-a-zA-Z0-9]+", categoryName)) {
      throw new IllegalArgumentException("NodePersistentStorage illegal category (" + categoryName + ")");
    }
  }

  private void validateKeyName(String keyName) {
    if (keyName == null) {
      throw new IllegalArgumentException("NodePersistentStorage name not specified");
    }

    if (! Pattern.matches("[\\-a-zA-Z0-9_ \\(\\)]+", keyName)) {
      throw new IllegalArgumentException("NodePersistentStorage illegal name (" + keyName + ")");
    }
  }

  public boolean configured() {
    return (NPS_DIR != null);
  }

  public boolean exists(String categoryName) {
    validateGeneral();
    validateCategoryName(categoryName);

    String dirName = NPS_DIR + NPS_SEPARATOR + categoryName;
    return H2O.getPM().exists(dirName);
  }

  public boolean exists(String categoryName, String keyName) {
    validateGeneral();
    validateCategoryName(categoryName);
    validateKeyName(keyName);

    String fileName = NPS_DIR + NPS_SEPARATOR + categoryName + NPS_SEPARATOR + keyName;
    return H2O.getPM().exists(fileName);
  }

  public void put(String categoryName, String keyName, InputStream is) {
    Log.info("NPS put content category(" + categoryName + ") keyName(" + keyName + ")");

    // Error checking
    validateGeneral();
    validateCategoryName(categoryName);
    validateKeyName(keyName);

    // Create common directories
    PersistManager pm = H2O.getPM();
    if (! pm.exists(NPS_DIR)) {
      boolean success = pm.mkdirs(NPS_DIR);
      if (! success) {
        throw new RuntimeException("Could not make NodePersistentStorage directory (" + NPS_DIR + ")");
      }
    }

    if (! pm.exists(NPS_DIR)) {
      throw new RuntimeException("NodePersistentStorage directory does not exist (" + NPS_DIR + ")");
    }

    String tmpd = NPS_DIR + NPS_SEPARATOR + "_tmp";
    if (! pm.exists(tmpd)) {
      boolean success = pm.mkdirs(tmpd);
      if (! success) {
        throw new RuntimeException("Could not make NodePersistentStorage category directory (" + tmpd + ")");
      }
    }
    if (! pm.exists(tmpd)) {
      throw new RuntimeException("NodePersistentStorage category directory does not exist (" + tmpd + ")");
    }

    // Create category directory
    String d2 = NPS_DIR + NPS_SEPARATOR + categoryName;
    if (! pm.exists(d2)) {
      boolean success = pm.mkdirs(d2);
      if (! success) {
        throw new RuntimeException("Could not make NodePersistentStorage category directory (" + d2 + ")");
      }
    }
    if (! pm.exists(d2)) {
      throw new RuntimeException("NodePersistentStorage category directory does not exist (" + d2 + ")");
    }

    // Create tmp file
    String tmpf = tmpd + NPS_SEPARATOR + keyName;
    OutputStream os = null;
    try {
      os = pm.create(tmpf, true);
      FileUtils.copyStream(is, os, 1024);
    }
    finally {
      if (os != null) {
        try {
          os.close();
        }
        catch (Exception e) {
          Log.err(e);
        }
      }
    }

    // Make final spot available if needed, and move tmp file to final spot.
    boolean success;
    String realf = d2 + NPS_SEPARATOR + keyName;
    if (pm.exists(realf)) {
      success = pm.delete(realf);
      if (! success) {
        throw new RuntimeException("NodePersistentStorage delete failed (" + realf + ")");
      }
    }

    success = pm.rename(tmpf, realf);
    if (! success) {
      throw new RuntimeException("NodePersistentStorage rename failed (" + tmpf + " -> " + realf + ")");
    }

    if (! pm.exists(realf)) {
      throw new RuntimeException("NodePersistentStorage file does not exist (" + realf + ")");
    }

    Log.info("Put succeeded");
  }

  public void put(String categoryName, String keyName, String value) {
    validateGeneral();
    validateCategoryName(categoryName);
    validateKeyName(keyName);

    InputStream is = new ByteArrayInputStream(value.getBytes());
    put(categoryName, keyName, is);
  }

  public NodePersistentStorageEntry[] list(String categoryName) {
    validateGeneral();
    validateCategoryName(categoryName);

    String dirName = NPS_DIR + NPS_SEPARATOR + categoryName;
    PersistEntry[] arr1 = H2O.getPM().list(dirName);
    NodePersistentStorageEntry[] arr2 = new NodePersistentStorageEntry[arr1.length];
    for (int i = 0; i < arr1.length; i++) {
      arr2[i] = new NodePersistentStorageEntry();
      arr2[i]._category = categoryName;
      arr2[i]._name = arr1[i]._name;
      arr2[i]._size = arr1[i]._size;
      arr2[i]._timestamp_millis = arr1[i]._timestamp_millis;
    }

    return arr2;
  }

  public String get_as_string(String categoryName, String keyName) {
    validateGeneral();
    validateCategoryName(categoryName);
    validateKeyName(keyName);

    String fileName = NPS_DIR + NPS_SEPARATOR + categoryName + NPS_SEPARATOR + keyName;
    InputStream is = H2O.getPM().open(fileName);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte[] buf = new byte[4096];
    try {
      int n = is.read(buf, 0, buf.length);
      while (true) {
        if (baos.size() > (1024L * 1024L * 1024L)) {
          throw new RuntimeException("File too big (" + fileName + ")");
        }

        if (n < 0) {
          return baos.toString();
        }

        baos.write(buf, 0, n);
        n = is.read(buf, 0, buf.length);
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public long get_length(String categoryName, String keyName) {
    validateGeneral();
    validateCategoryName(categoryName);
    validateKeyName(keyName);

    String fileName = NPS_DIR + NPS_SEPARATOR + categoryName + NPS_SEPARATOR + keyName;
    if (! H2O.getPM().exists(fileName)) {
      throw new IllegalArgumentException("File not found (" + fileName + ")");
    }

    return H2O.getPM().length(fileName);
  }

  public InputStream get(String categoryName, String keyName, AtomicLong length) {
    validateGeneral();
    validateCategoryName(categoryName);
    validateKeyName(keyName);

    String fileName = NPS_DIR + NPS_SEPARATOR + categoryName + NPS_SEPARATOR + keyName;
    if (length != null) {
      length.set(H2O.getPM().length(fileName));
    }

    return H2O.getPM().open(fileName);
  }

  public void delete(String categoryName, String keyName) {
    validateGeneral();
    validateCategoryName(categoryName);
    validateKeyName(keyName);

    String fileName = NPS_DIR + NPS_SEPARATOR + categoryName + NPS_SEPARATOR + keyName;
    if (! H2O.getPM().exists(fileName)) {
      return;
    }

    boolean success = H2O.getPM().delete(fileName);
    if (! success) {
      throw new RuntimeException("NodePersistentStorage delete failed (" + fileName + ")");
    }
  }
}
