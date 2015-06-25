package water.persist;

import water.H2O;
import water.Key;
import water.Value;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.UploadFileVec;
import water.util.Log;
import water.persist.Persist.PersistEntry;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One true persistence manager which hides the implementations from H2O.
 * In particular, HDFS support or S3 support may or may not exist depending
 * on what is on the classpath.
 */
public class PersistManager {
  final static public int MAX_BACKENDS = 8;

  /** Persistence schemes; used as file prefixes eg "hdfs://some_hdfs_path/some_file" */
  public static class Schemes {
    public static final String FILE = "file";
    public static final String HDFS = "hdfs";
    public static final String S3   = "s3";
    public static final String S3N  = "s3n";
    public static final String S3A  = "s3a";
    public static final String NFS  = "nfs";
  }

  public static class PersistStatsEntry {
    public PersistStatsEntry() {
      store_count = new AtomicLong();
      store_bytes = new AtomicLong();
      delete_count = new AtomicLong();
      load_count = new AtomicLong();
      load_bytes = new AtomicLong();
    }

    public AtomicLong store_count;
    public AtomicLong store_bytes;
    public AtomicLong delete_count;
    public AtomicLong load_count;
    public AtomicLong load_bytes;
  }

  private Persist[] I;
  private PersistStatsEntry[] stats;
  public PersistStatsEntry[] getStats() { return stats; }

  public static boolean isHdfsPath(String path) {
    String s = path.toLowerCase();
    if (s.startsWith("hdfs:") || s.startsWith("s3:") || s.startsWith("s3n:")
                          || s.startsWith("s3a:") || s.startsWith("maprfs:")) {
      return true;
    }
    return false;
  }

  private void validateHdfsConfigured() {
    if (I[Value.HDFS] == null) {
      throw new H2OIllegalArgumentException("HDFS, S3, S3N, and S3A support is not configured");
    }
  }

  public PersistManager(URI iceRoot) {
    I = new Persist[MAX_BACKENDS];
    stats = new PersistStatsEntry[MAX_BACKENDS];
    for (int i = 0; i < stats.length; i++) {
      stats[i] = new PersistStatsEntry();
    }

    if (iceRoot == null) {
      Log.err("ice_root must be specified.  Exiting.");
      H2O.exit(1);
    }

    Persist ice = null;
    boolean windowsPath = iceRoot.toString().matches("^[a-zA-Z]:.*");

    if (windowsPath) {
      ice = new PersistFS(new File(iceRoot.toString()));
    }
    else if ((iceRoot.getScheme() == null) || Schemes.FILE.equals(iceRoot.getScheme())) {
      ice = new PersistFS(new File(iceRoot.getPath()));
    }
    else if( Schemes.HDFS.equals(iceRoot.getScheme()) ) {
      Log.err("HDFS ice_root not yet supported.  Exiting.");
      H2O.exit(1);

// I am not sure anyone actually ever does this.
// H2O on Hadoop launches use local disk for ice root.
// This has a chance to work, but turn if off until it gets tested.
//
//      try {
//        Class klass = Class.forName("water.persist.PersistHdfs");
//        java.lang.reflect.Constructor constructor = klass.getConstructor(new Class[]{URI.class});
//        ice = (Persist) constructor.newInstance(iceRoot);
//      } catch (Exception e) {
//        Log.err("Could not initialize HDFS");
//        throw new RuntimeException(e);
//      }
    }

    I[Value.ICE ] = ice;
    I[Value.NFS ] = new PersistNFS();

    try {
      Class klass = Class.forName("water.persist.PersistHdfs");
      java.lang.reflect.Constructor constructor = klass.getConstructor();
      I[Value.HDFS] = (Persist) constructor.newInstance();
      Log.info("HDFS subsystem successfully initialized");
    }
    catch (Throwable ignore) {
      Log.info("HDFS subsystem not available");
    }

    try {
      I[Value.S3  ] = new PersistS3();
      Log.info("S3 subsystem successfully initialized");
    } catch (NoClassDefFoundError ignore) {
      Log.info("S3 subsystem not available");
    }
  }

