package water.persist;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.s3.S3FileSystem;
import org.jets3t.service.S3Service;
import org.jets3t.service.model.S3Object;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import water.*;
import water.fvec.Chunk;
import water.fvec.FileVec;
import water.fvec.Frame;
import water.util.FileUtils;
import water.util.ReflectionUtils;

import java.net.URI;
import java.util.ArrayList;

import static junit.framework.TestCase.*;
import static junit.framework.TestCase.assertEquals;
import static org.junit.Assume.assumeTrue;


public class PersistS3HdfsTest extends TestUtil  {

  private static final String AWS_ACCESS_KEY_PROPERTY_NAME = "AWS_ACCESS_KEY_ID";
  private static final String AWS_SECRET_KEY_PROPERTY_NAME = "AWS_SECRET_ACCESS_KEY";

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Rule
  public ExpectedException thrown = ExpectedException.none();

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

  @Test
  public void testS3AImportAccessProtected() throws Exception {
    // This test is only runnable in environment with Amazon credentials properly set {AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY}
    final String accessKey = System.getenv(AWS_ACCESS_KEY_PROPERTY_NAME);
    final String secretKey = System.getenv(AWS_SECRET_KEY_PROPERTY_NAME);

    assumeTrue(accessKey != null);
    assumeTrue(secretKey != null);

    StringBuilder s3UrlBuilder = new StringBuilder("s3a://");
    s3UrlBuilder.append(accessKey);
    s3UrlBuilder.append(":");
    s3UrlBuilder.append(secretKey);
    s3UrlBuilder.append("@test.0xdata.com/h2o-unit-tests/iris.csv");

    Scope.enter();
    Key k = null, k2 = null;
    Frame fr = null;
    FileVec v = null, v2 = null;
    try {
      k = H2O.getPM().anyURIToKey(new URI(s3UrlBuilder.toString()));
      fr = DKV.getGet(k);
      assertNotNull(fr);
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
  }

  @Test
  public void testS3CalcTypeaheadMatches() throws Exception {
    // This test is only runnable in environment with Amazon credentials properly set {AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY}
    final String accessKey = System.getenv(AWS_ACCESS_KEY_PROPERTY_NAME);
    final String secretKey = System.getenv(AWS_SECRET_KEY_PROPERTY_NAME);

    assumeTrue(accessKey != null);
    assumeTrue(secretKey != null);

    StringBuilder s3UrlBuilder = new StringBuilder("s3a://");
    s3UrlBuilder.append(accessKey);
    s3UrlBuilder.append(":");
    s3UrlBuilder.append(secretKey);
    s3UrlBuilder.append("@test.0xdata.com/h2o-unit-tests/iris.csv");

    final PersistHdfs persistHdfs = new PersistHdfs();
    final ArrayList<String> strings = persistHdfs.calcTypeaheadMatches(s3UrlBuilder.toString(), 10);
    assertNotNull(strings);
    assertEquals(1, strings.size());
  }

  @Test
  public void testImportFiles() throws Exception {
    // This test is only runnable in environment with Amazon credentials properly set {AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY}
    final String accessKey = System.getenv(AWS_ACCESS_KEY_PROPERTY_NAME);
    final String secretKey = System.getenv(AWS_SECRET_KEY_PROPERTY_NAME);

    assumeTrue(accessKey != null);
    assumeTrue(secretKey != null);

    StringBuilder s3UrlBuilder = new StringBuilder("s3a://");
    s3UrlBuilder.append(accessKey);
    s3UrlBuilder.append(":");
    s3UrlBuilder.append(secretKey);
    s3UrlBuilder.append("@test.0xdata.com/h2o-unit-tests/iris.csv");
    
    final PersistHdfs persistHdfs = new PersistHdfs();
    final ArrayList<String> keys = new ArrayList<>();
    final ArrayList<String> fails = new ArrayList<>();
    final ArrayList<String> deletions = new ArrayList<>();
    final ArrayList<String> files = new ArrayList<>();
    
    try {
      persistHdfs.importFiles(s3UrlBuilder.toString(), null, files, keys, fails, deletions);
      assertEquals(0, fails.size());
      assertEquals(0, deletions.size());
      assertEquals(1, files.size());
      assertEquals(1, keys.size());
    } finally {
      for (String key : keys){
        final Iced iced = DKV.getGet(key);
        assertTrue(iced instanceof Frame);
        final Frame frame = (Frame) iced;
        frame.remove();
      }
    }
  }

  @Test
  public void testImportFilesFromBucketFolder() throws Exception {
    // This test is only runnable in environment with Amazon credentials properly set {AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY}
    final String accessKey = System.getenv(AWS_ACCESS_KEY_PROPERTY_NAME);
    final String secretKey = System.getenv(AWS_SECRET_KEY_PROPERTY_NAME);

    assumeTrue(accessKey != null);
    assumeTrue(secretKey != null);

    StringBuilder s3UrlBuilder = new StringBuilder("s3a://");
    s3UrlBuilder.append(accessKey);
    s3UrlBuilder.append(":");
    s3UrlBuilder.append(secretKey);
    s3UrlBuilder.append("@test.0xdata.com/h2o-unit-tests/");

    final PersistHdfs persistHdfs = new PersistHdfs();
    final ArrayList<String> keys = new ArrayList<>();
    final ArrayList<String> fails = new ArrayList<>();
    final ArrayList<String> deletions = new ArrayList<>();
    final ArrayList<String> files = new ArrayList<>();

    try {
      persistHdfs.importFiles(s3UrlBuilder.toString(), null, files, keys, fails, deletions);
      assertEquals(0, fails.size());
      assertEquals(0, deletions.size());
      assertEquals(2, files.size());
      assertEquals(2, keys.size());
    } finally {
      for (String key : keys){
        final Iced iced = DKV.getGet(key);
        assertTrue(iced instanceof Frame);
        final Frame frame = (Frame) iced;
        frame.remove();
      }
    }
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

private static class XORTask extends MRTask<XORTask> {
  long _res = 0;

  @Override
  public void map(Chunk c) {
    for (int i = 0; i < c._len; ++i)
      _res ^= c.at8(i);
  }

  @Override
  public void reduce(XORTask xort) {
    _res = _res ^ xort._res;
  }
}

}
