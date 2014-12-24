package water.persist;

import java.io.*;
import java.net.URI;
//import java.net.SocketTimeoutException;
//import java.util.Arrays;
//import java.util.Properties;

import water.Key;
import water.Value;
import water.H2O;
import water.Job;
//import water.fvec.Vec;
import water.fvec.Vec;
import water.util.Log;
//import water.util.RIStream;
//
//import com.amazonaws.ClientConfiguration;
//import com.amazonaws.Protocol;
//import com.amazonaws.services.s3.AmazonS3;
//import com.amazonaws.services.s3.AmazonS3Client;
//import com.amazonaws.services.s3.model.*;

/** Persistence backend for S3 */
public final class PersistS3 extends Persist {
  //
  // FIXME: back port here authorization chain from h2o1
  //
  private static final String HELP = "You can specify a credentials properties file with the -aws_credentials command line switch.";

  private static final String KEY_PREFIX = "s3://";
  private static final int KEY_PREFIX_LEN = KEY_PREFIX.length();

  private static final Object _lock = new Object();
  private static volatile AmazonS3 _s3;
  private static class AmazonS3 { }
  /** Initialize the AWS S3 client */
  public static AmazonS3 getClient() {
    if( _s3 == null ) {
      synchronized( _lock ) {
        if( _s3 == null ) {
          try {
            //_s3 = new AmazonS3Client(H2O.getAWSCredentials(), s3ClientCfg());
            throw H2O.unimpl();
          } catch( Throwable e ) {
            StringBuilder msg = new StringBuilder();
            msg.append(e.getMessage() + "\n");
            msg.append("Unable to load S3 credentials.");
            if( H2O.ARGS.aws_credentials == null ) msg.append(HELP);
            Log.throwErr(new RuntimeException(msg.toString()));
          }
        }
      }
    }
    return _s3;
  }
  
  //public static final class H2OS3InputStream extends RIStream {
  //  Key _k;
  //  long _to;
  //  String[] _bk;
  //
  //  protected InputStream open(long offset) {
  //    return getClient().getObject(new GetObjectRequest(_bk[0], _bk[1]).withRange(offset, _to)).getObjectContent();
  //  }
  //
  //  public H2SO3InputStream(Key k, ProgressMonitor pmon) {
  //    this(k, pmon, 0, Long.MAX_VALUE);
  //  }
  //
  //  public H2SO3InputStream(Key k, ProgressMonitor pmon, long from, long to) {
  //    super(from, pmon);
  //    _k = k;
  //    _to = Math.min(DKV.get(k).length() - 1, to);
  //    _bk = decodeKey(k);
  //    open();
  //  }
  //}
  //
  /** InputStream from a S3-based Key */
  public static InputStream openStream(Key k, Job pmon) throws IOException {
    throw H2O.unimpl();
  //  return new H2OS3InputStream(k, pmon);
  }
  //
  //public static Key loadKey(S3ObjectSummary obj) throws IOException {
  //  Key k = encodeKey(obj.getBucketName(), obj.getKey());
  //  long size = obj.getSize();
  //  Value val = null;
  //  if( obj.getKey().endsWith(Extensions.HEX) ) { // Hex file?
  //    int sz = (int) Math.min(ValueArray.CHUNK_SZ, size);
  //    byte[] mem = MemoryManager.malloc1(sz); // May stall a long time to get memory
  //    S3ObjectInputStream is = getObjectForKey(k, 0, ValueArray.CHUNK_SZ).getObjectContent();
  //    int off = 0;
  //    while( off < sz )
  //      off += is.read(mem, off, sz - off);
  //    ValueArray ary = new ValueArray(k, sz).read(new AutoBuffer(mem));
  //    val = new Value(k, ary, Value.S3);
  //  } else if( size >= 2 * ValueArray.CHUNK_SZ ) {
  //    // ValueArray byte wrapper over a large file
  //    val = new Value(k, new ValueArray(k, size), Value.S3);
  //  } else {
  //    val = new Value(k, (int) size, Value.S3); // Plain Value
  //    val.setdsk();
  //  }
  //  DKV.put(k, val);
  //  return k;
  //}
  //

