package water.persist;

import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.FileVec;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.FileUtils;

import java.net.URI;

import static junit.framework.TestCase.assertEquals;

public class PersistHdfsTest extends TestUtil {

  @BeforeClass
  public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void testS3NImport() throws Exception {
    testImport("s3n");
  }

  @Test
  public void testS3AImport() throws Exception {
    testImport("s3a");
  }

  private void testImport(String scheme) throws Exception {
    Scope.enter();
    try {
      Key keyS3 = H2O.getPM().anyURIToKey(URI.create(scheme + "://h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv.zip"));
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

}
