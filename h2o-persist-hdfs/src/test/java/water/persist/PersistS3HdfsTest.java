package water.persist;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.s3a.S3AFileSystem;
import org.jets3t.service.S3Service;
import org.jets3t.service.model.S3Object;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import water.H2O;
import water.TestUtil;
import water.util.ReflectionUtils;

import java.net.URI;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;


public class PersistS3HdfsTest extends TestUtil  {

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

    String existing = "s3a://" + bucket + "/" + key;
    Path p = new Path(existing);

    S3AFileSystem fs = (S3AFileSystem) FileSystem.get(p.toUri(), PersistHdfs.CONF);
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

}
