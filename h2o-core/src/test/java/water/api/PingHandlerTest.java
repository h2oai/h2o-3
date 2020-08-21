package water.api;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.api.schemas3.PingV3;

import static org.junit.Assert.assertNotNull;

public class PingHandlerTest extends TestUtil {

  @BeforeClass
  public static void beforeClass() {
    TestUtil.stall_till_cloudsize(1);
  }

  @Test
  public void status() {
    final PingHandler handler = new PingHandler();
    final PingV3 heartbeat = handler.ping(3, new PingV3());
    assertNotNull(heartbeat);
  }
}
