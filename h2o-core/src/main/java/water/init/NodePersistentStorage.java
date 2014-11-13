package water.init;

import water.util.Log;

import java.io.*;
import java.net.URI;

public class NodePersistentStorage {
  String NPS_DIR;

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
    NPS_DIR = npsDirParentURI.toString() + File.separator + "NodePersistentStorage";
  }

  public void put(String categoryName, String keyName, InputStream is) {
    Log.info("NPS put content category(" + categoryName + ") keyName(" + keyName + ")");
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

    File f = new File(d2 + File.separator + keyName);

    FileOutputStream fos = null;
    try {
      fos = new FileOutputStream(f);
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
    Log.info("Put succeeded");
  }
}
