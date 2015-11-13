package water.util;

import static org.junit.Assert.*;
import org.junit.*;

import water.H2O;
import water.TestUtil;

public class ProfilerTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(3); }
  public ProfilerTest() { super(3); }

  @Test public void testProfiler() {
    for( int i=0; i<10; i++ ) {
      JProfile jp = new JProfile(5);
      jp.execImpl(false);
      assertEquals(jp.nodes.length, H2O.CLOUD.size());
      for (int j=0; j<H2O.CLOUD.size(); ++j) {
        assertTrue(jp.nodes[j].profile != null);
      }
    }
  }
}
