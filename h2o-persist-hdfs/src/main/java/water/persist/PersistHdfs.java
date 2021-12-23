package water.persist;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;

import java.io.*;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import water.Futures;
import water.H2O;
import water.Key;
import water.MemoryManager;
import water.Value;
import water.api.HDFSIOException;
import water.fvec.HDFSFileVec;
import water.fvec.Vec;
import water.persist.security.HdfsDelegationTokenRefresher;
import water.util.FileUtils;
import water.util.Log;

import static water.fvec.FileVec.getPathForKey;

/**
 * HDFS persistence layer.
 */
public final class PersistHdfs extends Persist {
  /** Globally shared HDFS configuration. */
  public static final Configuration CONF;
  /** Root path of HDFS */
  private final Path _iceRoot;

  public static Configuration lastSavedHadoopConfiguration = null;
  
  private static final String H2O_DYNAMIC_AUTH_S3A_TOKEN_REFRESHER_ENABLED = "h2o.auth.dynamicS3ATokenRefresher.enabled";
  private static final Set<String> bucketsWithDelegationToken = Collections.synchronizedSet(new HashSet<>());
  private static final Object GENERATION_LOCK = new Object();

  /**
   * Filter out hidden files/directories (dot files, eg.: .crc).
   * Note: This implementation differs from the filter used in Hadoop MR: we do not skip underscore-prefixed files.
   * We already have another filter that takes care of on zero-length files (underscore files are typically empty anyway
   * eg.: _SUCCESS)
   */
  private static final PathFilter HIDDEN_FILE_FILTER = new PathFilter() {
    public boolean accept(Path p) { return ! p.getName().startsWith("."); }
  };

  // Global HDFS initialization
  static {
    final Configuration conf = new Configuration();
    if (H2O.ARGS.hdfs_config != null && H2O.ARGS.hdfs_config.length > 0) {
      for (String config : H2O.ARGS.hdfs_config) {
        File p = new File(config);
        if (!p.exists())
          H2O.die("Unable to open hdfs configuration file " + p.getAbsolutePath());
        conf.addResource(new Path(p.getAbsolutePath()));
        Log.debug("resource ", p.getAbsolutePath(), " added to the hadoop configuration");
      }
    } else {
      Path confDir = null;
      // Try to guess location of default Hadoop configuration
      // http://www.slideshare.net/martyhall/hadoop-tutorial-hdfs-part-3-java-api
      // WARNING: loading of default properties should be disabled if the job
      // is executed via yarn command which prepends core-site.xml properties on classpath
      if (System.getenv().containsKey("HADOOP_CONF_DIR")) {
        confDir = new Path(System.getenv("HADOOP_CONF_DIR"));
      } else if (System.getenv().containsKey("YARN_CONF_DIR")) {
        confDir = new Path(System.getenv("YARN_CONF_DIR"));
      } else if (System.getenv().containsKey("HADOOP_HOME")) {
        confDir = new Path(System.getenv("HADOOP_HOME"), "conf");
      }
      // Load default HDFS configuration
      if (confDir != null) {
        Log.info("Using HDFS configuration from " + confDir);
        conf.addResource(new Path(confDir, "core-site.xml"));
      } else {
        Log.debug("Cannot find HADOOP_CONF_DIR or YARN_CONF_DIR - default HDFS properties are NOT loaded!");
      }
    }
    CONF = conf;
  }

  // Loading HDFS files
  @SuppressWarnings("unused") // called via reflection
  public PersistHdfs() { _iceRoot = null; }
  public void cleanUp() { throw H2O.unimpl(); /* user-mode swapping not implemented */}

