package water.persist;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.s3a.S3AFileSystem;
import com.amazonaws.services.s3.model.S3Object;
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
    S3Object s3Object = fs.getAmazonS3ClientForTesting("testPubDev5663").getObject(bucket, key);
    
    assertNotNull(s3Object); // The object exists
    assert(fs.exists(p)); // But FS says it exists as well.
    assert(hdfsPersist.exists(existing)); // Our persist gives the same result
  }
}
