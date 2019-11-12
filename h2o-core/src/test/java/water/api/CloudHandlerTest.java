package water.api;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.api.schemas3.CloudV3;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

public class CloudHandlerTest extends TestUtil {

  @BeforeClass
  public static void beforeClass() {
    TestUtil.stall_till_cloudsize(1);
  }

  @Test
  public void status() {
    final CloudHandler cloudHandler = new CloudHandler();
    final CloudV3 status = cloudHandler.status(3, new CloudV3());
    assertNotNull(status);
    assertNotEquals(status.leader_idx, -1);
  }
}
