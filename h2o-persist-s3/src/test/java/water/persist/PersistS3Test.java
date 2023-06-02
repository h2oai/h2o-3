package water.persist;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import org.apache.commons.io.IOUtils;
import org.apache.hadoop.conf.Configuration;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import water.*;
import water.fvec.Chunk;
import water.fvec.FileVec;
import water.fvec.Frame;
import water.fvec.Vec;
import water.parser.ParseDataset;
import water.parser.ParseSetup;
import water.rapids.Rapids;
import water.rapids.Val;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.FileUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Created by tomas on 6/27/16.
 */
@RunWith(H2ORunner.class)
@CloudSize(1)
public class PersistS3Test extends TestUtil {

  private static final String AWS_ACCESS_KEY_PROPERTY_NAME = "AWS_ACCESS_KEY_ID";
  private static final String AWS_SECRET_KEY_PROPERTY_NAME = "AWS_SECRET_ACCESS_KEY";
  private static final String IRIS_H2O_AWS = "s3://test.0xdata.com/h2o-unit-tests/iris.csv";
  private static final String IRIS_BUCKET_H2O_AWS = "s3://test.0xdata.com/h2o-unit-tests";
  
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Before
  public void resetClientFactory() {
    PersistS3.setClientFactory(null);
  }
  
  private static class XORTask extends MRTask<XORTask> {
    long _res = 0;

