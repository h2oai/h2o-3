package water.runner;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.TestUtil;
import water.fvec.Vec;
import water.util.Log;

import static org.junit.Assert.assertEquals;

@CloudSize(1)
@RunWith(H2ORunner.class)
// not a test, just a place for developers to experiment with H2ORunner
public class H2ORunnerPlayground {

  @Test
  public void testReportRollupsLeaks() {
    Assume.assumeFalse(TestUtil.isCI()); // don't run this on CI
    Vec v = Vec.makeZero((long) 1e6);
    Log.info("Created key: ", v._key.toString());
    assertEquals(0, v.min(), 0);
  }

}
