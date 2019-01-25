package water.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;
import water.H2O;
import water.TestUtil;
import water.init.NetworkTest;

public class NetworkTestTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(5); }

  @Test public void testNetworkTest() {
    NetworkTest nt = new NetworkTest();
    nt.execImpl();
    assertEquals(nt.nodes.length, H2O.CLOUD.size());
    assertTrue(nt.bandwidths.length == nt.msg_sizes.length);
    assertTrue(nt.microseconds.length == nt.msg_sizes.length);
    assertTrue(nt.bandwidths_collective.length == nt.msg_sizes.length);
    assertTrue(nt.microseconds_collective.length == nt.msg_sizes.length);
    for (int m=0; m<nt.msg_sizes.length; ++m) {
      for (int j=0; j<H2O.CLOUD.size(); ++j) {
        assertTrue(nt.bandwidths[m].length == H2O.CLOUD.size());
        assertTrue(nt.microseconds[m].length == H2O.CLOUD.size());
      }
    }
  }
}
