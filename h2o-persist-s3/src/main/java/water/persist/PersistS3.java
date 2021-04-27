package water.persist;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.*;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
import com.amazonaws.services.s3.model.*;
import hex.genmodel.utils.IOUtils;
import org.apache.log4j.Logger;
import water.*;
import water.fvec.FileVec;
import water.fvec.S3FileVec;
import water.fvec.Vec;
import water.util.ByteStreams;
import water.util.FileUtils;
import water.util.Log;

import java.io.*;
import java.net.URI;
import java.util.*;

import static water.H2O.OptArgs.SYSTEM_PROP_PREFIX;

/** Persistence backend for S3 */
public final class PersistS3 extends Persist {
  private static final Logger LOG = Logger.getLogger(PersistS3.class);

  private static final String KEY_PREFIX = "s3://";
  private static final int KEY_PREFIX_LEN = KEY_PREFIX.length();

  private static final Object _lock = new Object();
  private static volatile AmazonS3 _s3;

  // for unit testing
  static void setClient(AmazonS3 s3) {
    _s3 = s3;
  }

  public static AmazonS3 getClient() {
    if (_s3 == null) {
      synchronized (_lock) {
        if( _s3 == null ) {
          try {
            H2OAWSCredentialsProviderChain c = new H2OAWSCredentialsProviderChain();
            c.setReuseLastProvider(false);
            ClientConfiguration cc = s3ClientCfg();
            _s3 = configureClient(new AmazonS3Client(c, cc));
          } catch( Throwable e ) {
            e.printStackTrace();
            StringBuilder msg = new StringBuilder();
            msg.append(e.getMessage() + "\n");
            msg.append("Unable to load S3 credentials.");
            throw new RuntimeException(msg.toString(), e);
          }
        }
      }
    }
    return _s3;
  }

  /** Modified version of default credentials provider which includes H2O-specific
   * credentials provider.
   */
  public static class H2OAWSCredentialsProviderChain extends AWSCredentialsProviderChain {
    public H2OAWSCredentialsProviderChain() {
      super(
              new H2ODynamicCredentialsProvider(),
              new H2OArgCredentialsProvider(),
              new InstanceProfileCredentialsProvider(),
              new EnvironmentVariableCredentialsProvider(),
              new SystemPropertiesCredentialsProvider(),
              new ProfileCredentialsProvider()
      );
    }
  }

  /**
   * Holds basic credentials (Secret key ID + Secret access key) pair.
   */
  private static final class H2ODynamicCredentialsProvider implements AWSCredentialsProvider {

    @Override
    public AWSCredentials getCredentials() {
      final IcedS3Credentials s3Credentials = DKV.getGet(IcedS3Credentials.S3_CREDENTIALS_DKV_KEY);

      if (s3Credentials != null && s3Credentials.isAWSCredentialsAuth()) {
        return new BasicAWSCredentials(s3Credentials._secretKeyId, s3Credentials._secretAccessKey);
      } else if (s3Credentials != null && s3Credentials.isAWSSessionTokenAuth()) {
        return new BasicSessionCredentials(s3Credentials._secretKeyId, s3Credentials._secretAccessKey,
                s3Credentials._sessionToken);
      } else {
        throw new AmazonClientException("No Amazon S3 credentials set directly.");
      }
    }

    @Override
    public void refresh() {
      // No actions taken on refresh
    }
  }

  /** A simple credentials provider reading file-based credentials from given
   * command argument <code>--aws_credentials</code>.
   */
  static class H2OArgCredentialsProvider implements AWSCredentialsProvider {

    // Default location of the AWS credentials file
    public static final String DEFAULT_CREDENTIALS_LOCATION = "AwsCredentials.properties";
    
    @Override public AWSCredentials getCredentials() {
      File credentials = new File(H2O.ARGS.aws_credentials != null ? H2O.ARGS.aws_credentials : DEFAULT_CREDENTIALS_LOCATION);
      try {
        return new PropertiesCredentials(credentials);
      } catch (IOException e) {
        LOG.debug(
            "Unable to load AWS credentials from file " + credentials + 
                "; exists? " + credentials.exists() + ", canRead? " + credentials.canRead() + 
                ", size=" + credentials.length() + "; problem: " + e.getMessage());
        throw new AmazonClientException(
            "PersistS3. Unable to load AWS credentials from file " + credentials + ": " + e.getMessage());
      }
    }

