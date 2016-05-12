package water.util;

import org.eclipse.jetty.io.EofException;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import water.Key;

/**
 * File utilities.
 */
public class FileUtils {

  private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;

  private static final int EOF = -1;


  /**
   * Silently close given files.
   *
   * @param closeable files to close
   */
  public static void close(Closeable...closeable) {
    for(Closeable c : closeable)
      try { if( c != null ) c.close(); } catch( IOException xe ) { }
  }

  public static long copyStream(InputStream is, OutputStream os) {
    return copyStream(is, os, DEFAULT_BUFFER_SIZE);
  }

  public static long copyStream(InputStream is, OutputStream os, final int bufferSize) {
    return copyStream(is, os, new byte[bufferSize]);
  }

  public static long copyStream(InputStream is, OutputStream os, byte[] buffer) {
    long count = 0;
    try {
      int n = 0;
      while (EOF != (n = is.read(buffer))) {
        os.write(buffer, 0, n);
        count += n;
      }
      return count;
    } catch (EofException eofe) { // This is Jetty EOFException!
      // Return without problem
      return count;
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  public static URI getURI(String path) {
    if (path.contains(":/")) { // Seems like
      return URI.create(path);
    } else {
      return new File(path).toURI();
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
