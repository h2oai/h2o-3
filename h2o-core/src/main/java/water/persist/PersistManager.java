package water.persist;

import water.H2O;
import water.Key;
import water.Value;
import water.util.Log;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicLong;

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
      I[Value.HDFS] = new PersistHdfs();
    } catch( NoClassDefFoundError ignore ) {
      // Not linked against HDFS, so not available in this build
    }
    try {
      I[Value.S3  ] = new PersistS3();
    } catch( NoClassDefFoundError ignore ) {
      // Not linked against S3, so not available in this build
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
}
