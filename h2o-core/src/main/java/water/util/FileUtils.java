package water.util;

import water.Key;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * File utilities.
 */
public class FileUtils {

  public static boolean makeSureDirExists(String dir) {
    File f = new File(dir);
    if (!f.exists()) {
      return f.mkdirs();
    } else {
      return true;
    }
  }

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
        if (f.isDirectory()) {
          delete(f);
        } else {
          f.delete();
        }
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
    // Search in pre-defined path, when active, overrides all other, including direct file lookup
    final Optional<File> fileInPredefinedPath = findFileInPredefinedPath(fname);
    if (fileInPredefinedPath.isPresent()) return fileInPredefinedPath.get();
    
    File file = new File(fname);
    if (file.exists()) return file;
    
    file = new File("target/" + fname);
    if (!file.exists())
      file = new File("../" + fname);
    if (!file.exists())
      file = new File("../../" + fname);
    if (!file.exists())
      file = new File("../../../" + fname);
    if (!file.exists())
      file = new File("../target/" + fname);
    if (!file.exists())
      file = new File(StringUtils.expandPath(fname));
    if (!file.exists())
      file = null;
    return file;
  }

  /**
   * @param fileName File name/path to search for in pre-defined search path
   * @return An {@link Optional} with the file inside, if the H2O_FILES_SEARCH_PATH is defined and the file exists.
   * Otherwise an empty {@link Optional}. Never null.
   */
  private static Optional<File> findFileInPredefinedPath(final String fileName) {
    Objects.requireNonNull(fileName);

    final String searchPath = System.getenv("H2O_FILES_SEARCH_PATH");
    if (searchPath == null) return Optional.empty();

    final StringBuilder localizedFileNameBuilder = new StringBuilder(searchPath);

    if (!searchPath.endsWith("/")) {
      localizedFileNameBuilder.append('/');
    }
    
    // If the file starts with {"./", ".\", "../", "..\"} (or multiple instances of these), strip it.
    // Does not match relative paths from top of the filesystem tree (starting with "/").
    final Pattern pattern = Pattern.compile("(\\.+[\\/]{1})+(.*)");
    final Matcher matcher = pattern.matcher(fileName);

    if (matcher.matches()) {
      localizedFileNameBuilder.append(matcher.group(2));
    } else if (fileName.startsWith("/")) {
      return Optional.empty(); // The "/" at the beginning indicates absolute path, except for Windows. Do not attempt.
    } else {
      localizedFileNameBuilder.append(fileName);
    }
    
    final File file = new File(localizedFileNameBuilder.toString());

    if (file.exists()) {
      return Optional.of(file);
    } else {
      return Optional.empty();
    }

  }

  private static void check(boolean cond, String msg) throws IOException {
    if (!cond) throw new IOException(msg);
  }

  private static void checkFileEntry(String name, File file) throws IOException {
    check(file != null, "File not found: " + name);
    check(file.exists(), "File should exist: " + name);
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
