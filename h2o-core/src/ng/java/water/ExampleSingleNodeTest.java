package water;

import static org.testng.AssertJUnit.*;
import org.testng.annotations.*;

import java.io.File;
import water.fvec.Chunk;
import water.fvec.NFSFileVec;
import water.util.UnsafeUtils;

public class ExampleSingleNodeTest extends TestNGUtil {
  ExampleSingleNodeTest() { super(1); }

  @Test
  public void testCloudSize() {
    assertTrue(H2O.getCloudSize() >= 1);
  }

  @Test(groups={"NOPASS"})
  public void testSingleNodeNOPASSWontRun() {
    assertTrue(false);
  }
}