  public void store(int backend, Value v) {
    stats[backend].store_count.incrementAndGet();
    I[backend].store(v);
  }

  public void delete(int backend, Value v) {
    stats[backend].delete_count.incrementAndGet();
    I[backend].delete(v);
  }

  public byte[] load(int backend, Value v) throws IOException {
    stats[backend].load_count.incrementAndGet();
    byte[] arr = I[backend].load(v);
    stats[backend].load_bytes.addAndGet(arr.length);
    return arr;
  }

  /** Get the current Persist flavor for user-mode swapping. */
  public Persist getIce() { return I[Value.ICE]; }

  /** Convert given URI into a specific H2O key representation.
   *
   * The representation depends on persistent backend, since it will
   * deduce file location from the key content.
   *
   * The method will look at scheme of URI and based on it, it will
   * ask a backend to provide a conversion to a key (i.e., URI with scheme
   * 'hdfs' will be forwared to HDFS backend).
   *
   * @param uri file location
   * @return a key encoding URI
   * @throws IOException in the case of uri conversion problem
   * @throws water.exceptions.H2OIllegalArgumentException in case of unsupported scheme
   */
  public final Key anyURIToKey(URI uri) throws IOException {
    Key ikey = null;
    String scheme = uri.getScheme();
    if ("hdfs".equals(scheme)) {
      ikey = I[Value.HDFS].uriToKey(uri);
    } else if ("s3".equals(scheme) || "s3n".equals(scheme) || "s3a".equals(scheme)) {
      ikey = I[Value.HDFS].uriToKey(uri);
    } else if ("file".equals(scheme) || scheme == null) {
      ikey = I[Value.NFS].uriToKey(uri);
    } else {
      throw new H2OIllegalArgumentException("Unsupported schema '" + scheme + "' for given uri " + uri);
    }
    return ikey;
  }

  /**
   * Calculate typeahead matches for src
   *
   * @param filter Source string to match for typeahead
   * @param limit Max number of entries to return
   * @return List of matches
   */
  public ArrayList<String> calcTypeaheadMatches(String filter, int limit) {
    String s = filter.toLowerCase();
    if (s.startsWith("hdfs:") || s.startsWith("s3:") || s.startsWith("s3n:")
                          || s.startsWith("s3a:") || s.startsWith("maprfs:")) {
      if (I[Value.HDFS] == null) {
        throw new H2OIllegalArgumentException("HDFS, S3, S3N, and S3A support is not configured");
      }

      return I[Value.HDFS].calcTypeaheadMatches(filter, limit);
    }

    return I[Value.NFS].calcTypeaheadMatches(filter, limit);
  }

  /**
   * From a path produce a list of files and keys for parsing.
   *
   * Use as follows:
   *
   * ArrayList<String> files = new ArrayList();
   * ArrayList<String> keys = new ArrayList();
   * ArrayList<String> fails = new ArrayList();
   * ArrayList<String> dels = new ArrayList();
   * importFiles(importFiles.path, files, keys, fails, dels);
   *
   * @param path  (Input) Path to import data from
   * @param files (Output) List of files found
   * @param keys  (Output) List of keys corresponding to files
   * @param fails (Output) List of failed files which mismatch among nodes
   * @param dels  (Output) I don't know what this is
   */
  public void importFiles(String path, ArrayList<String> files, ArrayList<String> keys, ArrayList<String> fails, ArrayList<String> dels) {
    assert path != null;
    String s = path.toLowerCase();
    if (s.startsWith("http:") || s.startsWith("https:")) {
      try {
        java.net.URL url = new URL(path);
        Key destination_key = Key.make(path);
        java.io.InputStream is = url.openStream();
        if( is == null ) {
          Log.err("Unable to open stream to URL " + path);
        }

        UploadFileVec.ReadPutStats stats = new UploadFileVec.ReadPutStats();
        UploadFileVec.readPut(destination_key, is, stats);

        files.add(path);
        keys.add(destination_key.toString());
      }
      catch( Throwable e) {
        fails.add(path);
      }

      return;
    }
    else if (s.startsWith("hdfs:") || s.startsWith("s3:") || s.startsWith("s3n:")
                               || s.startsWith("s3a:") || s.startsWith("maprfs:")) {
      if (I[Value.HDFS] == null) {
        throw new H2OIllegalArgumentException("HDFS, S3, S3N, and S3A support is not configured");
      }

      I[Value.HDFS].importFiles(path, files, keys, fails, dels);
      return;
    }

    I[Value.NFS].importFiles(path, files, keys, fails, dels);
  }