  @Override public byte[] load(final Value v) {
    //
    // !!! WARNING !!!
    //
    // tomk: Sun Apr 19 13:11:51 PDT 2015
    //
    //
    // This load implementation behaved *HORRIBLY* with S3 when the libraries were updated.
    //    Behaves well (and is the same set of libraries as H2O-1):
    //        org.apache.hadoop:hadoop-client:2.0.0-cdh4.3.0
    //        net.java.dev.jets3t:jets3t:0.6.1
    //
    //    Behaves abysmally:
    //        org.apache.hadoop:hadoop-client:2.5.0-cdh5.2.0
    //        net.java.dev.jets3t:jets3t:0.9.2
    //
    //
    // I did some debugging.
    //
    // What happens in the new libraries is the connection type is a streaming connection, and
    // the entire file gets read on close() even if you only wanted to read a chunk.  The result
    // is the same data gets read over and over again by the underlying transport layer even
    // though H2O only thinks it's asking for (and receiving) each piece of data once.
    //
    // I suspect this has something to do with the 'Range' HTTP header on the GET, but I'm not
    // entirely sure.  Many layers of library need to be fought through to really figure it out.
    //
    // Anyway, this will need to be rewritten from the perspective of how to properly use the
    // new library version.  Might make sense to go to straight to 's3a' which is a replacement
    // for 's3n'.
    //
    assert v.isPersisted();

    final Key k = v._key;
    final long skip = k.isChunkKey() ? water.fvec.NFSFileVec.chunkOffset(k) : 0;

    return load(k, skip, v._max);
  }

  @Override
  public byte[] load(final Key k, final long skip, final int max) {
    final Path p = _iceRoot == null?new Path(getPathForKey(k)):new Path(_iceRoot, getIceName(k));
    return load(p, skip, max);
  }
  
  private byte[] load(final Path p, final long skip, final int max) {
    //
    // !!! WARNING !!!
    //
    // tomk: Sun Apr 19 13:11:51 PDT 2015
    //
    //
    // This load implementation behaved *HORRIBLY* with S3 when the libraries were updated.
    //    Behaves well (and is the same set of libraries as H2O-1):
    //        org.apache.hadoop:hadoop-client:2.0.0-cdh4.3.0
    //        net.java.dev.jets3t:jets3t:0.6.1
    //
    //    Behaves abysmally:
    //        org.apache.hadoop:hadoop-client:2.5.0-cdh5.2.0
    //        net.java.dev.jets3t:jets3t:0.9.2
    //
    //
    // I did some debugging.
    //
    // What happens in the new libraries is the connection type is a streaming connection, and
    // the entire file gets read on close() even if you only wanted to read a chunk.  The result
    // is the same data gets read over and over again by the underlying transport layer even
    // though H2O only thinks it's asking for (and receiving) each piece of data once.
    //
    // I suspect this has something to do with the 'Range' HTTP header on the GET, but I'm not
    // entirely sure.  Many layers of library need to be fought through to really figure it out.
    //
    // Anyway, this will need to be rewritten from the perspective of how to properly use the
    // new library version.  Might make sense to go to straight to 's3a' which is a replacement
    // for 's3n'.
    //
    long start = System.currentTimeMillis();
    final byte[] b = MemoryManager.malloc1(max);
    run(() -> {
        FileSystem fs = FileSystem.get(p.toUri(), CONF);
        FSDataInputStream s = null;
        try {
          s = fs.open(p);
          s.seek(skip);
          s.readFully(b);
        } finally {
          if (s != null) {
            FileUtils.close(s.getWrappedStream());
            FileUtils.closeSilently(s);
          }
        }
        return null;
    });
    long end = System.currentTimeMillis();
    if (end-start > 1000) // Only log read that took over 1 second to complete
      Log.debug("Slow Read: "+(end-start)+" millis to get bytes "+skip +"-"+(skip+b.length)+" in HDFS read.");

    return b;
  }
  
  @Override public void store(Value v) {
    // Should be used only if ice goes to HDFS
    assert this == H2O.getPM().getIce();
    assert !v.isPersisted();

    byte[] m = v.memOrLoad();
    assert (m == null || m.length == v._max); // Assert not saving partial files
    store(new Path(_iceRoot, getIceName(v)), m);
  }

  private static void store(final Path path, final byte[] data) {
    run(() -> {
        FileSystem fs = getFileSystem(path, true);
        fs.mkdirs(path.getParent());
        try (FSDataOutputStream s = fs.create(path)) {
          s.write(data);
        }
        return null;
    });
  }

