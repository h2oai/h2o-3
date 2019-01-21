package water.persist;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import water.*;
import water.fvec.Chunk;
import water.fvec.FileVec;
import water.fvec.Frame;
import water.util.FileUtils;

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
public class PersistS3Test extends TestUtil {

  private static final String AWS_ACCESS_KEY_PROPERTY_NAME = "AWS_ACCESS_KEY_ID";
  private static final String AWS_SECRET_KEY_PROPERTY_NAME = "AWS_SECRET_ACCESS_KEY";
  private static final String IRIS_H2O_AWS = "s3://test.0xdata.com/h2o-unit-tests/iris.csv";
  private static final String IRIS_BUCKET_H2O_AWS = "s3://test.0xdata.com/h2o-unit-tests/";


  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @After
  public void tearDown() throws Exception {
    // Make sure after each test method, wrong client with invalid credentials - required to avoid false negatives
    final String invalidCredential = UUID.randomUUID().toString();
    PersistS3.changeClientCredentials(invalidCredential, invalidCredential);
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
  public void testS3UriToKeyChangedCredentials() throws Exception {
    // This test is only runnable in environment with Amazon credentials properly set {AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY}
    final String accessKey = System.getenv(AWS_ACCESS_KEY_PROPERTY_NAME);
    final String secretKey = System.getenv(AWS_SECRET_KEY_PROPERTY_NAME);

    assumeTrue(accessKey != null);
    assumeTrue(secretKey != null);

    final AmazonS3 defaultClient = PersistS3.getClient(); // Create a default client
    assertNotNull(defaultClient);

    PersistS3.changeClientCredentials(accessKey, secretKey);
    final AmazonS3 staticCredentialsClient = PersistS3.getClient();
    assertNotEquals(defaultClient, staticCredentialsClient);


    Scope.enter();
    Key k = null, k2 = null;
    Frame fr = null;
    FileVec v = null, v2 = null;
    try {
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
    }

    expectedException.expect(AmazonS3Exception.class);
    expectedException.expectMessage("Forbidden (Service: Amazon S3; Status Code: 403; Error Code: 403 Forbidden; Request ID:");
    final String unexistingAmazonCredential = UUID.randomUUID().toString();
    PersistS3.changeClientCredentials(unexistingAmazonCredential, unexistingAmazonCredential);
    H2O.getPM().anyURIToKey(new URI(IRIS_H2O_AWS));
  }

  @Test
  public void testS3ImportUriCredentials() throws Exception {
    // This test is only runnable in environment with Amazon credentials properly set {AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY}
    final String accessKey = System.getenv(AWS_ACCESS_KEY_PROPERTY_NAME);
    final String secretKey = System.getenv(AWS_SECRET_KEY_PROPERTY_NAME);

    assumeTrue(accessKey != null);
    assumeTrue(secretKey != null);

    final AmazonS3 defaultClient = PersistS3.getClient(); // Create a default client
    assertNotNull(defaultClient);

    PersistS3.changeClientCredentials(accessKey, secretKey);
    final AmazonS3 staticCredentialsClient = PersistS3.getClient();
    assertNotEquals(defaultClient, staticCredentialsClient);


    PersistS3 persistS3 = new PersistS3();
    final ArrayList<String> keys = new ArrayList<>();
    final ArrayList<String> fails = new ArrayList<>();
    final ArrayList<String> deletions = new ArrayList<>();
    final ArrayList<String> files = new ArrayList<>();

    try {
      persistS3.importFiles(IRIS_H2O_AWS, null, files, keys, fails, deletions);
      assertEquals(0, fails.size());
      assertEquals(0, deletions.size());
      assertEquals(1, files.size());
      assertEquals(1, keys.size());
    } finally {
      for (String key : keys) {
        final Iced iced = DKV.getGet(key);
        assertTrue(iced instanceof Frame);
        final Frame frame = (Frame) iced;
        frame.remove();
      }
    }

    expectedException.expect(AmazonS3Exception.class);
    expectedException.expectMessage("The AWS Access Key Id you provided does not exist in our records. (Service: Amazon S3; Status Code: 403; Error Code: InvalidAccessKeyId; Request ID:");
    final String unexistingAmazonCredential = UUID.randomUUID().toString();
    PersistS3.changeClientCredentials(unexistingAmazonCredential, unexistingAmazonCredential);
    persistS3.importFiles(IRIS_H2O_AWS, null, files, keys, fails, deletions);

  }

  @Test
  public void testS3calcTypeaheadMatchesSingleFile() throws Exception {
    // This test is only runnable in environment with Amazon credentials properly set {AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY}
    final String accessKey = System.getenv(AWS_ACCESS_KEY_PROPERTY_NAME);
    final String secretKey = System.getenv(AWS_SECRET_KEY_PROPERTY_NAME);


    assumeTrue(accessKey != null);
    assumeTrue(secretKey != null);

    final AmazonS3 defaultClient = PersistS3.getClient(); // Create a default client
    assertNotNull(defaultClient);

    PersistS3.changeClientCredentials(accessKey, secretKey);
    final AmazonS3 staticCredentialsClient = PersistS3.getClient();
    assertNotEquals(defaultClient, staticCredentialsClient);
    
    
    PersistS3 persistS3 = new PersistS3();
    final List<String> strings = persistS3.calcTypeaheadMatches(IRIS_H2O_AWS, 10);

    assertNotNull(strings);
    assertEquals(1, strings.size()); // Only single file returned
    assertEquals(IRIS_H2O_AWS, strings.get(0));


    expectedException.expect(AmazonS3Exception.class);
    expectedException.expectMessage("The AWS Access Key Id you provided does not exist in our records. (Service: Amazon S3; Status Code: 403; Error Code: InvalidAccessKeyId; Request ID:");
    final String unexistingAmazonCredential = UUID.randomUUID().toString();
    // Also tests cache erasure during client credentials chage. The list of files in the bucket is still in the caches unless erased
    PersistS3.changeClientCredentials(unexistingAmazonCredential, unexistingAmazonCredential);
    final List<String> failed = persistS3.calcTypeaheadMatches(IRIS_H2O_AWS, 10);

  }

  @Test
  public void testS3calcTypeaheadMatchesBucketOnly() {
    // This test is only runnable in environment with Amazon credentials properly set {AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY}
    final String accessKey = System.getenv(AWS_ACCESS_KEY_PROPERTY_NAME);
    final String secretKey = System.getenv(AWS_SECRET_KEY_PROPERTY_NAME);

    assumeTrue(accessKey != null);
    assumeTrue(secretKey != null);


    final AmazonS3 defaultClient = PersistS3.getClient(); // Create a default client
    assertNotNull(defaultClient);

    PersistS3.changeClientCredentials(accessKey, secretKey);
    final AmazonS3 staticCredentialsClient = PersistS3.getClient();
    assertNotEquals(defaultClient, staticCredentialsClient);


    PersistS3 persistS3 = new PersistS3();
    final List<String> strings = persistS3.calcTypeaheadMatches(IRIS_BUCKET_H2O_AWS, 10);
    assertNotNull(strings);
    assertEquals(3, strings.size());

    
    expectedException.expect(AmazonS3Exception.class);
    expectedException.expectMessage("The AWS Access Key Id you provided does not exist in our records. (Service: Amazon S3; Status Code: 403; Error Code: InvalidAccessKeyId; Request ID:");
    final String unexistingAmazonCredential = UUID.randomUUID().toString();
    // Also tests cache erasure during client credentials chage. The list of files in the bucket is still in the caches unless erased
    PersistS3.changeClientCredentials(unexistingAmazonCredential, unexistingAmazonCredential);
    final List<String> failed = persistS3.calcTypeaheadMatches(IRIS_BUCKET_H2O_AWS, 10);
  }
  
}
