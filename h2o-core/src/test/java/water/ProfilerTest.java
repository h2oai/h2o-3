package water;

import static org.testng.AssertJUnit.*;
import org.testng.annotations.*;

import water.util.JProfile;

@Test(groups={"multi-node"})
public class ProfilerTest extends TestUtil {
  ProfilerTest() { super(3); }

  @Test public void testProfiler() {
    for( int i=0; i<10; i++ ) {
      JProfile jp = new JProfile(5);
      jp.execImpl();
      assertEquals(jp.nodes.length,H2O.CLOUD.size());
      for (int j=0; j<H2O.CLOUD.size(); ++j) {
        assertTrue(jp.nodes[j].profile != null);
      }
    }
  }
}