  @Override public void delete(final Value v) {
    assert this == H2O.getPM().getIce();
    assert !v.isPersisted();   // Upper layers already cleared out

    run(() -> {
        Path p = new Path(_iceRoot, getIceName(v));
        FileSystem fs = getFileSystem(p, true);
        fs.delete(p, true);
        return null;
    });
  }

  private static void run(Callable<?> c) {
    while( true ) {
      try {
        c.call();
        break;
        // Explicitly ignore the following exceptions but
        // fail on the rest IOExceptions
      } catch( EOFException e ) {
        ignoreAndWait(e, true);
      } catch( SocketTimeoutException e ) {
        ignoreAndWait(e, false);
      } catch( IOException e ) {
        // Newer versions of Hadoop derive S3Exception from IOException
        ignoreAndWait(e, e.getClass().getName().contains("S3Exception"));
      } catch( RuntimeException e ) {
        // Older versions of Hadoop derive S3Exception from RuntimeException
        if (e.getClass().getName().contains("S3Exception")) {
          ignoreAndWait(e, false);
        } else {
          throw Log.throwErr(e);
        }
      } catch( Exception e ) {
        throw Log.throwErr(e);
      }
    }
  }

  private static void ignoreAndWait(final Exception e, boolean printException) {
    Log.ignore(e, "Hit HDFS reset problem, retrying...", printException);
    try {
      Thread.sleep(500);
    } catch( InterruptedException ie ) {}
  }

  public static void addFolder(Path p, ArrayList<String> keys,ArrayList<String> failed) throws IOException, RuntimeException {
      FileSystem fs = getFileSystem(p, false);
      if(!fs.exists(p)){
        failed.add("Path does not exist: '" + p.toString() + "'");
        return;
      }
      addFolder(fs, p, keys, failed);
  }

  private static void startDelegationTokenRefresher(Path p) throws IOException {
    if (H2O.CLOUD.leader() != H2O.SELF) {
      // only cloud leader is allowed to refresh the tokens
      return;
    }

    if (lastSavedHadoopConfiguration == null || !lastSavedHadoopConfiguration.getBoolean(H2O_DYNAMIC_AUTH_S3A_TOKEN_REFRESHER_ENABLED, false)) {
      return;
    }

    final URI uri = p.toUri();
    if (!"s3a".equalsIgnoreCase(uri.getScheme())) {
      // only S3A needs to generate delegation token
      if (Log.isLoggingFor(Log.DEBUG)) {
        Log.debug("Scheme is not s3a: " + uri);
      }
      return;
    }

    synchronized (GENERATION_LOCK) {
      if (isInBucketWithAlreadyExistingToken(uri)) {
        return;
      }
      final String bucketIdentifier = p.toUri().getHost();
      HdfsDelegationTokenRefresher.setup(lastSavedHadoopConfiguration, System.getProperty("java.io.tmpdir"), p.toString());
      Log.debug("Bucket added to bucketsWithDelegationToken: '" + bucketIdentifier + "'");
      bucketsWithDelegationToken.add(bucketIdentifier);
    }
  }
  
  private static boolean isInBucketWithAlreadyExistingToken(URI uri) {
    return bucketsWithDelegationToken.contains(uri.getHost());
  }

  private static void addFolder(FileSystem fs, Path p, ArrayList<String> keys, ArrayList<String> failed) {
    if (fs == null) return;
    Futures futures = new Futures();
    try {
      for( FileStatus file : fs.listStatus(p, HIDDEN_FILE_FILTER) ) {
        Path pfs = file.getPath();
        if(file.isDirectory()) {
          addFolder(fs, pfs, keys, failed);
        } else if (file.getLen() > 0){
          Key<?> k = HDFSFileVec.make(pfs.toString(), file.getLen(), futures);
          keys.add(k.toString());
          Log.debug("PersistHdfs: DKV.put(" + k + ")");
        }
      }
    } catch( Exception e ) {
      Log.err(e);
      failed.add(p.toString());
    } finally {
      futures.blockForPending();
    }
  }

