package water;

import org.junit.*;
import water.util.JStackCollectorTask;

public class JStackTest extends TestUtil {
  public JStackTest() { super(3); }

  @Test public void testJStack() {
    for( int i=0; i<10; i++ ) {
      JStackCollectorTask.DStackTrace traces[] = new water.util.JStackCollectorTask().doAllNodes()._traces;
      Assert.assertEquals(traces.length,H2O.CLOUD.size());
      for (int j=0; j<traces.length; ++j) {
        Assert.assertTrue(traces[j] != null);
      }
    }
  }
}
