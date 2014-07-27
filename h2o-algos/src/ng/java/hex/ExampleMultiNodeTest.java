package hex;

import static org.testng.AssertJUnit.*;
import org.testng.annotations.*;

import java.io.File;
import water.fvec.Chunk;
import water.fvec.NFSFileVec;
import water.util.UnsafeUtils;
import water.H2O;
import water.TestNGUtil;

@Test(groups={"multi-node"})
public class ExampleMultiNodeTest extends TestNGUtil {
  ExampleMultiNodeTest() { super(5); }

  public void testCloudSize() {
    assertTrue(H2O.getCloudSize() >= 5);
  }

  @Test(groups={"NOPASS"})
  public void testMultiNodeNOPASSWontRun() {
    assertTrue(false);
  }
}