  // file implementation -------------------------------------------------------

  @Override public byte[] load(Value v) {
    throw H2O.unimpl();
  //  long start_io_ms = System.currentTimeMillis();
  //  byte[] b = MemoryManager.malloc1(v._max);
  //  Key k = v._key;
  //  long skip = 0;
  //  // Convert an arraylet chunk into a long-offset from the base file.
  //  if( k._kb[0] == Key.ARRAYLET_CHUNK ) {
  //    skip = ValueArray.getChunkOffset(k); // The offset
  //    k = ValueArray.getArrayKey(k); // From the base file key
  //    if( k.toString().endsWith(Extensions.HEX) ) { // Hex file?
  //      int value_len = DKV.get(k).memOrLoad().length; // How long is the ValueArray header?
  //      skip += value_len;    // Skip header
  //    }
  //  }
  //  // Too complicate matters, S3 likes to reset connections when H2O hits it
  //  // too hard.  We "fix" this by just trying again, assuming we're getting
  //  // hit with a bogus resource limit (H2O doing a parse looks like a DDOS to
  //  // Amazon S3).
  //  S3ObjectInputStream s = null;
  //
  //  while( true ) {             // Loop, in case we get premature EOF's
  //    try {
  //      long start_ns = System.nanoTime(); // Blocking i/o call timing - without counting repeats
  //      s = getObjectForKey(k, skip, v._max).getObjectContent();
  //      ByteStreams.readFully(s, b); // delegate work to Google (it reads the byte buffer in a cycle as we did)
  //      assert v.isPersisted();
  //      TimeLine.record_IOclose(start_ns, start_io_ms, 1/* read */, v._max, Value.S3);
  //      return b;
  //      // Explicitly ignore the following exceptions but
  //      // fail on the rest IOExceptions
  //    } catch( EOFException e ) {
  //      ignoreAndWait(e, false);
  //    } catch( SocketTimeoutException e ) {
  //      ignoreAndWait(e, false);
  //    } catch( IOException e ) {
  //      ignoreAndWait(e, true);
  //    } finally {
  //      try {
  //        if( s != null ) s.close();
  //      } catch( IOException e ) {}
  //    }
  //  }
  }

  //private static void ignoreAndWait(final Exception e, boolean printException) {
  //  H2O.ignore(e, "Hit the S3 reset problem, waiting and retrying...", printException);
  //  try {
  //    Thread.sleep(500);
  //  } catch( InterruptedException ie ) {}
  //}

