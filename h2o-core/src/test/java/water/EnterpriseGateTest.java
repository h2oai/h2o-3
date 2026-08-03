package water;

import static org.junit.Assert.*;
import org.junit.Test;
import water.exceptions.H2OIllegalArgumentException;

/**
 * Unit tests for the server-side H2O-3 Enterprise gate. Pure (no cloud needed):
 * {@link EnterpriseGate#block(String)} only builds and throws.
 */
public class EnterpriseGateTest {

  @Test
  public void blockAlwaysThrows() {
    try {
      EnterpriseGate.block("MOJO export");
      fail("expected EnterpriseGate.block to throw");
    } catch (H2OIllegalArgumentException e) {
      String msg = e.getMessage();
      assertTrue("names the operation", msg.contains("MOJO export"));
      assertTrue("marked as blocked/enterprise", msg.contains("H2O-3 ENTERPRISE REQUIRED"));
      assertTrue("routes to contact", msg.contains(EnterpriseGate.ENTERPRISE_EMAIL));
      assertTrue("links the comparison page", msg.contains(EnterpriseGate.LEARN_MORE));
    }
  }

  /**
   * The MOJO/POJO writers guard with {@link EnterpriseGate#blockUnlessTestHarness(String)} so the
   * JUnit suites can keep round-tripping models through MOJOs to compare in-cluster scoring with
   * MOJO scoring. h2o-test-support is on this classpath, so the exemption must be active here.
   */
  @Test
  public void testHarnessIsDetected() {
    assertTrue("h2o-test-support is on the test classpath, so the harness must be detected",
               EnterpriseGate.isTestHarness());
  }

  @Test
  public void blockUnlessTestHarnessIsInertUnderTheHarness() {
    try {
      EnterpriseGate.blockUnlessTestHarness("MOJO export");
    } catch (H2OIllegalArgumentException e) {
      fail("blockUnlessTestHarness must be inert under the test harness, but threw: " + e.getMessage());
    }
  }

  /**
   * Pins the exemption to the harness check rather than to a globally disabled gate: the REST
   * layer's unconditional {@link EnterpriseGate#block(String)} must still throw in the very same
   * JVM in which {@code blockUnlessTestHarness} above did not.
   */
  @Test
  public void blockStillThrowsUnderTheHarness() {
    assertTrue("precondition: running under the harness", EnterpriseGate.isTestHarness());
    try {
      EnterpriseGate.block("MOJO export");
      fail("block must throw even under the test harness - it is what the REST layer uses");
    } catch (H2OIllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("H2O-3 ENTERPRISE REQUIRED"));
    }
  }

  @Test
  public void blockCarriesTheGivenOperation() {
    try {
      EnterpriseGate.block("MOJO download");
      fail("expected EnterpriseGate.block to throw");
    } catch (H2OIllegalArgumentException e) {
      assertTrue(e.getMessage().contains("MOJO download"));
    }
  }
}
