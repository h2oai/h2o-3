package water.persist;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import water.*;
import water.fvec.Chunk;
import water.fvec.FileVec;
import water.fvec.Frame;
import water.parser.ParseDataset;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.FileUtils;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
      Key k = H2O.getPM().anyURIToKey(new URI("s3://h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv.zip"));
      Frame fr = DKV.getGet(k);
      FileVec v = (FileVec) fr.anyVec();
      // make sure we have some chunks
      int chunkSize = (int) (v.length() / 3);
      v.setChunkSize(fr, chunkSize);
      long xor = new XORTask().doAll(v)._res;
      Key k2 = H2O.getPM().anyURIToKey(new URI(FileUtils.getFile("smalldata/airlines/AirlinesTrain.csv.zip").getAbsolutePath()));
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
    assertTrue(new PersistS3().exists("s3://h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv.zip"));
    assertFalse(new PersistS3().exists("s3://h2o-public-test-data/smalldata/airlines/invalid.file"));
  }

  @Test
  public void testS3UriToKeyChangedCredentials() throws Exception {
    Scope.enter();
    Key k = null, k2 = null;
    Frame fr = null;
    FileVec v = null, v2 = null;
    Key credentialsKey = Key.make(IcedS3Credentials.S3_CREDENTIALS_DKV_KEY);
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
      if (k != null) k.remove();
      if (k2 != null) k2.remove();
      if (fr != null) fr.remove();
      if (v != null) v.remove();
      if (v2 != null) v2.remove();
      if (credentialsKey != null) DKV.remove(credentialsKey);
    }
  }

  @Test
  public void testS3ImportFiles() throws Exception {
    PersistS3 persistS3 = new PersistS3();
    final ArrayList<String> keys = new ArrayList<>();
    final ArrayList<String> fails = new ArrayList<>();
    final ArrayList<String> deletions = new ArrayList<>();
    final ArrayList<String> files = new ArrayList<>();

    Key credentialsKey = Key.make(IcedS3Credentials.S3_CREDENTIALS_DKV_KEY);

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
      deprecateBucketContentCaches(persistS3);
      persistS3.importFiles(IRIS_H2O_AWS, null, files, keys, fails, deletions);
    } finally {
      if (credentialsKey != null) DKV.remove(credentialsKey);
      for (String key : keys) {
        final Iced iced = DKV.getGet(key);
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

    Key credentialsKey = Key.make(IcedS3Credentials.S3_CREDENTIALS_DKV_KEY);

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
      if (credentialsKey != null) DKV.remove(credentialsKey);
      for (String key : keys) {
        final Iced iced = DKV.getGet(key);
        assertTrue(iced instanceof Frame);
        final Frame frame = (Frame) iced;
        frame.remove();
      }
    }
  }

  @Test
  public void testS3calcTypeaheadMatchesSingleFile() throws Exception {

    Key credentialsKey = Key.make(IcedS3Credentials.S3_CREDENTIALS_DKV_KEY);
    try {
      // This test is only runnable in environment with Amazon credentials properly set {AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY}
      final String accessKey = System.getenv(AWS_ACCESS_KEY_PROPERTY_NAME);
      final String secretKey = System.getenv(AWS_SECRET_KEY_PROPERTY_NAME);


      assumeTrue(accessKey != null);
      assumeTrue(secretKey != null);

      final AmazonS3 defaultClient = PersistS3.getClient(); // Create a default client
      assertNotNull(defaultClient);

      final IcedS3Credentials s3Credentials = new IcedS3Credentials(accessKey, secretKey, null);
      DKV.put(credentialsKey, s3Credentials);

      PersistS3 persistS3 = new PersistS3();
      final List<String> strings = persistS3.calcTypeaheadMatches(IRIS_H2O_AWS, 10);

      assertNotNull(strings);
      assertEquals(1, strings.size()); // Only single file returned
      assertEquals(IRIS_H2O_AWS, strings.get(0));


      expectedException.expect(AmazonS3Exception.class);
      expectedException.expectMessage("The AWS Access Key Id you provided does not exist in our records. (Service: Amazon S3; Status Code: 403; Error Code: InvalidAccessKeyId; Request ID:");
      final String unexistingAmazonCredential = UUID.randomUUID().toString();
      // Also tests cache erasure during client credentials chage. The list of files in the bucket is still in the caches unless erased
      final IcedS3Credentials unexistingCredentials = new IcedS3Credentials(unexistingAmazonCredential, unexistingAmazonCredential, null);
      DKV.put(credentialsKey, unexistingCredentials);
      deprecateBucketContentCaches(persistS3);
      final List<String> failed = persistS3.calcTypeaheadMatches(IRIS_H2O_AWS, 10);
    } finally {
      if (credentialsKey != null) DKV.remove(credentialsKey);
    }

  }

  @Test
  public void testS3calcTypeaheadMatchesBucketOnly() throws Exception {

    Key credentialsKey = Key.make(IcedS3Credentials.S3_CREDENTIALS_DKV_KEY);

    try {
      // This test is only runnable in environment with Amazon credentials properly set {AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY}
      final String accessKey = System.getenv(AWS_ACCESS_KEY_PROPERTY_NAME);
      final String secretKey = System.getenv(AWS_SECRET_KEY_PROPERTY_NAME);

      assumeTrue(accessKey != null);
      assumeTrue(secretKey != null);

      final IcedS3Credentials s3Credentials = new IcedS3Credentials(accessKey, secretKey, null);
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
      deprecateBucketContentCaches(persistS3);
      deprecatedBucketCache(persistS3);
      // Also tests cache erasure during client credentials chage. The list of files in the bucket is still in the caches unless erased
      final List<String> failed = persistS3.calcTypeaheadMatches(IRIS_BUCKET_H2O_AWS, 10);
    } finally {

      if (credentialsKey != null) DKV.remove(credentialsKey);
    }
  }

  @Test
  public void testS3ImportFolder() {
    PersistS3 persistS3 = new PersistS3();
    final ArrayList<String> keys = new ArrayList<>();
    final ArrayList<String> fails = new ArrayList<>();
    final ArrayList<String> deletions = new ArrayList<>();
    final ArrayList<String> files = new ArrayList<>();

    Key credentialsKey = Key.make(IcedS3Credentials.S3_CREDENTIALS_DKV_KEY);

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
      if (credentialsKey != null) DKV.remove(credentialsKey);
      for (String key : keys) {
        final Iced iced = DKV.getGet(key);
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

    Key credentialsKey = Key.make(IcedS3Credentials.S3_CREDENTIALS_DKV_KEY);

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
      if (credentialsKey != null) DKV.remove(credentialsKey);
    }
  }
  
  private static final void deprecatedBucketCache(final PersistS3 persistS3) throws NoSuchFieldException, IllegalAccessException {
    Field timeoutMillis = null;
    Field lastUpdated = null;
    try {
      final Class<? extends PersistS3.Cache> bucketCacheClass = persistS3._bucketCache.getClass();
      
      timeoutMillis = bucketCacheClass.getDeclaredField("_timeoutMillis");
      timeoutMillis.setAccessible(true);
      timeoutMillis.set(persistS3._bucketCache, 1); // one millisecond timeout
      
      lastUpdated = bucketCacheClass.getDeclaredField("_lastUpdated");
      lastUpdated.setAccessible(true);
      lastUpdated.set(persistS3._bucketCache, 1);
      
    } finally {
      if(timeoutMillis != null) timeoutMillis.setAccessible(false);
      if(lastUpdated != null) lastUpdated.setAccessible(false);
    }
  }

  private static final void deprecateBucketContentCaches(final PersistS3 persistS3) throws NoSuchFieldException, IllegalAccessException {
    Field timeoutMillis = null;
    Field lastUpdated = null;
    try {
      final Class<PersistS3.Cache> cacheClass = PersistS3.Cache.class;

      timeoutMillis = cacheClass.getDeclaredField("_timeoutMillis");
      timeoutMillis.setAccessible(true);

      lastUpdated = cacheClass.getDeclaredField("_lastUpdated");
      lastUpdated.setAccessible(true);
      
      for (PersistS3.Cache cache: persistS3._keyCaches.values()){
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
      assertEquals(persistS3CredentialsV3.secret_key_id, icedS3Credentials._secretKeyId);
      assertEquals(persistS3CredentialsV3.session_token, icedS3Credentials._sessionToken);
    } finally {
      DKV.remove(Key.make(IcedS3Credentials.S3_CREDENTIALS_DKV_KEY));
    }
  }
}
