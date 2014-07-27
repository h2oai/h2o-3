package water;

import static org.testng.AssertJUnit.*;
import org.testng.annotations.*;

import java.io.File;
import water.fvec.Chunk;
import water.fvec.NFSFileVec;
import water.util.UnsafeUtils;

@Test(groups={"multi-node"})
public class ExampleMultiNodeTest extends TestNGUtil {
  ExampleMultiNodeTest() { super(2); }

  public void testCloudSize() {
    assertTrue(H2O.getCloudSize() >= 2);
  }

  @Test(groups={"NOPASS"})
  public void testMultiNodeNOPASSWontRun() {
    assertTrue(false);
  }
}
