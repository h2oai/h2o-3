package water.persist;

import water.Key;
import water.Value;

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

    Persist ice = null;
    URI uri = iceRoot;
    if( uri != null ) { // Otherwise class loaded for reflection
      boolean windowsPath = uri.toString().matches("^[a-zA-Z]:.*");

      if ( windowsPath ) {
        ice = new PersistFS(new File(uri.toString()));
      }
      else if ((uri.getScheme() == null) || Schemes.FILE.equals(uri.getScheme())) {
        ice = new PersistFS(new File(uri.getPath()));
      }
      else if( Schemes.HDFS.equals(uri.getScheme()) ) {
        ice = new PersistHdfs(uri);
      }

      I[Value.ICE ] = ice;
      I[Value.NFS ] = new PersistNFS();
      try {
        I[Value.HDFS] = new PersistHdfs();
        I[Value.S3  ] = new PersistS3();
      } catch( NoClassDefFoundError e ) {
        // Not linked against HDFS or S3, so not available in this build
      }
    }
  }

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
