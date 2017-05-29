package water.util;

import org.eclipse.jetty.io.EofException;
import water.Key;

import java.io.*;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * File utilities.
 */
public class FileUtils {
  private static final int MB = 1 << 20;
  private static final int MAX_SIZE = 25 * MB;
  private static int MAX_DIR_CREATION_ATTEMPTS = 10;

  /**
   * Create a directory inside the given parent directory. The directory is guaranteed to be
   * newly created, and is not marked for automatic deletion.
   */
  public static File createUniqueDirectory(String rootDir, String namePrefix) throws IOException {
    int attempts = 0;
    int maxAttempts = MAX_DIR_CREATION_ATTEMPTS;
    File dir = null;
    while (dir == null) {
      attempts += 1;
      if (attempts > maxAttempts) {
        throw new IOException("Failed to create a temp directory (under " + rootDir + ") after " +
                maxAttempts + " attempts!");
      }
      try {
        dir = new File(rootDir, namePrefix + "-" + UUID.randomUUID().toString());
        if (dir.exists() || !dir.mkdirs()) {
          dir = null;
        }
      } catch(SecurityException e) {
        dir = null;
      }
    }

    return dir.getCanonicalFile();
  }

  public static void zipDir(String dir2zip, ByteArrayOutputStream baos, ZipOutputStream zos) throws IOException{
    zipDir(dir2zip, null, baos, zos);
  }

  /** Zip provided directory. The files which should be zipped can be specified */
  public static void zipDir(String dir2zip, Set<String> filter, ByteArrayOutputStream baos, ZipOutputStream zos) throws IOException {
    try {
      // create a new File object based on the directory we have to zip.
      File zipDir = new File(dir2zip);
      // get a listing of the directory content
      String[] dirList = zipDir.list();
      byte[] readBuffer = new byte[4096];
      int bytesIn = 0;
      //loop through dirList, and zip the files
      for (String aDirList : dirList) {
        File f = new File(zipDir, aDirList);
        if (f.isDirectory()) {
          //if the File object is a directory, call this
          //function again to add its content recursively
          String filePath = f.getPath();
          zipDir(filePath, baos, zos);
          //loop again
          continue;
        }

        if (filter != null && !filter.contains(f.getName())) {
          continue;
        }

        //if we reached here, the File object f was not a directory
        //create a FileInputStream on top of f
        FileInputStream fis = new FileInputStream(f);
        // create a new zip entry
        ZipEntry anEntry = new ZipEntry(f.getPath());
        anEntry.setTime(f.lastModified());
        //place the zip entry in the ZipOutputStream object
        zos.putNextEntry(anEntry);
        //now write the content of the file to the ZipOutputStream

        boolean stopEarlyBecauseTooMuchData = false;
        while ((bytesIn = fis.read(readBuffer)) != -1) {
          zos.write(readBuffer, 0, bytesIn);
          if (baos.size() > MAX_SIZE) {
            stopEarlyBecauseTooMuchData = true;
            break;
          }
        }
        //close the Stream
        fis.close();
        zos.closeEntry();

        if (stopEarlyBecauseTooMuchData) {
          Log.warn("Zipping stopped because of stopEarlyBecauseTooMuchData");
          break;
        }
      }
    }
    catch(Exception e) {
      Log.warn(e);
    }
  }

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
      while( is.available() > 0 )
      {
        int count=is.read(bytes, 0, buffer_size);
        if(count<=0)
          break;
        os.write(bytes, 0, count);
      }
    }
    catch(EofException eofe) {
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
