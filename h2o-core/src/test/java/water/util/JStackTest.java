package water.util;

import org.junit.*;
import org.junit.runner.RunWith;
import water.H2O;
import water.runner.CloudSize;
import water.runner.H2ORunner;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class JStackTest {


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
