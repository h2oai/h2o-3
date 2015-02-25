package water.init;

import water.Iced;
import water.util.Log;

import java.io.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.regex.Pattern;

public class NodePersistentStorage {
  String NPS_DIR;

  public static class NodePersistentStorageEntry extends Iced {
    public String _category;
    public String _name;
    public long _size;
    public long _timestamp_millis;
  }

  public static void copyStream(InputStream is, OutputStream os)
  {
    final int buffer_size=1024;
    try {
      byte[] bytes=new byte[buffer_size];
      for(;;)
      {
        int count=is.read(bytes, 0, buffer_size);
        if(count==-1)
          break;
        os.write(bytes, 0, count);
      }
    }
    catch(Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  public NodePersistentStorage(URI npsDirParentURI) {
    NPS_DIR = npsDirParentURI.toString() + File.separator + "h2onps";
  }

  private void validateCategoryName(String categoryName) {
    if (categoryName == null) {
      throw new IllegalArgumentException("NodePersistentStorage category not specified");
    }

    if (! Pattern.matches("[\\-a-zA-Z0-9]+", categoryName)) {
      throw new IllegalArgumentException("NodePersistentStorage illegal category");
    }
  }

  private void validateKeyName(String keyName) {
    if (keyName == null) {
      throw new IllegalArgumentException("NodePersistentStorage name not specified");
    }

    if (! Pattern.matches("[\\-a-zA-Z0-9]+", keyName)) {
      throw new IllegalArgumentException("NodePersistentStorage illegal name");
    }
  }

  public void put(String categoryName, String keyName, InputStream is) {
    Log.info("NPS put content category(" + categoryName + ") keyName(" + keyName + ")");

    // Error checking
    validateCategoryName(categoryName);
    validateKeyName(keyName);

    // Create common directories
    File d = new File(NPS_DIR);
    if (! d.exists()) {
      boolean success = d.mkdir();
      if (! success) {
        throw new RuntimeException("Could not make NodePersistentStorage directory (" + d + ")");
      }
    }
    if (! d.exists()) {
      throw new RuntimeException("NodePersistentStorage directory does not exist (" + d + ")");
    }

    File tmpd = new File(d + File.separator + "_tmp");
    if (! tmpd.exists()) {
      boolean success = tmpd.mkdir();
      if (! success) {
        throw new RuntimeException("Could not make NodePersistentStorage category directory (" + tmpd + ")");
      }
    }
    if (! tmpd.exists()) {
      throw new RuntimeException("NodePersistentStorage category directory does not exist (" + tmpd + ")");
    }

    // Create category directory
    File d2 = new File(d + File.separator + categoryName);
    if (! d2.exists()) {
      boolean success = d2.mkdir();
      if (! success) {
        throw new RuntimeException("Could not make NodePersistentStorage category directory (" + d2 + ")");
      }
    }
    if (! d2.exists()) {
      throw new RuntimeException("NodePersistentStorage category directory does not exist (" + d2 + ")");
    }

    // Create tmp file
    File tmpf = new File(tmpd + File.separator + keyName);
    FileOutputStream fos = null;
    try {
      fos = new FileOutputStream(tmpf);
      copyStream(is, fos);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    finally {
      try {
        if (fos != null) {
          fos.close();
        }
      }
      catch (Exception ignore) {}
    }

    // Move tmp file to final spot
    File realf = new File(d2 + File.separator + keyName);
    try {
      boolean success = tmpf.renameTo(realf);
      if (! success) {
        throw new RuntimeException("NodePersistentStorage move failed (" + tmpf + " -> " + realf + ")");
      }

      if (! realf.exists()) {
        throw new RuntimeException("NodePersistentStorage file does not exist (" + realf + ")");
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }

    Log.info("Put succeeded");
  }

  public void put(String categoryName, String keyName, String value) {
    validateCategoryName(categoryName);
    validateKeyName(keyName);

    InputStream is = new ByteArrayInputStream(value.getBytes());
    put(categoryName, keyName, is);
  }

  public NodePersistentStorageEntry[] list(String categoryName) {
    validateCategoryName(categoryName);

    String dirName = NPS_DIR + File.separator + categoryName;
    File dir = new File(dirName);
    File[] files = dir.listFiles();
    if (files == null) {
      return new NodePersistentStorageEntry[0];
    }

    ArrayList<NodePersistentStorageEntry> arr = new ArrayList<>();
    for (File f : files) {
      NodePersistentStorageEntry entry = new NodePersistentStorageEntry();
      entry._category = categoryName;
      entry._name = f.getName();
      entry._size = f.length();
      entry._timestamp_millis = f.lastModified();
      arr.add(entry);
    }

    return arr.toArray(new NodePersistentStorageEntry[arr.size()]);
  }

  public String get_as_string(String categoryName, String keyName) {
    validateCategoryName(categoryName);
    validateKeyName(keyName);

    try {
      String fileName = NPS_DIR + File.separator + categoryName + File.separator + keyName;
      BufferedReader reader = new BufferedReader(new FileReader(fileName));
      String line;
      StringBuilder stringBuilder = new StringBuilder();
      String lineseparator = "\n";

      while ((line = reader.readLine()) != null) {
        stringBuilder.append(line);
        stringBuilder.append(lineseparator);
      }

      return stringBuilder.toString();
    }
    catch (FileNotFoundException e) {
      throw new IllegalArgumentException("Not found");
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void delete(String categoryName, String keyName) {
    validateCategoryName(categoryName);
    validateKeyName(keyName);

    String fileName = NPS_DIR + File.separator + categoryName + File.separator + keyName;
    File f = new File(fileName);
    if (! f.exists()) {
      return;
    }

    boolean success = f.delete();
    if (! success) {
      throw new RuntimeException("NodePersistentStorage delete failed (" + fileName + ")");
    }
  }
}
