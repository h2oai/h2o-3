package water.util;

import water.Key;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
  public static void closeSilently(Closeable...closeable) {
    for(Closeable c : closeable)
      try { if( c != null ) c.close(); } catch( IOException xe ) { }
  }

  /**
   * Closes given files, logging exceptions thrown during the process of closing.
   *
   * @param closeable files to close
   */
  public static void close(Closeable...closeable) {
    for(Closeable c : closeable)
      try { if( c != null ) c.close(); } catch( IOException ex ) {
        Log.err(ex);
      }
  }



  public static void copyStream(InputStream is, OutputStream os, final int buffer_size) {
    try {
      byte[] bytes=new byte[buffer_size];
      while( is.available() > 0 )
      {
        int count=is.read(bytes, 0, buffer_size);
        if(count<=0)
          break;
        os.write(bytes, 0, count);
      }
    }
    catch(EOFException eofe) {
      // no problem
    }
    catch(Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  public static URI getURI(String path) {
    boolean windowsPath = path.matches("^[a-zA-Z]:.*$");
    if (windowsPath) {
      return new File(path).toURI();
    } else if (path.contains(":/")) { // Seems like
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
  
  /** Hunt for files in likely places.  Null if cannot find.
   *  @param fname filename
   *  @return      Found file or null */
  public static File locateFile(String fname) {
    // When run from eclipse, the working directory is different.
    // Try pointing at another likely place
    File file = new File(fname);
    if( !file.exists() )
      file = new File("target/" + fname);
    if( !file.exists() )
      file = new File("../" + fname);
    if( !file.exists() )
      file = new File("../../" + fname);
    if( !file.exists() )
      file = new File("../../../" + fname);
    if( !file.exists() )
      file = new File("../target/" + fname);
    if( !file.exists() )
      file = new File(StringUtils.expandPath(fname));
    if( !file.exists() )
      file = null;
    return file;
  }

  private static void check(boolean cond, String msg) throws IOException{
    if (!cond) throw new IOException(msg);
  }
  
  private static void checkFileEntry(String name, File file) throws IOException {
    check(file != null, "File not found: " + name);
    check(file.exists(), "File should exist: "  + name);
  }

  public static void checkFile(File file, String name) throws IOException {
    checkFileEntry(name, file);
    check(file.isFile(), "Expected a file: " + name);
    check(file.canRead(), "Expected a readable file: " + name);
  }

  public static File getFile(String fname) throws IOException {
    File f = locateFile(fname);
    checkFile(f, fname);
    return f;
  }

  public static File[] contentsOf(File folder, String name) throws IOException {
    checkFileEntry(name, folder);
    if (!folder.isDirectory()) throw new IOException("Expected a folder: " + name);
    File[] files = folder.listFiles();
    if (files == null) throw new IOException("Cannot read folder: " + folder);
    return files;
  }

  /** Transform given key to a string which can be used as a file name. */
  public static String keyToFileName(Key k) {
    return k.toString().replaceAll("[^a-zA-Z0-9_\\-\\.]", "_");
  }
}
