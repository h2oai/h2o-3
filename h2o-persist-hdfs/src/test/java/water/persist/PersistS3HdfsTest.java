package water.persist;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import water.H2O;
import water.TestUtil;

import java.net.URI;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.Matchers.endsWith;


public class PersistS3HdfsTest extends TestUtil  {

  @BeforeClass
  public static void setup() { stall_till_cloudsize(1); }

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testPubDev5663() { // Demonstrates that S3FileSystem is broken
    thrown.expect(water.api.HDFSIOException.class);
    thrown.expectMessage(endsWith("java.io.IOException: /smalldata/airlines/AirlinesTrain.csv.zip doesn't exist")); // should not start with a slash!!!

    Persist hdfsPersist = H2O.getPM().getPersistForURI(URI.create("hdfs://localhost/"));

    String existing = "s3://h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv.zip";
    assertTrue(hdfsPersist.exists(existing));
  }

}