    @Override public void map(Chunk c) {
      for(int i = 0; i < c._len; ++i)
        _res ^= c.at8(i);
    }
    @Override public void reduce(XORTask xort){
      _res = _res ^ xort._res;
    }
  }
  @Test
  public void testS3Import() throws Exception {
    Scope.enter();
    try {
      assertExists("smalldata/airlines/AirlinesTrain.csv.zip");
      Key<?> k = H2O.getPM().anyURIToKey(new URI("s3://h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv.zip"));
      Frame fr = DKV.getGet(k);
      FileVec v = (FileVec) fr.anyVec();
      assertNotNull(v);
      // make sure we have some chunks
      int chunkSize = (int) (v.length() / 3);
      v.setChunkSize(fr, chunkSize);
      long xor = new XORTask().doAll(v)._res;
      Key<?> k2 = H2O.getPM().anyURIToKey(new URI(FileUtils.getFile("smalldata/airlines/AirlinesTrain.csv.zip").getAbsolutePath()));
      FileVec v2 = DKV.getGet(k2);
      assertEquals(v2.length(), v.length());
      assertVecEquals(v, v2, 0);
      // make sure we have some chunks
      v2.setChunkSize(chunkSize);
      long xor2 = new XORTask().doAll(v2)._res;
      assertEquals(xor2, xor);
      fr.delete();
      v2.remove();
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testExists() {
    assertTrue(new PersistS3().exists("s3://h2o-public-test-data/smalldata/airlines/"));
    assertTrue(new PersistS3().exists("s3://h2o-public-test-data/smalldata/airlines"));
    assertTrue(new PersistS3().exists("s3://h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv.zip"));
    assertFalse(new PersistS3().exists("s3://h2o-public-test-data/smalldata/airlines/invalid.file"));
    assertFalse(new PersistS3().exists("s3://h2o-public-test-data/smalldata/airli"));
  }

  @Test
  public void testList() {
    Persist.PersistEntry[] entriesNoSlash = new PersistS3().list("s3://h2o-public-test-data/smalldata/airlines");
    assertEquals(entriesNoSlash.length, 100, 80); // at least 20 entries at the time of making of this test
    Persist.PersistEntry[] entriesSlash = new PersistS3().list("s3://h2o-public-test-data/smalldata/airlines/");
    assertArrayEquals(entriesNoSlash, entriesSlash);
    Persist.PersistEntry[] entriesPrefix = new PersistS3().list("s3://h2o-public-test-data/smalldata/airlin");
    assertEquals(entriesPrefix.length, 0);
    Persist.PersistEntry[] entriesOneFile = new PersistS3().list("s3://h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv.zip");
    assertEquals(entriesOneFile.length, 1);
    assertEquals(entriesOneFile[0]._name, "smalldata/airlines/AirlinesTrain.csv.zip");
  }

  @Test
  public void testS3UriToKeyChangedCredentials() throws Exception {
    Scope.enter();
    Key<?> k = null, k2 = null;
    Frame fr = null;
    FileVec v = null, v2 = null;
    Key<?> credentialsKey = Key.make(IcedS3Credentials.S3_CREDENTIALS_DKV_KEY);
    try {
      // This test is only runnable in environment with Amazon credentials properly set {AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY}
      final String accessKey = System.getenv(AWS_ACCESS_KEY_PROPERTY_NAME);
      final String secretKey = System.getenv(AWS_SECRET_KEY_PROPERTY_NAME);

      assumeTrue(accessKey != null);
      assumeTrue(secretKey != null);

      final IcedS3Credentials s3Credentials = new IcedS3Credentials(accessKey, secretKey, null);
      DKV.put(credentialsKey, s3Credentials);
      k = H2O.getPM().anyURIToKey(new URI(IRIS_H2O_AWS));
      fr = DKV.getGet(k);
      v = (FileVec) fr.anyVec();
      assertNotNull(v);
      // make sure we have some chunks
      int chunkSize = (int) (v.length() / 3);
      v.setChunkSize(fr, chunkSize);
      long xor = new XORTask().doAll(v)._res;
      k2 = H2O.getPM().anyURIToKey(new URI(FileUtils.getFile("smalldata/iris/iris.csv").getAbsolutePath()));
      v2 = DKV.getGet(k2);
      assertEquals(v2.length(), v.length());
      assertVecEquals(v, v2, 0);
      // make sure we have some chunks
      v2.setChunkSize(chunkSize);
      long xor2 = new XORTask().doAll(v2)._res;
      assertEquals(xor2, xor);
      fr.delete();
      v2.remove();
    } finally {
      Scope.exit();
      if (k != null) Keyed.remove(k);
      if (k2 != null) Keyed.remove(k2);
      if (fr != null) fr.remove();
      if (v != null) v.remove();
      if (v2 != null) v2.remove();
      DKV.remove(credentialsKey);
    }
  }

  @Test
  public void testS3ImportFiles() throws Exception {
    PersistS3 persistS3 = new PersistS3();
    final ArrayList<String> keys = new ArrayList<>();
    final ArrayList<String> fails = new ArrayList<>();
    final ArrayList<String> deletions = new ArrayList<>();
    final ArrayList<String> files = new ArrayList<>();

    Key<?> credentialsKey = Key.make(IcedS3Credentials.S3_CREDENTIALS_DKV_KEY);

    try {
      // This test is only runnable in environment with Amazon credentials properly set {AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY}
      final String accessKeyId = System.getenv(AWS_ACCESS_KEY_PROPERTY_NAME);
      final String secretKey = System.getenv(AWS_SECRET_KEY_PROPERTY_NAME);

      assumeTrue(accessKeyId != null);
      assumeTrue(secretKey != null);

      final IcedS3Credentials s3Credentials = new IcedS3Credentials(accessKeyId, secretKey, null);
      DKV.put(credentialsKey, s3Credentials);

      persistS3.importFiles(IRIS_H2O_AWS, null, files, keys, fails, deletions);
      assertEquals(0, fails.size());
      assertEquals(0, deletions.size());
      assertEquals(1, files.size());
      assertEquals(1, keys.size());

      expectedException.expect(AmazonS3Exception.class);
      expectedException.expectMessage("The AWS Access Key Id you provided does not exist in our records. (Service: Amazon S3; Status Code: 403; Error Code: InvalidAccessKeyId; Request ID:");
      final String unexistingAmazonCredential = UUID.randomUUID().toString();
      final IcedS3Credentials unexistingCredentials = new IcedS3Credentials(unexistingAmazonCredential, unexistingAmazonCredential, null);
      DKV.put(credentialsKey, unexistingCredentials);
      deprecateBucketContentCaches();
      persistS3.importFiles(IRIS_H2O_AWS, null, files, keys, fails, deletions);
    } finally {
      DKV.remove(credentialsKey);
      for (String key : keys) {
        final Iced<?> iced = DKV.getGet(key);
        assertTrue(iced instanceof Frame);
        final Frame frame = (Frame) iced;
        frame.remove();
      }
    }
  }

  @Test
  public void testS3ImportFiles_noCredentialsSetExplicitely() {
    PersistS3 persistS3 = new PersistS3();
    final ArrayList<String> keys = new ArrayList<>();
    final ArrayList<String> fails = new ArrayList<>();
    final ArrayList<String> deletions = new ArrayList<>();
    final ArrayList<String> files = new ArrayList<>();

    Key<?> credentialsKey = Key.make(IcedS3Credentials.S3_CREDENTIALS_DKV_KEY);

    try {
      // This test is only runnable in environment with Amazon credentials properly set {AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY}
      final String accessKeyId = System.getenv(AWS_ACCESS_KEY_PROPERTY_NAME);
      final String secretKey = System.getenv(AWS_SECRET_KEY_PROPERTY_NAME);

      assumeTrue(accessKeyId != null);
      assumeTrue(secretKey != null);

      //Make sure there are no credentials set
      DKV.remove(credentialsKey);
      // Should not fail on this method invocation, should take the credentials from environment
      // Goal is to prove S3 basic credentials set to null in DKV under given key won't cause any exceptions to be thrown
      persistS3.importFiles(IRIS_H2O_AWS, null, files, keys, fails, deletions);

      assertEquals(0, fails.size());
      assertEquals(0, deletions.size());
      assertEquals(1, files.size());
      assertEquals(1, keys.size());
    } finally {
      DKV.remove(credentialsKey);
      for (String key : keys) {
        final Iced<?> iced = DKV.getGet(key);
        assertTrue(iced instanceof Frame);
        final Frame frame = (Frame) iced;
        frame.remove();
      }
    }
  }

  @Test
  public void testS3calcTypeaheadMatchesSingleFile() throws Exception {
    Key<?> credentialsKey = Key.make(IcedS3Credentials.S3_CREDENTIALS_DKV_KEY);
    try {
      final IcedS3Credentials s3Credentials = new IcedS3Credentials(getCredentials());
      DKV.put(credentialsKey, s3Credentials);

      PersistS3 persistS3 = new PersistS3();
      final List<String> strings = persistS3.calcTypeaheadMatches(IRIS_H2O_AWS, 10);

      assertNotNull(strings);
      assertEquals(1, strings.size()); // Only single file returned
      assertEquals(IRIS_H2O_AWS, strings.get(0));

      expectedException.expect(AmazonS3Exception.class);
      expectedException.expectMessage("The AWS Access Key Id you provided does not exist in our records. (Service: Amazon S3; Status Code: 403; Error Code: InvalidAccessKeyId; Request ID:");
      final String invalidAmazonCredential = UUID.randomUUID().toString();
      // Also tests cache erasure during client credentials change. The list of files in the bucket is still in the caches unless erased
      final IcedS3Credentials invalidCredentials = new IcedS3Credentials(invalidAmazonCredential, invalidAmazonCredential, null);
      DKV.put(credentialsKey, invalidCredentials);
      deprecateBucketContentCaches();
      persistS3.calcTypeaheadMatches(IRIS_H2O_AWS, 10);
      fail("should not reach here");
    } finally {
      DKV.remove(credentialsKey);
    }
  }

  @Test
  public void testS3calcTypeaheadMatchesBucketOnly() throws Exception {
    Key<?> credentialsKey = Key.make(IcedS3Credentials.S3_CREDENTIALS_DKV_KEY);
    try {
      final IcedS3Credentials s3Credentials = new IcedS3Credentials(getCredentials());
      DKV.put(credentialsKey, s3Credentials);

      PersistS3 persistS3 = new PersistS3();
      final List<String> strings = persistS3.calcTypeaheadMatches(IRIS_BUCKET_H2O_AWS, 10);
      assertNotNull(strings);
      assertEquals(3, strings.size());


      expectedException.expect(AmazonS3Exception.class);
      expectedException.expectMessage("The AWS Access Key Id you provided does not exist in our records. (Service: Amazon S3; Status Code: 403; Error Code: InvalidAccessKeyId; Request ID:");
      final String unexistingAmazonCredential = UUID.randomUUID().toString();
      final IcedS3Credentials unexistingCredentials = new IcedS3Credentials(unexistingAmazonCredential, unexistingAmazonCredential, null);
      DKV.put(credentialsKey, unexistingCredentials);
      deprecateBucketContentCaches();
      deprecatedBucketCache();
      // Also tests cache erasure during client credentials change. The list of files in the bucket is still in the caches unless erased
      persistS3.calcTypeaheadMatches(IRIS_BUCKET_H2O_AWS, 10);
      fail("should not reach here");
    } finally {
      DKV.remove(credentialsKey);
    }
  }

  @Test
  public void testS3ImportFolder() {
    PersistS3 persistS3 = new PersistS3();
    final ArrayList<String> keys = new ArrayList<>();
    final ArrayList<String> fails = new ArrayList<>();
    final ArrayList<String> deletions = new ArrayList<>();
    final ArrayList<String> files = new ArrayList<>();

    Key<?> credentialsKey = Key.make(IcedS3Credentials.S3_CREDENTIALS_DKV_KEY);

    try {
      // This test is only runnable in environment with Amazon credentials properly set {AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY}
      final String accessKeyId = System.getenv(AWS_ACCESS_KEY_PROPERTY_NAME);
      final String secretKey = System.getenv(AWS_SECRET_KEY_PROPERTY_NAME);

      assumeTrue(accessKeyId != null);
      assumeTrue(secretKey != null);

      final IcedS3Credentials s3Credentials = new IcedS3Credentials(accessKeyId, secretKey, null);
      DKV.put(credentialsKey, s3Credentials);

      persistS3.importFiles("s3://test.0xdata.com/h2o-unit-tests", null, files, keys, fails, deletions);
      assertEquals(0, fails.size());
      assertEquals(0, deletions.size());
      assertEquals(3, files.size()); // Parse including files in sub folders
      assertEquals(3, keys.size());

      for (final String k : keys) {
        final Value value = DKV.get(k);
        assertNotNull(value);
        assertTrue(value.isFrame());
        
        Frame f = value.get();
        assertTrue(f.numCols() > 0);
      }

    } finally {
      DKV.remove(credentialsKey);
      for (String key : keys) {
        final Iced<?> iced = DKV.getGet(key);
        assertTrue(iced instanceof Frame);
        final Frame frame = (Frame) iced;
        frame.remove();
      }
    }
  }


  @Test
  public void testS3Filter() {
    PersistS3 persistS3 = new PersistS3();
    final ArrayList<String> keys = new ArrayList<>();
    final ArrayList<String> fails = new ArrayList<>();
    final ArrayList<String> deletions = new ArrayList<>();
    final ArrayList<String> files = new ArrayList<>();

    Key<?> credentialsKey = Key.make(IcedS3Credentials.S3_CREDENTIALS_DKV_KEY);

    try {
      Scope.enter();
      // This test is only runnable in environment with Amazon credentials properly set {AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY}
      final String accessKeyId = System.getenv(AWS_ACCESS_KEY_PROPERTY_NAME);
      final String secretKey = System.getenv(AWS_SECRET_KEY_PROPERTY_NAME);

      assumeTrue(accessKeyId != null);
      assumeTrue(secretKey != null);

      final IcedS3Credentials s3Credentials = new IcedS3Credentials(accessKeyId, secretKey, null);
      DKV.put(credentialsKey, s3Credentials);

      persistS3.importFiles("s3://test.0xdata.com/h2o-unit-tests", ".*iris.*.csv", files, keys, fails, deletions);
      assertEquals(0, fails.size());
      assertEquals(0, deletions.size());
      assertEquals(2, files.size()); // Parse including files in sub folders
      assertEquals(2, keys.size());


      final Frame frame = ParseDataset.parse(Key.make(), DKV.get(keys.get(0))._key, DKV.get(keys.get(1))._key);
      Scope.track(frame);

      assertEquals(5, frame.numCols());
      assertEquals(300, frame.numRows()); // Two identical files with different names, each with 150 records
      

    } finally {
      Scope.exit();
      DKV.remove(credentialsKey);
    }
  }
  
  private static void deprecatedBucketCache() throws NoSuchFieldException, IllegalAccessException {
    Field timeoutMillis = null;
    Field lastUpdated = null;
    try {
      final Class<? extends PersistS3.Cache> bucketCacheClass = PersistS3._bucketCache.getClass();
      
      timeoutMillis = bucketCacheClass.getDeclaredField("_timeoutMillis");
      timeoutMillis.setAccessible(true);
      timeoutMillis.set(PersistS3._bucketCache, 1); // one millisecond timeout
      
      lastUpdated = bucketCacheClass.getDeclaredField("_lastUpdated");
      lastUpdated.setAccessible(true);
      lastUpdated.set(PersistS3._bucketCache, 1);
      
    } finally {
      if(timeoutMillis != null) timeoutMillis.setAccessible(false);
      if(lastUpdated != null) lastUpdated.setAccessible(false);
    }
  }

  private static void deprecateBucketContentCaches() throws NoSuchFieldException, IllegalAccessException {
    Field timeoutMillis = null;
    Field lastUpdated = null;
    try {
      final Class<PersistS3.Cache> cacheClass = PersistS3.Cache.class;

      timeoutMillis = cacheClass.getDeclaredField("_timeoutMillis");
      timeoutMillis.setAccessible(true);

      lastUpdated = cacheClass.getDeclaredField("_lastUpdated");
      lastUpdated.setAccessible(true);
      
      for (PersistS3.Cache cache: PersistS3._keyCaches.values()){
        timeoutMillis.set(cache, 1); // one millisecond timeout
        lastUpdated.set(cache, 0); // Updated at the beginning of EPOCH
      }

    } finally {
      if(timeoutMillis != null) timeoutMillis.setAccessible(false);
      if(lastUpdated != null) lastUpdated.setAccessible(false);
    }
  }

  @Test
  public void setS3SessionTokenVariablePropagationTest() {
    try {
      Scope.enter();
      final PersistS3CredentialsV3 persistS3CredentialsV3 = new PersistS3CredentialsV3();
      persistS3CredentialsV3.secret_key_id = "SECRET_KEY_ID";
      persistS3CredentialsV3.secret_access_key = "SECRET_ACCESS_KEY";
      persistS3CredentialsV3.session_token = "SESSION_TOKEN";


      final PersistS3Handler persistS3Handler = new PersistS3Handler();
      persistS3Handler.setS3Credentials(3, persistS3CredentialsV3);
      
      final Value credentialsValueFromDkv = DKV.get(IcedS3Credentials.S3_CREDENTIALS_DKV_KEY);
      assertNotNull(credentialsValueFromDkv);

      final IcedS3Credentials icedS3Credentials = credentialsValueFromDkv.get(IcedS3Credentials.class);
      assertNotNull(icedS3Credentials);

      assertEquals(persistS3CredentialsV3.secret_access_key, icedS3Credentials._secretAccessKey);
      assertEquals(persistS3CredentialsV3.secret_key_id, icedS3Credentials._accessKeyId);
      assertEquals(persistS3CredentialsV3.session_token, icedS3Credentials._sessionToken);
    } finally {
      DKV.remove(Key.make(IcedS3Credentials.S3_CREDENTIALS_DKV_KEY));
    }
  }

  @Test
  public void testCustomCredentialsProvider() {
    AWSCredentialsProvider[] defaultProviders =
            PersistS3.H2OAWSCredentialsProviderChain.constructProviderChain(H2O.ARGS, null);
    AWSCredentialsProvider[] extendedProviders = 
            PersistS3.H2OAWSCredentialsProviderChain.constructProviderChain(H2O.ARGS, CustomCredentialsProvider.class.getName());
    assertEquals(defaultProviders.length + 1, extendedProviders.length);
    assertTrue(extendedProviders[0] instanceof CustomCredentialsProvider);
  }

  @Test
  public void testCustomCredentialsProvider_ignoreInvalid() {
    AWSCredentialsProvider[] defaultProviders =
            PersistS3.H2OAWSCredentialsProviderChain.constructProviderChain(H2O.ARGS, null);
    AWSCredentialsProvider[] extendedProviders =
            PersistS3.H2OAWSCredentialsProviderChain.constructProviderChain(H2O.ARGS, "no.such.class");
    assertEquals(defaultProviders.length, extendedProviders.length);
  }

  @Test
  public void testImportParquetFromS3() {
    checkEnv();

    final ArrayList<String> keys = new ArrayList<>();
    final ArrayList<String> fails = new ArrayList<>();
    final ArrayList<String> deletions = new ArrayList<>();
    final ArrayList<String> files = new ArrayList<>();

    try {
      Scope.enter();
      H2O.getPM().importFiles("s3://h2o-public-test-data/smalldata/parser/parquet/airlines-simple.snappy.parquet", 
              null, files, keys, fails, deletions);

      assertEquals(0, fails.size());
      assertEquals(0, deletions.size());
      assertEquals(1, files.size()); // Parse including files in sub folders
      assertEquals(1, keys.size());

      Frame fromS3 = DKV.getGet(keys.get(0));
      Scope.track(fromS3);

      FileVec localVec = makeNfsFileVec("smalldata/parser/parquet/airlines-simple.snappy.parquet");
      assertNotNull(localVec);
      Scope.track(localVec);

      Vec s3Vec = fromS3.anyVec();
      assertNotNull(s3Vec);
      assertEquals(localVec.length(), s3Vec.length());
    } finally {
      Scope.exit();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testParseParquetFromS3() throws IOException {
    checkEnv();

    try {
      Scope.enter();
      URI parquetDatasetUri = URI.create("s3://h2o-public-test-data/smalldata/parser/parquet/airlines-simple.snappy.parquet");
      Key<?> parquetKey = H2O.getPM().anyURIToKey(parquetDatasetUri);
      Scope.track_generic(parquetKey.get());
      
      ParseSetup guessedSetup = ParseSetup.guessSetup(new Key[]{parquetKey}, false, ParseSetup.GUESS_HEADER);
      assertEquals("PARQUET", guessedSetup.getParseType().name());

      Frame fromS3 = ParseDataset.parse(Key.make(), new Key[]{parquetKey}, true, guessedSetup);
      Scope.track(fromS3);

      Frame fromLocal = parseTestFile("./smalldata/parser/parquet/airlines-simple.snappy.parquet");
      Scope.track(fromLocal);

      assertFrameEquals(fromLocal, fromS3, 0.0);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testGeneratePresignedURL() throws IOException {
    checkEnv();
    
    Date expiration = new Date(new Date().getTime() + 360_0000); // in 6 minutes
    URL url = new PersistS3().generatePresignedUrl("s3://test.0xdata.com/h2o-unit-tests/iris.csv", expiration);

    String httpsUrl = url.toString();
    assertEquals("https", httpsUrl.substring(0, 5).toLowerCase());

    final String httpUrl = "http" + httpsUrl.substring(5);
    final byte[] bytes;
    try (InputStream is = new URL(httpUrl).openStream()) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      IOUtils.copyLarge(is, baos);
      bytes = baos.toByteArray();
    }

    assertNotNull(bytes);
    assertEquals(4551, bytes.length);
  }

  @Test
  public void testRapidPresign() {
    checkEnv();

    Val val = Rapids.exec("(s3.generate.presigned.URL 's3://test.0xdata.com/h2o-unit-tests/iris.csv' 3600000)");
    assertTrue(val.isStr());

    assertTrue(val.getStr().toLowerCase().startsWith("https"));
  }

  @Test
  public void testCredentialsProviderContainsAwsDefault() {
    AWSCredentialsProvider[] providers = PersistS3.H2OAWSCredentialsProviderChain.constructProviderChain(H2O.ARGS);
    List<String> providerClasses = Arrays.stream(providers)
            .map(Object::getClass).map(Class::getName)
            .collect(Collectors.toList());
    assertEquals(Arrays.asList(
            "water.persist.PersistS3$H2ODynamicCredentialsProvider", 
            "water.persist.PersistS3$H2OArgCredentialsProvider", 
            "com.amazonaws.auth.DefaultAWSCredentialsProviderChain"), providerClasses);
  }

  @Test
  public void testConfigureS3UsingS3A() {
    AWSCredentials credentials = getCredentials();
    assertNotNull(credentials);
    Configuration conf = s3aConfiguration(credentials);

    H2O.OptArgs args = new H2O.OptArgs();
    args.configure_s3_using_s3a = true;

    AmazonS3 client = PersistS3.getClient("test.0xdata.com", args, conf);
    assertNotNull(client);
    assertTrue(PersistS3.getS3ClientFactory() instanceof S3AClientFactory);
  }

  @Test
  public void testConfigureS3UsingS3AAppliesCustomSecurityProviders() {
    // this test is about things you don't see: in `testConfigureS3UsingS3A` we need to populate Configuration
    // with fs.s3a.access.key/fs.s3a.secret.key, however, if we provide PersistHdfs.CONF it already has injected
    // the credentials providers
    H2O.OptArgs args = new H2O.OptArgs();
    args.configure_s3_using_s3a = true;

    assertNull(PersistHdfs.CONF.get("fs.s3a.access.key"));
    assertNull(PersistHdfs.CONF.get("fs.s3a.secret.key"));
    assertNull(PersistHdfs.CONF.get("hadoop.security.credential.provider.path"));
    assertFalse(PersistHdfs.CONF.get("fs.s3a.security.credential.provider.path").isEmpty());

    if (System.getenv(AWS_ACCESS_KEY_PROPERTY_NAME) == null) {
      // now derive a new configuration without any providers...
      Configuration noProviderConf = new Configuration(PersistHdfs.CONF);
      noProviderConf.set("fs.s3a.security.credential.provider.path", "");

      // and show it won't work without it (proves we are taking the credentials using the providers in the next test)
      try {
        PersistS3.getClient("test.0xdata.com", args, noProviderConf);
        fail();
      } catch (Exception e) {
        assertTrue(e.getMessage().contains("No AWS Credentials provided"));
      }
    }

    // test the default configuration
    AmazonS3 client = PersistS3.getClient("test.0xdata.com", args, PersistHdfs.CONF);
    assertNotNull(client);
    assertTrue(PersistS3.getS3ClientFactory() instanceof S3AClientFactory);
  }

  private static Configuration s3aConfiguration(AWSCredentials credentials) {
    Configuration conf = new Configuration();
    conf.set("fs.s3a.access.key", credentials.getAWSAccessKeyId());
    conf.set("fs.s3a.secret.key", credentials.getAWSSecretKey());
    return conf;
  }

  private static void checkEnv() {
    // This test is only runnable in environment with Amazon credentials properly set {AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY}
    Consumer<Object> checkImpl = isCI() ? Assert::assertNotNull : Assume::assumeNotNull; // assert in CI, assume elsewhere
    checkImpl.accept(System.getenv(AWS_ACCESS_KEY_PROPERTY_NAME));
    checkImpl.accept(System.getenv(AWS_SECRET_KEY_PROPERTY_NAME));
  }

  private AWSCredentials getCredentials() {
    return new PersistS3.H2OAWSCredentialsProviderChain().getCredentials();
  }

}
