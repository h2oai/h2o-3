package ai.h2o.automl;

import org.junit.BeforeClass;
import org.junit.Test;

import static junit.framework.TestCase.assertTrue;

public class FailingOnPurposeMultinodeTest extends water.TestUtil {

  @BeforeClass public static void setup() { stall_till_cloudsize(2); }

  @Test public void testThatAutoMLJUnitIsNotActuallyRunningTests() {
    assertTrue(false);
  }
}
