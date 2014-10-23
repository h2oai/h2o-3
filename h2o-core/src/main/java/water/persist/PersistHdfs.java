package water.persist;

import java.io.*;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.concurrent.Callable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Callable;

import com.google.common.io.ByteStreams;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;
import org.apache.hadoop.fs.s3.S3Exception;
//import org.jets3t.service.S3ServiceException;

import water.*;

import water.fvec.HDFSFileVec;
import water.fvec.Vec;
import water.util.FileUtils;
import water.util.Log;

/**
 * HDFS persistence layer.
 */
public final class PersistHdfs extends Persist {
  /** Globally shared HDFS configuration. */
  public static final Configuration CONF;
  /** Root path of HDFS */
  private final Path _iceRoot;

  // Returns String with path for given key.
  private static String getPathForKey(Key k) {
    final int off = k._kb[0]==Key.CHK ? Vec.KEY_PREFIX_LEN : 0;
    return new String(k._kb,off,k._kb.length-off);
  }

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
      if( H2O.ARGS.hdfs != null && H2O.ARGS.hdfs.length() > 0 ) {
        // setup default remote Filesystem - for version 0.21 and higher
        conf.set("fs.defaultFS", H2O.ARGS.hdfs);
        // To provide compatibility with version 0.20.0 it is necessary to setup the property
        // fs.default.name which was in newer version renamed to 'fs.defaultFS'
        conf.set("fs.default.name", H2O.ARGS.hdfs);
      }
    }
    CONF = conf;
  }
  
  // Loading HDFS files
  PersistHdfs() { _iceRoot = null; }

  // Loading/Writing ice to HDFS
  PersistHdfs(URI uri) {
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
    final byte[] b = MemoryManager.malloc1(v._max);
    long skip = 0;
    Key k = v._key;
    if(k._kb[0] == Key.CHK){
      skip = water.fvec.NFSFileVec.chunkOffset(k); // The offset
    }
    final Path p = _iceRoot == null?new Path(getPathForKey(k)):new Path(_iceRoot, getIceName(v));
    final long skip_ = skip;
    run(new Callable() {
      @Override public Object call() throws Exception {
        FileSystem fs = FileSystem.get(p.toUri(), CONF);
        FSDataInputStream s = null;
        try {
          s = fs.open(p);
          // NOTE:
          // The following line degrades performance of HDFS load from S3 API: s.readFully(skip,b,0,b.length);
          // Google API's simple seek has better performance
          // Load of 300MB file via Google API ~ 14sec, via s.readFully ~ 5min (under the same condition)
          ByteStreams.skipFully(s, skip_);
          ByteStreams.readFully(s, b);
          assert v.isPersisted();
        } finally {
          FileUtils.close(s);
        }
        return null;
      }
    }, true, v._max);
  return b;
  }

  @Override public void store(Value v) {
    // Should be used only if ice goes to HDFS
    assert this == getIce();
    assert !v.isPersisted();

    byte[] m = v.memOrLoad();
    assert (m == null || m.length == v._max); // Assert not saving partial files
    store(new Path(_iceRoot, getIceName(v)), m);
    v.setdsk(); // Set as write-complete to disk
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
    assert this == getIce();
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
        TimeLine.record_IOclose(start_ns, start_io_ms, read ? 1 : 0, size, Value.HDFS);
        break;
        // Explicitly ignore the following exceptions but
        // fail on the rest IOExceptions
      } catch( EOFException e ) {
        ignoreAndWait(e, false);
      } catch( SocketTimeoutException e ) {
        ignoreAndWait(e, false);
      } catch( S3Exception e ) {
        // Preserve S3Exception before IOException
        // Since this is tricky code - we are supporting different HDFS version
        // New version declares S3Exception as IOException
        // But old versions (0.20.xxx) declares it as RuntimeException
        // So we have to catch it before IOException !!!
        ignoreAndWait(e, false);
      } catch( IOException e ) {
        ignoreAndWait(e, true);
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
    try {
      if( fs == null ) return;

      Futures futures = new Futures();
      for( FileStatus file : fs.listStatus(p) ) {
        Path pfs = file.getPath();
        if( file.isDirectory() ) {
          addFolder(fs, pfs, keys, failed);
        } else {
          long size = file.getLen();
          Key k = null;
          keys.add((k = HDFSFileVec.make(file, futures)).toString());
          Log.debug("PersistHdfs: DKV.put(" + k + ")");
        }
      }
    } catch( Exception e ) {
      Log.err(e);
      failed.add(p.toString());
    }
  }
}
