package water.persist;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
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
  // FIXME: do not share it via classes, but initialize it by object
  static {
    Configuration conf = null;
    if( H2O.ARGS.hdfs_config != null ) {
      conf = new Configuration();
      File p = new File(H2O.ARGS.hdfs_config);
      if( !p.exists() ) H2O.die("Unable to open hdfs configuration file " + p.getAbsolutePath());
      conf.addResource(new Path(p.getAbsolutePath()));
      Log.debug("resource ", p.getAbsolutePath(), " added to the hadoop configuration");
    } else {
      conf = new Configuration();
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
  public PersistHdfs() { _iceRoot = null; }
  public void cleanUp() { throw H2O.unimpl(); /** user-mode swapping not implemented */}

  // Loading/Writing ice to HDFS
  public PersistHdfs(URI uri) {
    try {
      _iceRoot = new Path(uri + "/ice" + H2O.SELF_ADDRESS.getHostAddress() + "-" + H2O.API_PORT);
      // Make the directory as-needed
      FileSystem fs = FileSystem.get(_iceRoot.toUri(), CONF);
      fs.mkdirs(_iceRoot);
    } catch( Exception e ) {
      throw Log.throwErr(e);
    }
  }

  /** InputStream from a HDFS-based Key */
  /*public static InputStream openStream(Key k, Job pmon) throws IOException {
    H2OHdfsInputStream res = null;
    Path p = new Path(k.toString());
    try {
      res = new H2OHdfsInputStream(p, 0, pmon);
    } catch( IOException e ) {
      try {
        Thread.sleep(1000);
      } catch( Exception ex ) {}
      Log.warn("Error while opening HDFS key " + k.toString() + ", will wait and retry.");
      res = new H2OHdfsInputStream(p, 0, pmon);
    }
    return res;
  }*/

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
    long end, start = System.currentTimeMillis();
    final byte[] b = MemoryManager.malloc1(v._max);
    Key k = v._key;

    long skip = k.isChunkKey() ? water.fvec.NFSFileVec.chunkOffset(k) : 0;
    final Path p = _iceRoot == null?new Path(getPathForKey(k)):new Path(_iceRoot, getIceName(v));
    final long skip_ = skip;
    run(new Callable() {
      @Override public Object call() throws Exception {
        FileSystem fs = FileSystem.get(p.toUri(), CONF);
        FSDataInputStream s = null;
        try {
//          fs.getDefaultBlockSize(p);

            s = fs.open(p);
//          System.out.println("default block size = " + fs.getDefaultBlockSize(p));
//          FileStatus f = fs.getFileStatus(p);
//          BlockLocation [] bs = fs.getFileBlockLocations(f,0,f.getLen());
//          System.out.println(Arrays.toString(bs));
          if (p.toString().toLowerCase().startsWith("maprfs:")) {
            // MapR behaves really horribly with the google ByteStreams code below.
            // Instead of skipping by seeking, it skips by reading and dropping.  Very bad.
            // Use the HDFS API here directly instead.

            s.seek(skip_);
            s.readFully(b);
          }
          else {
            // NOTE:
            // The following line degrades performance of HDFS load from S3 API: s.readFully(skip,b,0,b.length);
            // Google API's simple seek has better performance
            // Load of 300MB file via Google API ~ 14sec, via s.readFully ~ 5min (under the same condition)
//            ByteStreams.skipFully(s, skip_);
//            ByteStreams.readFully(s, b);
            s.seek(skip_);
            s.readFully(b);
          }
          assert v.isPersisted();
        } finally {
          s.getWrappedStream().close();
          FileUtils.closeSilently(s);
        }
        return null;
      }
    }, true, v._max);
    end = System.currentTimeMillis();
    if (end-start > 1000) // Only log read that took over 1 second to complete
      Log.debug("Slow Read: "+(end-start)+" millis to get bytes "+skip_ +"-"+(skip_+b.length)+" in HDFS read.");

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

  public static void store(final Path path, final byte[] data) {
    run(new Callable() {
      @Override public Object call() throws Exception {
        FileSystem fs = FileSystem.get(path.toUri(), CONF);
        fs.mkdirs(path.getParent());
        FSDataOutputStream s = fs.create(path);
        try {
          s.write(data);
        } finally {
          s.close();
        }
        return null;
      }
    }, false, data.length);
  }

  @Override public void delete(final Value v) {
    assert this == H2O.getPM().getIce();
    assert !v.isPersisted();   // Upper layers already cleared out

    run(new Callable() {
      @Override public Object call() throws Exception {
        Path p = new Path(_iceRoot, getIceName(v));
        FileSystem fs = FileSystem.get(p.toUri(), CONF);
        fs.delete(p, true);
        return null;
      }
    }, false, 0);
  }

  private static class Size {
    int _value;
  }

  private static void run(Callable c, boolean read, int size) {
    // Count all i/o time from here, including all retry overheads
    long start_io_ms = System.currentTimeMillis();
    while( true ) {
      try {
        long start_ns = System.nanoTime(); // Blocking i/o call timing - without counting repeats
        c.call();
//        TimeLine.record_IOclose(start_ns, start_io_ms, read ? 1 : 0, size, Value.HDFS);
        break;
        // Explicitly ignore the following exceptions but
        // fail on the rest IOExceptions
      } catch( EOFException e ) {
        e.printStackTrace();
        System.out.println(e.getMessage());
        ignoreAndWait(e, true);
      } catch( SocketTimeoutException e ) {
        ignoreAndWait(e, false);
      } catch( IOException e ) {
        // Newer versions of Hadoop derive S3Exception from IOException
        if (e.getClass().getName().contains("S3Exception")) {
          ignoreAndWait(e, true);
        } else {
          ignoreAndWait(e, true);
        }
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

  public static void addFolder(Path p, ArrayList<String> keys,ArrayList<String> failed) throws IOException {
    FileSystem fs = FileSystem.get(p.toUri(), PersistHdfs.CONF);
    if(!fs.exists(p)){
      failed.add("Path does not exist: '" + p.toString() + "'");
      return;
    }
    addFolder(fs, p, keys, failed);
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
          Key k = HDFSFileVec.make(pfs.toString(), file.getLen(), futures);
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

    FileSystem fs = FileSystem.get(uri, PersistHdfs.CONF);
    FileStatus[] fstatus = fs.listStatus(new Path(uri));
    assert fstatus.length == 1 : "Expected uri to single file, but uri is " + uri;

    return HDFSFileVec.make(fstatus[0].getPath().toString(), fstatus[0].getLen());
  }

  public static FileSystem getFS(String path) throws IOException {
    try {
      return getFS(new URI(path));
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
  public static FileSystem getFS(URI uri) throws IOException {
    return FileSystem.get(uri, PersistHdfs.CONF);
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
    // Get HDFS configuration
    Configuration conf = PersistHdfs.CONF;

    // Hack around s3://
//    filter = convertS3toS3N(filter);

    // Handle S3N bare buckets - s3n://bucketname should be suffixed by '/'
    // or underlying Jets3n will throw NPE. filter name should be s3n://bucketname/
    if (isBareS3NBucketWithoutTrailingSlash(filter)) {
      filter += "/";
    }
    // Output matches
    ArrayList<String> array = new ArrayList<String>();
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
      FileSystem fs = FileSystem.get(p.toUri(), conf);
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
      URI uri = p.toUri();
      FileSystem fs = FileSystem.get(uri, CONF);
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
    URI uri = p.toUri();
    try {
      FileSystem fs = FileSystem.get(uri, CONF);
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
    URI uri = p.toUri();
    try {
      FileSystem fs = FileSystem.get(uri, CONF);
      return fs.isDirectory(p);
    }
    catch (IOException e) {
      throw new HDFSIOException(path, CONF.toString(), e);
    }
  }

  @Override
  public long length(String path) {
    Path p = new Path(path);
    URI uri = p.toUri();
    try {
      FileSystem fs = FileSystem.get(uri, CONF);
      return fs.getFileStatus(p).getLen();
    }
    catch (IOException e) {
      throw new HDFSIOException(path, CONF.toString(), e);
    }
  }

  @Override
  public InputStream open(String path) {
    Path p = new Path(path);
    URI uri = p.toUri();
    try {
      FileSystem fs = FileSystem.get(uri, CONF);
      return fs.open(p);
    }
    catch (IOException e) {
      throw new HDFSIOException(path, CONF.toString(), e);
    }
  }

  @Override
  public boolean mkdirs(String path) {
    Path p = new Path(path);
    URI uri = p.toUri();
    try {
      FileSystem fs = FileSystem.get(uri, CONF);
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
    URI uri = f.toUri();
    try {
      FileSystem fs = FileSystem.get(uri, CONF);
      return fs.rename(f, t);
    }
    catch (IOException e) {
      throw new HDFSIOException(toPath, CONF.toString(), e);
    }
  }

  @Override
  public OutputStream create(String path, boolean overwrite) {
    Path p = new Path(path);
    URI uri = p.toUri();
    try {
      FileSystem fs = FileSystem.get(uri, CONF);
      return fs.create(p, overwrite);
    }
    catch (IOException e) {
      throw new HDFSIOException(path, CONF.toString(), e);
    }
  }

  @Override
  public boolean delete(String path) {
    Path p = new Path(path);
    URI uri = p.toUri();
    try {
      FileSystem fs = FileSystem.get(uri, CONF);
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
}
