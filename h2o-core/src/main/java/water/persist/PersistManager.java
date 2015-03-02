package water.persist;

import water.H2O;
import water.Key;
import water.Value;
import water.exceptions.H2OIllegalArgumentException;
import water.util.Log;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One true persistence manager which hides the implementations from H2O.
 * In particular, HDFS support or S3 support may or may not exist depending
 * on what is on the classpath.
 */
public class PersistManager {
  /** Persistence schemes; used as file prefixes eg "hdfs://some_hdfs_path/some_file" */
  public static class Schemes {
    public static final String FILE = "file";
    public static final String HDFS = "hdfs";
    public static final String S3   = "s3";
    public static final String NFS  = "nfs";
  }

  private Persist[] I;
  private AtomicLong storeCount;
  private AtomicLong deleteCount;
  private AtomicLong loadCount;

  public PersistManager(URI iceRoot) {
    I = new Persist[8];
    storeCount = new AtomicLong();
    deleteCount = new AtomicLong();
    loadCount = new AtomicLong();

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
    catch (Exception ignore) {
      Log.info("HDFS subsystem not available");
    }

    try {
      I[Value.S3  ] = new PersistS3();
      Log.info("S3 subsystem successfully initialized");
    } catch (NoClassDefFoundError ignore) {
      Log.info("S3 subsystem not available");
    }
  }

  long getStoreCount() { return storeCount.get(); }
  long getDeleteCount() { return deleteCount.get(); }
  long getLoadCount() { return loadCount.get(); }

  public void store(int backend, Value v) {
    storeCount.incrementAndGet();
    I[backend].store(v);
  }

  public void delete(int backend, Value v) {
    deleteCount.incrementAndGet();
    I[backend].delete(v);
  }

  public byte[] load(int backend, Value v) throws IOException {
    loadCount.incrementAndGet();
    byte[] arr = I[backend].load(v);
    return arr;
  }

  /** Get the current Persist flavor for user-mode swapping. */
  public Persist getIce() { return I[Value.ICE]; }

  public final Key anyURIToKey(URI uri) throws IOException {
    Key ikey = null;
    String scheme = uri.getScheme();
    if ("hdfs".equals(scheme)) {
      ikey = I[Value.HDFS].uriToKey(uri);
    } else if ("s3n".equals(scheme)) {
      ikey = I[Value.HDFS].uriToKey(uri);
    } else if ("files".equals(scheme) || scheme == null) {
      ikey = I[Value.NFS].uriToKey(uri);
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
    if (s.startsWith("hdfs://") || s.startsWith("s3n://")) {
      if (I[Value.HDFS] == null) {
        throw new H2OIllegalArgumentException("HDFS and S3N support is not configured");
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
    if (s.startsWith("hdfs://") || s.startsWith("s3n://")) {
      if (I[Value.HDFS] == null) {
        throw new H2OIllegalArgumentException("HDFS and S3N support is not configured");
      }

      I[Value.HDFS].importFiles(path, files, keys, fails, dels);
      return;
    }

    I[Value.NFS].importFiles(path, files, keys, fails, dels);
  }
}