  @Override
  public Key uriToKey(URI uri) throws IOException {
    assert "hdfs".equals(uri.getScheme()) || "s3".equals(uri.getScheme())
            || "s3n".equals(uri.getScheme()) || "s3a".equals(uri.getScheme()) : "Expected hdfs, s3 s3n, or s3a scheme, but uri is " + uri;

    Path path = new Path(uri);
    FileSystem fs = getFileSystem(path, false);
    FileStatus[] fstatus = fs.listStatus(path);
    assert fstatus.length == 1 : "Expected uri to single file, but uri is " + uri;

    return HDFSFileVec.make(fstatus[0].getPath().toString(), fstatus[0].getLen());
  }

  // Is there a bucket name without a trailing "/" ?
  private boolean isBareS3NBucketWithoutTrailingSlash(String s) {
    String s2 = s.toLowerCase();
    Matcher m = Pattern.compile("s3n://[^/]*").matcher(s2);
    return m.matches();
  }
//  // We don't handle HDFS style S3 storage, just native storage.  But all users
//  // don't know about HDFS style S3 so treat S3 as a request for a native file
//  private static final String convertS3toS3N(String s) {
//    if (Pattern.compile("^s3[a]?://.*").matcher(s).matches())
//      return s.replaceFirst("^s3[a]?://", "s3n://");
//    else return s;
//  }

  @Override
  public ArrayList<String> calcTypeaheadMatches(String filter, int limit) {
    // Handle S3N bare buckets - s3n://bucketname should be suffixed by '/'
    // or underlying Jets3n will throw NPE. filter name should be s3n://bucketname/
    if (isBareS3NBucketWithoutTrailingSlash(filter)) {
      filter += "/";
    }
    // Output matches
    ArrayList<String> array = new ArrayList<>();
    {
      // Filter out partials which are known to print out useless stack traces.
      String s = filter.toLowerCase();
      if ("hdfs:".equals(s)) return array;
      if ("maprfs:".equals(s)) return array;
    }
    try {
      Path p = new Path(filter);
      Path expand = p;
      if( !filter.endsWith("/") ) expand = p.getParent();
      FileSystem fs = getFileSystem(p, false);
      for( FileStatus file : fs.listStatus(expand) ) {
        Path fp = file.getPath();
        if( fp.toString().startsWith(p.toString()) ) {
          array.add(fp.toString());
        }
        if( array.size() == limit) break;
      }
    } catch (Exception e) {
      Log.trace(e);
    } catch (Throwable t) {
      Log.warn(t);
    }

    return array;
  }

  @Override
  public void importFiles(String path, String pattern, ArrayList<String> files, ArrayList<String> keys, ArrayList<String> fails, ArrayList<String> dels) {
//    path = convertS3toS3N(path);

    // Fix for S3 kind of URL
    if (isBareS3NBucketWithoutTrailingSlash(path)) {
      path += "/";
    }
    Log.info("ImportHDFS processing (" + path + ")");

    // List of processed files
    try {
      // Recursively import given file/folder
      addFolder(new Path(path), keys, fails);
      files.addAll(keys);
      // write barrier was here : DKV.write_barrier();
    } catch (IOException e) {
      throw new HDFSIOException(path, PersistHdfs.CONF.toString(), e);
    }
  }

  // -------------------------------
  // Node Persistent Storage helpers
  // -------------------------------

  @Override
  public String getHomeDirectory() {
    try {
      FileSystem fs = FileSystem.get(CONF);
      return fs.getHomeDirectory().toString();
    }
    catch (Exception e) {
      return null;
    }
  }

  @Override
  public PersistEntry[] list(String path) {
    try {
      Path p = new Path(path);
      FileSystem fs = getFileSystem(p, false);
      FileStatus[] arr1 = fs.listStatus(p);
      PersistEntry[] arr2 = new PersistEntry[arr1.length];
      for (int i = 0; i < arr1.length; i++) {
        arr2[i] = new PersistEntry(arr1[i].getPath().getName(), arr1[i].getLen(), arr1[i].getModificationTime());
      }
      return arr2;
    }
    catch (IOException e) {
      throw new HDFSIOException(path, CONF.toString(), e);
    }
  }