  // -------------------------------
  // Node Persistent Storage helpers
  // -------------------------------

  // Reads

  public String getHdfsHomeDirectory() {
    if (I[Value.HDFS] == null) {
      return null;
    }

    return I[Value.HDFS].getHomeDirectory();
  }

  public PersistEntry[] list(String path) {
    if (isHdfsPath(path)) {
      validateHdfsConfigured();
      PersistEntry[] arr = I[Value.HDFS].list(path);
      return arr;
    }

    File dir = new File(path);
    File[] files = dir.listFiles();
    if (files == null) {
      return new PersistEntry[0];
    }

    ArrayList<PersistEntry> arr = new ArrayList<>();
    for (File f : files) {
      PersistEntry entry = new PersistEntry(f.getName(), f.length(), f.lastModified());
      arr.add(entry);
    }

    return arr.toArray(new PersistEntry[arr.size()]);
  }

  public boolean exists(String path) {
    if (isHdfsPath(path)) {
      validateHdfsConfigured();
      boolean b = I[Value.HDFS].exists(path);
      return b;
    }

    File f = new File(path);
    return f.exists();
  }

  public long length(String path) {
    if (isHdfsPath(path)) {
      validateHdfsConfigured();
      long l = I[Value.HDFS].length(path);
      return l;
    }

    File f = new File(path);
    if (! f.exists()) {
      throw new IllegalArgumentException("File not found (" + path + ")");
    }

    return f.length();
  }

  public InputStream open(String path) {
    if (isHdfsPath(path)) {
      validateHdfsConfigured();
      InputStream os = I[Value.HDFS].open(path);
      return os;
    }

    try {
      File f = new File(path);
      return new FileInputStream(f);
    }
    catch (FileNotFoundException e) {
      throw new IllegalArgumentException("File not found (" + path + ")");
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // Writes

  public boolean mkdirs(String path) {
    if (isHdfsPath(path)) {
      validateHdfsConfigured();
      boolean b = I[Value.HDFS].mkdirs(path);
      return b;
    }

    File f = new File(path);
    boolean b = f.mkdirs();
    return b;
  }

  public boolean rename(String fromPath, String toPath) {
    if (isHdfsPath(fromPath) || isHdfsPath(toPath)) {
      validateHdfsConfigured();
      boolean b = I[Value.HDFS].rename(fromPath, toPath);
      return b;
    }

    File f = new File(fromPath);
    File t = new File(toPath);
    boolean b = f.renameTo(t);
    return b;
  }

  public OutputStream create(String path, boolean overwrite) {
    if (isHdfsPath(path)) {
      validateHdfsConfigured();
      return I[Value.HDFS].create(path, overwrite);
    }

    try {
      if (! overwrite) {
        File f = new File(path);
        if (f.exists()) {
          throw new IllegalArgumentException("File already exists (" + path + ")");
        }
      }

      FileOutputStream fos;
      fos = new FileOutputStream(path);
      return fos;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public boolean delete(String path) {
    if (isHdfsPath(path)) {
      validateHdfsConfigured();
      boolean b = I[Value.HDFS].delete(path);
      return b;
    }

    File f = new File(path);
    boolean b = f.delete();
    return b;
  }

  public Persist getPersistForURI(URI uri) {
    String scheme = uri.getScheme();
    if (scheme != null ) {
      switch (scheme) {
        case Schemes.FILE:
          return I[Value.ICE]; // Local FS
        case Schemes.HDFS:
        case Schemes.S3N:
        case Schemes.S3A:
          return I[Value.HDFS];
        case Schemes.S3:
          return I[Value.S3];
        default:
          throw new IllegalArgumentException("Cannot find persist manager for scheme " + scheme);
      }
    } else {
      return I[Value.ICE];
    }
  }
}