  @Override public void store(Value v) {
    throw H2O.unimpl();
  //  if( !v._key.home() ) return;
  //  // Never store arraylets on S3, instead we'll store the entire array.
  //  assert !v.isArray();
  //
  //  Key dest = PersistS3Task.init(v);
  //  PersistS3Task.run(dest, v, null, null);
  }
  //
  ///**
  // * Creates the key for given S3 bucket and key. Returns the H2O key, or null if the key cannot be
  // * created.
  // *
  // * @param bucket
  // *          Bucket name
  // * @param key
  // *          Key name (S3)
  // * @return H2O key pointing to the given bucket and key.
  // */
  //public static Key encodeKey(String bucket, String key) {
  //  Key res = encodeKeyImpl(bucket, key);
////    assert checkBijection(res, bucket, key);
  //  return res;
  //}
  //
  ///**
  // * Decodes the given H2O key to the S3 bucket and key name. Returns the array of two strings,
  // * first one is the bucket name and second one is the key name.
  // *
  // * @param k
  // *          Key to be decoded.
  // * @return Pair (array) of bucket name and key name.
  // */
  //public static String[] decodeKey(Key k) {
  //  return decodeKeyImpl(k);
////    assert checkBijection(k, res[0], res[1]);
////    return res;
  //}
  //
////  private static boolean checkBijection(Key k, String bucket, String key) {
////    Key en = encodeKeyImpl(bucket, key);
////    String[] de = decodeKeyImpl(k);
////    boolean res = Arrays.equals(k._kb, en._kb) && bucket.equals(de[0]) && key.equals(de[1]);
////    assert res : "Bijection failure:" + "\n\tKey 1:" + k + "\n\tKey 2:" + en + "\n\tBkt 1:" + bucket + "\n\tBkt 2:"
////        + de[0] + "\n\tStr 1:" + key + "\n\tStr 2:" + de[1] + "";
////    return res;
////  }
  //
  //private static Key encodeKeyImpl(String bucket, String key) {
  //  return Key.make(KEY_PREFIX + bucket + '/' + key);
  //}
  //
  //private static String[] decodeKeyImpl(Key k) {
  //  String s = new String((k._kb[0] == Key.CHK)?Arrays.copyOfRange(k._kb, Vec.KEY_PREFIX_LEN, k._kb.length):k._kb);
  //  assert s.startsWith(KEY_PREFIX) && s.indexOf('/') >= 0 : "Attempting to decode non s3 key: " + k;
  //  s = s.substring(KEY_PREFIX_LEN);
  //  int dlm = s.indexOf('/');
  //  String bucket = s.substring(0, dlm);
  //  String key = s.substring(dlm + 1);
  //  return new String[] { bucket, key };
  //}
  //
  //// Gets the S3 object associated with the key that can read length bytes from offset
  //private static S3Object getObjectForKey(Key k, long offset, long length) throws IOException {
  //  String[] bk = decodeKey(k);
  //  GetObjectRequest r = new GetObjectRequest(bk[0], bk[1]);
  //  r.setRange(offset, offset + length - 1); // Range is *inclusive* according to docs???
  //  return getClient().getObject(r);
  //}
  //
  //// Gets the object metadata associated with given key.
  //private static ObjectMetadata getObjectMetadataForKey(Key k) {
  //  String[] bk = decodeKey(k);
  //  assert (bk.length == 2);
  //  return getClient().getObjectMetadata(bk[0], bk[1]);
  //}
  //
  ///** S3 socket timeout property name */
  //public final static String S3_SOCKET_TIMEOUT_PROP = "water.s3.socketTimeout";
  ///** S3 connection timeout property name */
  //public final static String S3_CONNECTION_TIMEOUT_PROP = "water.s3.connectionTimeout";
  ///** S3 maximal error retry number */
  //public final static String S3_MAX_ERROR_RETRY_PROP = "water.s3.maxErrorRetry";
  ///** S3 maximal http connections */
  //public final static String S3_MAX_HTTP_CONNECTIONS_PROP = "water.s3.maxHttpConnections";
  //
  //static ClientConfiguration s3ClientCfg() {
  //  ClientConfiguration cfg = new ClientConfiguration();
  //  Properties prop = System.getProperties();
  //  if( prop.containsKey(S3_SOCKET_TIMEOUT_PROP) ) cfg.setSocketTimeout(Integer.getInteger(S3_SOCKET_TIMEOUT_PROP));
  //  if( prop.containsKey(S3_CONNECTION_TIMEOUT_PROP) ) cfg.setConnectionTimeout(Integer
  //      .getInteger(S3_CONNECTION_TIMEOUT_PROP));
  //  if( prop.containsKey(S3_MAX_ERROR_RETRY_PROP) ) cfg.setMaxErrorRetry(Integer.getInteger(S3_MAX_ERROR_RETRY_PROP));
  //  if( prop.containsKey(S3_MAX_HTTP_CONNECTIONS_PROP) ) cfg.setMaxConnections(Integer
  //      .getInteger(S3_MAX_HTTP_CONNECTIONS_PROP));
  //  cfg.setProtocol(Protocol.HTTP);
  //  return cfg;
  //}
  //

  @Override public void delete(Value v) { throw H2O.fail(); }

  @Override
  public Key uriToKey(URI uri) throws IOException {
    throw H2O.unimpl();
  }
}
