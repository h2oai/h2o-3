package water;

import org.junit.*;

public class JStackTest extends TestUtil {

  @BeforeClass public static void stall() { stall_till_cloudsize(3); }

  @Test public void testJStack() {
    for( int i=0; i<10; i++ ) {
      String traces[] = new water.util.JStackCollectorTask().doAllNodes()._traces;
      Assert.assertEquals(traces.length,H2O.CLOUD.size());
    }
  }
}