    @Override public void refresh() {}

    @Override
    public String toString() {
      return getClass().getSimpleName();
    }
  }

  @Override
  public boolean exists(String path) {
    String[] bk = decodePath(path);
    ObjectListing objects = getClient().listObjects(bk[0], bk[1]);
    return !objects.getObjectSummaries().isEmpty();
  }

  @Override
  public InputStream open(String path) {
    String[] bk = decodePath(path);
    GetObjectRequest r = new GetObjectRequest(bk[0], bk[1]);
    S3Object s3obj = getClient().getObject(r);
    return s3obj.getObjectContent();
  }

  @Override
  public OutputStream create(String path, boolean overwrite) {
    String[] bk = decodePath(path);
    final File tmpFile;
    try {
      tmpFile = File.createTempFile("h2o-export", ".bin");
      tmpFile.deleteOnExit();
    } catch (IOException e) {
      throw new RuntimeException("Failed to create temporary file for S3 object upload", e);
    }
    Runnable callback = new PutObjectCallback(getClient(), tmpFile, true, bk[0], bk[1]);
    try {
      return new CallbackFileOutputStream(tmpFile, callback);
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e); // should never happen
    }
  }
  
  static class PutObjectCallback implements Runnable {
    private final AmazonS3 _client;
    private final File _file;
    private final boolean _deleteOnDone;
    private final String _bucketName;
    private final String _key;

    public PutObjectCallback(AmazonS3 client, File file, boolean deleteOnDone, String bucketName, String key) {
      _client = client;
      _file = file;
      _deleteOnDone = deleteOnDone;
      _bucketName = bucketName;
      _key = key;
    }

    @Override
    public void run() {
      try {
        PutObjectRequest request = new PutObjectRequest(_bucketName, _key, _file);
        PutObjectResult result = _client.putObject(request);
        Log.info("Object `" + _key + "` uploaded to bucket `" + _bucketName + "`, ETag=`" + result.getETag() + "`.");
      } finally {
        if (_deleteOnDone) {
          boolean deleted = _file.delete();
          if (!deleted) {
            LOG.warn("Temporary file `" + _file.getAbsolutePath() + "` was not deleted. Please delete manually.");
          }
        }
      }
    }
  } 

  static class CallbackFileOutputStream extends FileOutputStream {
    private final Object closeLock = new Object();
    private volatile boolean closed = false;
    private final Runnable callback;
    
    public CallbackFileOutputStream(File file, Runnable callback) throws FileNotFoundException {
      super(file);
      this.callback = callback;
    }

    @Override
    public void close() throws IOException {
      synchronized (closeLock) {
        if (closed) {
          super.close();
          return; // run callback only once
        }
        closed = true;
      }
      callback.run();
    }
  }

  public static Key loadKey(ObjectListing listing, S3ObjectSummary obj) throws IOException {
    // Note: Some of S3 implementations does not fill bucketName of returned object (for example, Minio).
    // So guess it based on returned ObjectListing
    String bucketName = obj.getBucketName() == null ? listing.getBucketName() : obj.getBucketName();
    return S3FileVec.make(encodePath(bucketName, obj.getKey()),obj.getSize());
  }


  private static void processListing(ObjectListing listing, String pattern, ArrayList<String> succ, ArrayList<String> fail, boolean doImport) {
    if( pattern != null && pattern.isEmpty()) pattern = null;
    for( S3ObjectSummary obj : listing.getObjectSummaries() ) {
      if (obj.getKey().endsWith("/")) continue;
      if (pattern != null && !obj.getKey().matches(pattern)) continue;
      try {
        if (doImport) {
          Key k = loadKey(listing, obj);
          succ.add(k.toString());
        } else {
          succ.add(obj.getKey());
        }
      } catch( IOException e ) {
        fail.add(obj.getKey());
      }
    }
  }
  public void importFiles(String path, String pattern, ArrayList<String> files, ArrayList<String> keys, ArrayList<String> fails, ArrayList<String> dels) {
    LOG.info("ImportS3 processing (" + path + ")");
    // List of processed files
    AmazonS3 s3 = getClient();
    String [] parts = decodePath(path);
    ObjectListing currentList = s3.listObjects(parts[0], parts[1]);
    processListing(currentList, pattern, files, fails, true);
    while(currentList.isTruncated()){
      currentList = s3.listNextBatchOfObjects(currentList);
      processListing(currentList, pattern, files, fails, true);
    }
    keys.addAll(files);
    // write barrier was here : DKV.write_barrier();
  }

  // file implementation -------------------------------------------------------

  // Read up to 'len' bytes of Value. Value should already be persisted to
  // disk. A racing delete can trigger a failure where we get a null return,
  // but no crash (although one could argue that a racing load&delete is a bug
  // no matter what).
  @Override public byte[] load(Value v) {
    long start_io_ms = System.currentTimeMillis();
    byte[] b = MemoryManager.malloc1(v._max);
    Key k = v._key;
    long skip = 0;
    // Skip offset based on chunk number
    if(k._kb[0] == Key.CHK)
      skip = FileVec.chunkOffset(k); // The offset
    // Too complicate matters, S3 likes to reset connections when H2O hits it
    // too hard.  We "fix" this by just trying again, assuming we're getting
    // hit with a bogus resource limit (H2O doing a parse looks like a DDOS to
    // Amazon S3).
    S3ObjectInputStream s = null;

    while( true ) {             // Loop, in case we get premature EOF's
      try {
        long start_ns = System.nanoTime(); // Blocking i/o call timing - without counting repeats
        s = getObjectForKey(k, skip, v._max).getObjectContent();
        ByteStreams.readFully(s, b); // delegate work to Google (it reads the byte buffer in a cycle as we did)
        assert v.isPersisted();
//        TimeLine.record_IOclose(start_ns, start_io_ms, 1/* read */, v._max, Value.S3);
        return b;
        // Explicitly ignore the following exceptions but
        // fail on the rest IOExceptions
      } catch( IOException e ) {
        ignoreAndWait(e);
      } finally {
        try {
          if( s != null ) s.close();
        } catch( IOException e ) {}
      }
    }
  }

  private static void ignoreAndWait(final Exception e) {
    LOG.debug("Hit the S3 reset problem, waiting and retrying...", e);
    try {
      Thread.sleep(500);
    } catch( InterruptedException ie ) {}
  }

  // Store Value v to disk.
  @Override public void store(Value v) {
    if( !v._key.home() ) return;
    throw H2O.unimpl();         // VA only
  }

  /**
   * Creates the key for given S3 bucket and key. Returns the H2O key, or null if the key cannot be
   * created.
   *
   * @param bucket
   *          Bucket name
   * @param key
   *          Key name (S3)
   * @return H2O key pointing to the given bucket and key.
   */
  public static Key encodeKey(String bucket, String key) {
    Key res = encodeKeyImpl(bucket, key);
//    assert checkBijection(res, bucket, key);
    return res;
  }

  /**
   * Decodes the given H2O key to the S3 bucket and key name. Returns the array of two strings,
   * first one is the bucket name and second one is the key name.
   *
   * @param k
   *          Key to be decoded.
   * @return Pair (array) of bucket name and key name.
   */
  public static String[] decodeKey(Key k) {
    return decodeKeyImpl(k);
//    assert checkBijection(k, res[0], res[1]);
//    return res;
  }

