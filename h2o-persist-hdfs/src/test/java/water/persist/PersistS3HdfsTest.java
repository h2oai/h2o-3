package water.persist;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.s3.S3FileSystem;
import org.apache.hadoop.security.alias.CredentialProviderFactory;
import org.jets3t.service.S3Service;
import org.jets3t.service.model.S3Object;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import water.*;
import water.fvec.Frame;
import water.util.Log;
import water.util.ReflectionUtils;

import java.net.URI;
import java.util.ArrayList;

import static junit.framework.TestCase.*;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;


public class PersistS3HdfsTest extends TestUtil  {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private static final String IRIS_H2O_AWS = "s3a://test.0xdata.com/h2o-unit-tests/iris.csv";
  private static final String AWS_ACCESS_KEY_PROPERTY_NAME = "AWS_ACCESS_KEY_ID";
  private static final String AWS_SECRET_KEY_PROPERTY_NAME = "AWS_SECRET_ACCESS_KEY";

  @Test
  public void testPubDev5663() throws Exception { // Demonstrates that S3FileSystem is broken
    final String bucket = "h2o-public-test-data";
    final String key = "smalldata/airlines/AirlinesTrain.csv.zip";

    PersistHdfs hdfsPersist = (PersistHdfs) H2O.getPM().getPersistForURI(URI.create("hdfs://localhost/"));

    String existing = "s3://" + bucket + "/" + key;
    Path p = new Path(existing);

    S3FileSystem fs = (S3FileSystem) FileSystem.get(p.toUri(), PersistHdfs.CONF);
    // use crazy reflection to get to the actual S3 Service instance
    S3Service s3Service = (S3Service) getValue(fs, "store", "h", "proxyDescriptor", "fpp", "proxy", "s3Service");

    S3Object s3Object = s3Service.getObject(bucket, key);
    assertNotNull(s3Object); // The object exists
    assertFalse(fs.exists(p)); // But FS says it doesn't => S3 is broken in Hadoop
    assertFalse(hdfsPersist.exists(existing)); // Our persist gives the same result
  }

  private Object getValue(Object o, String... fieldNames) {
    StringBuilder path = new StringBuilder(o.getClass().getName());
    for (String f : fieldNames) {
      path.append('.').append(f);
      Object no = ReflectionUtils.getFieldValue(o, f);
      if (no == null)
        throw new IllegalStateException("Invalid path: " + path.toString() + ", object is instance of " + o.getClass());
      o = no;
    }
    return o;
  }

  @Test
  public void testS3CredentialsChange() {
      final ArrayList<String> keys = new ArrayList<>();
      final ArrayList<String> fails = new ArrayList<>();
      final ArrayList<String> deletions = new ArrayList<>();
      final ArrayList<String> files = new ArrayList<>();
    try {
      Scope.enter();

      final String accessKeyId = System.getenv(AWS_ACCESS_KEY_PROPERTY_NAME);
      final String secretKey = System.getenv(AWS_SECRET_KEY_PROPERTY_NAME);

      assumeTrue(accessKeyId != null);
      assumeTrue(secretKey != null);
      
      PersistHdfs persistHdfs = (PersistHdfs) H2O.getPM().getPersistForURI(URI.create("hdfs://localhost/"));
      final PersistS3AHandler persistS3AHandler = new PersistS3AHandler();
      final PersistS3ACredentialsV3 credentialsV3 = createS3ACredentialsV3(accessKeyId,
              secretKey);

      Value value = DKV.get(IcedS3ACredentials.S3A_CREDENTIALS_DKV_KEY);
      assertNull(value);
      
      final PersistS3ACredentialsV3 credentialsV31 = persistS3AHandler.setS3ACredentials(3, credentialsV3);
      value = DKV.get(IcedS3ACredentials.S3A_CREDENTIALS_DKV_KEY);
      assertNotNull(value);

      persistHdfs.importFiles(IRIS_H2O_AWS, null, files, keys, fails, deletions);
      
      assertEquals(1, keys.size());
      
    } finally {
      Scope.exit();
      DKV.remove(Key.make(IcedS3ACredentials.S3A_CREDENTIALS_DKV_KEY));
      for (String key : keys) {
        final Iced iced = DKV.getGet(key);
        assertTrue(iced instanceof Frame);
        final Frame frame = (Frame) iced;
        frame.remove();
      }
    }


  }

  private static PersistS3ACredentialsV3 createS3ACredentialsV3(final String accessKeyId,
                                                                final String secretAccessKey) {
    final PersistS3ACredentialsV3 persistS3ACredentialsV3 = new PersistS3ACredentialsV3();
    persistS3ACredentialsV3.access_key_id = accessKeyId;
    persistS3ACredentialsV3.secret_access_key = secretAccessKey;

    return persistS3ACredentialsV3;
  }

}
