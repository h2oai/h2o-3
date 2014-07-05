package water;

import org.testng.AssertJUnit;
import org.testng.annotations.*;

@Test(groups={"multi-node"})
public class JStackTest extends TestUtil {

  @BeforeClass public static void stall() { stall_till_cloudsize(3); }

  @Test public void testJStack() {
    for( int i=0; i<10; i++ ) {
      String traces[] = new water.util.JStackCollectorTask().doAllNodes()._traces;
      AssertJUnit.assertEquals(traces.length,H2O.CLOUD.size());
      for (int j=0; j<traces.length; ++j) {
        AssertJUnit.assertTrue(traces[j] != null);
      }
    }
  }
}
