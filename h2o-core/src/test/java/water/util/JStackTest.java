package water.util;

import org.junit.*;
import water.H2O;
import water.TestUtil;

public class JStackTest extends TestUtil {
  public JStackTest() { super(3); }

  @Test public void testJStack() {
    for( int i=0; i<10; i++ ) {
      JStackCollectorTask.DStackTrace traces[] = new water.util.JStackCollectorTask().doAllNodes()._traces;
      Assert.assertEquals(traces.length, H2O.CLOUD.size());
      for( JStackCollectorTask.DStackTrace trace : traces ) {
        Assert.assertTrue(trace != null);
      }
    }
  }
}
