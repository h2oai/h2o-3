package water.util;

import water.Key;

import java.io.*;
import java.net.URI;

/**
 * File utilities.
 */
public class FileUtils {
  /**
   * Silently close given files.
   *
   * @param closeable files to close
   */
  public static void close(Closeable...closeable) {
    for(Closeable c : closeable)
      try { if( c != null ) c.close(); } catch( IOException xe ) { }
  }

  public static void copyStream(InputStream is, OutputStream os, final int buffer_size) {
    try {
      byte[] bytes=new byte[buffer_size];
      for(;;)
      {
        int count=is.read(bytes, 0, buffer_size);
        if(count<=0)
          break;
        os.write(bytes, 0, count);
      }
    }
    catch(Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  public static URI getURI(String path) {
    File[] roots = File.listRoots();
    if (path.startsWith("./")) { // It is relative path
      return new File(path).toURI();
    } else {
      for (File root : roots) { // It is local absolute path
        if (path.startsWith(root.getAbsolutePath())) {
          return new File(path).toURI();
        }
      }
      return URI.create(path);
    }
  }

  public static boolean delete(File file) {
    if (file.isFile())
      file.delete();
    else if (file.isDirectory()) {
      File[] files = file.listFiles();
      for (File f: files) {
        f.delete();
      }
      // Delete top-level directory
      return file.delete();
    }

    return false;
  }

  /** Transform given key to a string which can be used as a file name. */
  public static String keyToFileName(Key k) {
    return k.toString().replaceAll("[^a-zA-Z0-9_\\-\\.]", "_");
  }
}
