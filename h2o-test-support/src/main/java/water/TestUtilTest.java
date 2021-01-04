package water;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertTrue;

/**
 *  We need to make sure that our tools for testing are reliable as well
 */
public class TestUtilTest extends TestUtil {

  @Rule
  public transient TemporaryFolder tmp = new TemporaryFolder();
  
  @BeforeClass public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void asFactor() {
    Scope.enter();
    try {
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA")
              .withVecTypes(Vec.T_STR)
              .withDataForCol(0, ar("yes", "no"))
              .build();
      Scope.track(fr);

      assertTrue(fr.vec(0).isString());

      Frame res = asFactor(fr, "ColA");

      assertTrue(res.vec(0).isCategorical());
      Scope.track(res);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void writeFrameToCSV() throws IOException {
    Assume.assumeTrue(H2O.getCloudSize() == 1);
    try {
      Scope.enter();
      Frame f = TestFrameCatalog.oneChunkFewRows();
      f.remove("col_3");
      File exportedFile = new File(tmp.getRoot(), "export.csv");
      writeFrameToCSV(exportedFile.getAbsolutePath(), f, true, false);
      Frame imported = Scope.track(parseTestFile(exportedFile.getAbsolutePath()));
      assertFrameEquals(f, imported, 0);
    } finally {
      Scope.exit();
    }
  }
}
