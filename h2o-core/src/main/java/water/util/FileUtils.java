package water.util;

import java.io.Closeable;
import java.io.IOException;

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
}