//  private static boolean checkBijection(Key k, String bucket, String key) {
//    Key en = encodeKeyImpl(bucket, key);
//    String[] de = decodeKeyImpl(k);
//    boolean res = Arrays.equals(k._kb, en._kb) && bucket.equals(de[0]) && key.equals(de[1]);
//    assert res : "Bijection failure:" + "\n\tKey 1:" + k + "\n\tKey 2:" + en + "\n\tBkt 1:" + bucket + "\n\tBkt 2:"
//        + de[0] + "\n\tStr 1:" + key + "\n\tStr 2:" + de[1] + "";
//    return res;
//  }

  private static String encodePath(String bucket, String key){
    return KEY_PREFIX + bucket + '/' + key;
  }
  private static Key encodeKeyImpl(String bucket, String key) {
    return Key.make(KEY_PREFIX + bucket + '/' + key);
  }

  /**
   * Decompose S3 name into bucket name and key name
   *
   * @param s generic s3 path (e.g., "s3://bucketname/my/directory/file.ext")
   * @return array of { bucket name, key }
   */
  private static String [] decodePath(String s) {
    assert s.startsWith(KEY_PREFIX) && s.indexOf('/') >= 0 : "Attempting to decode non s3 key: " + s;
    s = s.substring(KEY_PREFIX_LEN);
    int dlm = s.indexOf('/');
    if(dlm < 0) return new String[]{s,null};
    String bucket = s.substring(0, dlm);
    String key = s.substring(dlm + 1);
    return new String[] { bucket, key };
  }
  private static String[] decodeKeyImpl(Key k) {
    String s = new String((k._kb[0] == Key.CHK)?Arrays.copyOfRange(k._kb, Vec.KEY_PREFIX_LEN, k._kb.length):k._kb);
    return decodePath(s);
  }

  // Gets the S3 object associated with the key that can read length bytes from offset
  private static S3Object getObjectForKey(Key k, long offset, long length) throws IOException {
    String[] bk = decodeKey(k);
    GetObjectRequest r = new GetObjectRequest(bk[0], bk[1]);
    r.setRange(offset, offset + length - 1); // Range is *inclusive* according to docs???
    return getClient().getObject(r);
  }

  // Gets the object metadata associated with given key.
  private static ObjectMetadata getObjectMetadataForKey(Key k) {
    String[] bk = decodeKey(k);
    assert (bk.length == 2);
    return getClient().getObjectMetadata(bk[0], bk[1]);
  }

  /** S3 socket timeout property name */
  public final static String S3_SOCKET_TIMEOUT_PROP = SYSTEM_PROP_PREFIX + "persist.s3.socketTimeout";
  /** S3 connection timeout property name */
  public final static String S3_CONNECTION_TIMEOUT_PROP = SYSTEM_PROP_PREFIX + "persist.s3.connectionTimeout";
  /** S3 maximal error retry number */
  public final static String S3_MAX_ERROR_RETRY_PROP = SYSTEM_PROP_PREFIX + "persist.s3.maxErrorRetry";
  /** S3 maximal http connections */
  public final static String S3_MAX_HTTP_CONNECTIONS_PROP = SYSTEM_PROP_PREFIX + "persist.s3.maxHttpConnections";
  /** S3 force HTTP traffic */
  public final static String S3_FORCE_HTTP = SYSTEM_PROP_PREFIX + "persist.s3.force.http";
  /** S3 end-point, for example: "https://localhost:9000 */
  public final static String S3_END_POINT = SYSTEM_PROP_PREFIX + "persist.s3.endPoint";
  /** S3 region, for example "us-east-1",
   * see {@link com.amazonaws.regions.Region#getRegion(com.amazonaws.regions.Regions)} for region list */
  public final static String S3_REGION = SYSTEM_PROP_PREFIX + "persist.s3.region";
  /** Enable S3 path style access via setting the property to true.
   * See: {@link com.amazonaws.services.s3.S3ClientOptions#setPathStyleAccess(boolean)} */
  public final static String S3_ENABLE_PATH_STYLE = SYSTEM_PROP_PREFIX + "persist.s3.enable.path.style";


  static ClientConfiguration s3ClientCfg() {
    ClientConfiguration cfg = new ClientConfiguration();
    Properties prop = System.getProperties();
    if (prop.containsKey(S3_SOCKET_TIMEOUT_PROP)) cfg.setSocketTimeout(Integer.getInteger(S3_SOCKET_TIMEOUT_PROP));
    if (prop.containsKey(S3_CONNECTION_TIMEOUT_PROP)) cfg.setConnectionTimeout(Integer.getInteger(S3_CONNECTION_TIMEOUT_PROP));
    if (prop.containsKey(S3_MAX_ERROR_RETRY_PROP)) cfg.setMaxErrorRetry(Integer.getInteger(S3_MAX_ERROR_RETRY_PROP));
    if (prop.containsKey(S3_MAX_HTTP_CONNECTIONS_PROP)) cfg.setMaxConnections(Integer.getInteger(S3_MAX_HTTP_CONNECTIONS_PROP));
    if (prop.containsKey(S3_FORCE_HTTP)) cfg.setProtocol(Protocol.HTTP);

    return cfg;
  }

  static  AmazonS3Client configureClient(AmazonS3Client s3Client) {
    if (System.getProperty(S3_REGION) != null) {
      String region = System.getProperty(S3_REGION);
      LOG.debug(String.format("S3 region specified: %s", region) );
      s3Client.setRegion(RegionUtils.getRegion(region));
    }
    // Region overrides end-point settings
    if (System.getProperty(S3_END_POINT) != null) {
      String endPoint = System.getProperty(S3_END_POINT);
      LOG.debug(String.format("S3 endpoint specified: %s", endPoint));
      s3Client.setEndpoint(endPoint);
    }
    if (System.getProperty(S3_ENABLE_PATH_STYLE) != null && Boolean.parseBoolean(System.getProperty(S3_ENABLE_PATH_STYLE))) {
      LOG.debug("S3 path style access enabled");
      S3ClientOptions sco = S3ClientOptions.builder()
              .setPathStyleAccess(true)
              .build();
      s3Client.setS3ClientOptions(sco);
    }
    return s3Client;
  }

  @Override public void delete(Value v) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Key uriToKey(URI uri) throws IOException {
    AmazonS3 s3 = getClient();
    // Decompose URI into bucket, key
    String [] parts = decodePath(uri.toString());
    try {
      ObjectMetadata om = s3.getObjectMetadata(parts[0], parts[1]);
      // Voila: create S3 specific key pointing to the file
      return S3FileVec.make(encodePath(parts[0], parts[1]), om.getContentLength());
    } catch (AmazonServiceException e) {
      if (e.getErrorCode().contains("404")) {
        throw new IOException(e);
      } else {
        LOG.error("AWS failed for " + Arrays.toString(parts) + ": " + e.getMessage());
        throw e;
      }
    }
  }

  @Override
  public void cleanUp() { throw H2O.unimpl(); /** user-mode swapping not implemented */}

  static class Cache {
    long _lastUpdated = 0;
    long _timeoutMillis = 5*60*1000;
    String [] _cache = new String[0];

    public boolean containsKey(String k) { return Arrays.binarySearch(_cache,k) >= 0;}
    protected String [] update(){
      LOG.debug("Renewing S3 bucket cache.");
      List<Bucket> l = getClient().listBuckets();
      String [] cache = new String[l.size()];
      int i = 0;
      for (Bucket b : l) cache[i++] = b.getName();
      Arrays.sort(cache);
      return _cache = cache;
    }


    protected String wrapKey(String s) {return "s3://" + s;}

    public ArrayList<String> fetch(String filter, int limit) {
      String [] cache = _cache;
      if(System.currentTimeMillis() > _lastUpdated + _timeoutMillis) {
        cache = update();
        _lastUpdated = System.currentTimeMillis();
      }
      ArrayList<String> res = new ArrayList<>();
      int i = Arrays.binarySearch(cache, filter);
      if (i < 0) i = -i - 1;
      while (i < cache.length && cache[i].startsWith(filter) && (limit < 0 || res.size() < limit))
        res.add(wrapKey(cache[i++]));
      return res;
    }
  }

  private static class KeyCache extends Cache {

    private final String _keyPrefix;
    private final String _bucket;
    public KeyCache(String bucket){
      _bucket = bucket;
      _keyPrefix = super.wrapKey(bucket) + "/";
    }

    @Override
    protected String [] update(){
      LOG.debug("Renewing S3 cache.");
      AmazonS3 s3 = getClient();
      ObjectListing currentList = s3.listObjects(_bucket,"");
      ArrayList<String> res = new ArrayList<>();
      processListing(currentList, null, res, null, false);
      while(currentList.isTruncated()){
        currentList = s3.listNextBatchOfObjects(currentList);
        processListing(currentList, null, res, null, false);
      }
      Collections.sort(res);
      return _cache = res.toArray(new String[res.size()]);
    }
    @Override
    protected String wrapKey(String s) {
      return _keyPrefix + s;
    }
  }


  static volatile Cache _bucketCache = new Cache();
  static volatile HashMap<String, KeyCache> _keyCaches = new HashMap<>();
  @Override
  public List<String> calcTypeaheadMatches(String filter, int limit) {
    String [] parts = decodePath(filter);
    if(parts[1] != null) { // bucket and key prefix
      if(_keyCaches.get(parts[0]) == null) {
        if(!getClient().doesBucketExist(parts[0]))
          return new ArrayList<>();
        _keyCaches.put(parts[0], new KeyCache(parts[0]));
      }
      return _keyCaches.get(parts[0]).fetch(parts[1],limit);
    } else { // no key, only bucket prefix
      return _bucketCache.fetch(parts[0],limit);
    }
  }
}
