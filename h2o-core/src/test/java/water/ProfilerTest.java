package water;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.util.JProfile;

public class ProfilerTest extends TestUtil {

  @BeforeClass public static void stall() { stall_till_cloudsize(3); }

  @Test public void testProfiler() {
    for( int i=0; i<10; i++ ) {
      JProfile jp = new JProfile(5);
      jp.execImpl();
      Assert.assertEquals(jp.nodes.length,H2O.CLOUD.size());
      for (int j=0; j<H2O.CLOUD.size(); ++j) {
        Assert.assertTrue(jp.nodes[j].profile != null);
      }
    }
  }
}
