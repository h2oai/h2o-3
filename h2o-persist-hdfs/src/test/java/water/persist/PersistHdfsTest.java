package water.persist;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.*;
import water.fvec.FileVec;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.FileUtils;

import java.net.URI;

import static junit.framework.TestCase.*;

@RunWith(Parameterized.class)
public class PersistHdfsTest extends TestUtil {

  @Parameterized.Parameters(name = "{index}: scheme={0}")
  public static Object[] schemes() {
    return new Object[] { "s3n", "s3a" };
  }

  @Parameterized.Parameter
  public String scheme;

  @BeforeClass
  public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void testImport() throws Exception {
    Scope.enter();
    try {
      Persist hdfsPersist = H2O.getPM().getPersistForURI(URI.create("hdfs://localhost/"));
      Key keyS3 = hdfsPersist.uriToKey(URI.create(scheme + "://h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv.zip"));
      Frame fr = Scope.track((Frame) DKV.getGet(keyS3));
      FileVec vS3 = (FileVec) fr.anyVec();

      Key keyLocal = H2O.getPM().anyURIToKey(new URI(FileUtils.getFile("smalldata/airlines/AirlinesTrain.csv.zip").getAbsolutePath()));
      FileVec vLocal = (FileVec) Scope.track((Vec) DKV.getGet(keyLocal));

      assertEquals(vLocal.length(), vS3.length());
      assertVecEquals(vLocal, vS3, 0);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testExist() {
    Persist hdfsPersist = H2O.getPM().getPersistForURI(URI.create("hdfs://localhost/"));

    String existing = scheme + "://h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv.zip";
    assertTrue(hdfsPersist.exists(existing));

    String invalid = scheme + "://h2o-public-test-data/smalldata/does.not.exist";
    assertFalse(hdfsPersist.exists(invalid));
  }

  @Test
  public void testGetParent() {
    Persist hdfsPersist = H2O.getPM().getPersistForURI(URI.create("hdfs://localhost/"));

    String directory = "hdfs://h2o-public-test-data/smalldata/airlines";
    assertEquals("hdfs://h2o-public-test-data/smalldata", hdfsPersist.getParent(directory));
  }

}