  @Override
  public boolean exists(String path) {
    Path p = new Path(path);
    try {
      FileSystem fs = getFileSystem(p, false);
      return fs.exists(p);
    }
    catch (IOException e) {
      throw new HDFSIOException(path, CONF.toString(), e);
    }
  }

  @Override
  public String getParent(String path) {
    Path p = new Path(path);
    return p.getParent().toUri().toString();
  }

  @Override
  public boolean isDirectory(String path) {
    Path p = new Path(path);
    try {
      FileSystem fs = getFileSystem(p, false);
      return fs.isDirectory(p);
    }
    catch (IOException e) {
      throw new HDFSIOException(path, CONF.toString(), e);
    }
  }

  @Override
  public long length(String path) {
    Path p = new Path(path);
    try {
      FileSystem fs = getFileSystem(p, false);
      return fs.getFileStatus(p).getLen();
    }
    catch (IOException e) {
      throw new HDFSIOException(path, CONF.toString(), e);
    }
  }

  @Override
  public InputStream open(String path) {
    return openSeekable(path);
  }

  @Override
  public InputStream openSeekable(String path) {
    Path p = new Path(path);
    try {
      FileSystem fs = getFileSystem(p, false);
      return fs.open(p);
    }
    catch (IOException e) {
      throw new HDFSIOException(path, CONF.toString(), e);
    }
  }

  public InputStream wrapSeekable(Vec vec) {
    return new FSDataInputStream(new VecDataInputStream(vec));
  }
  
  public boolean isSeekableOpenSupported() {
    return true;
  }

  @Override
  public boolean mkdirs(String path) {
    Path p = new Path(path);
    try {
      FileSystem fs = getFileSystem(p, false);
      // Be consistent with Java API and File#mkdirs
      if (fs.exists(p)) {
        return false;
      } else {
        return fs.mkdirs(p);
      }
    }
    catch (IOException e) {
      throw new HDFSIOException(path, CONF.toString(), e);
    }
  }

  @Override
  public boolean rename(String fromPath, String toPath) {
    Path f = new Path(fromPath);
    Path t = new Path(toPath);
    try {
      FileSystem fs = getFileSystem(f, false);
      return fs.rename(f, t);
    }
    catch (IOException e) {
      throw new HDFSIOException(toPath, CONF.toString(), e);
    }
  }

  @Override
  public OutputStream create(String path, boolean overwrite) {
    Path p = new Path(path);
    try {
      FileSystem fs = getFileSystem(p, false);
      return fs.create(p, overwrite);
    }
    catch (IOException e) {
      throw new HDFSIOException(path, CONF.toString(), e);
    }
  }

  @Override
  public boolean delete(String path) {
    Path p = new Path(path);
    try {
      FileSystem fs = getFileSystem(p, false);
      return fs.delete(p, true);
    }
    catch (IOException e) {
      throw new HDFSIOException(path, CONF.toString(), e);
    }
  }

  @Override
  public boolean canHandle(String path) {
    URI uri = new Path(path).toUri();
    try {
      // Skip undefined scheme
      return uri.getScheme() != null && FileSystem.getFileSystemClass(uri.getScheme(), CONF) != null;
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Retrieves FileSystem instance - the sole purpose of this method is to make sure delegation tokens
   * are acquired before we attempt to get FileSystem instance.
   * 
   * @param path hdfs path
   * @param assumeTokensAcquired set to true if this function is called for retrieving data from DKV
   *                             in this case we assume that a higher-level method already acquired the tokens 
   * @return FileSystem instance
   * @throws IOException ouch...
   */
  private static FileSystem getFileSystem(Path path, boolean assumeTokensAcquired) throws IOException {
    if (! assumeTokensAcquired) {
      startDelegationTokenRefresher(path);
    }
    return FileSystem.get(path.toUri(), CONF);
  }
}
