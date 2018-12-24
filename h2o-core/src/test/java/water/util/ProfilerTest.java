package water.util;

import static org.junit.Assert.*;
import org.junit.*;

import org.junit.runner.RunWith;
import water.H2O;
import water.TestUtil;
import water.runner.CloudSize;
import water.runner.H2ORunner;

@RunWith(H2ORunner.class)
@CloudSize(3)
public class ProfilerTest {

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
