package water.parser;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import water.MRTask;
import water.TestUtil;

/**
 * Test parser service.
 */
public class ParserServiceTest extends TestUtil {

  @BeforeClass
  static public void setup() { TestUtil.stall_till_cloudsize(1); }

  // A list of REGISTERED core provider names in the expected order based on priorities.
  // Warning: The order is fixed in the test to detect any changes in the code!!!
  private static final String[] CORE_PROVIDER_NAMES = { "GUESS", "ARFF", "XLS", "SVMLight", "CSV"};

  @Test
  public void testVerifyCoreProvidersInCaller() {
    verifyCoreProviders();
  }

  @Test
  public void testVerifyCoreProvidersPerNode() {
    new MRTask() {
      @Override
      protected void setupLocal() {
        verifyCoreProviders();
      }
    }.doAllNodes();
  }

  private static void verifyCoreProviders() {
    ParserService ps = ParserService.INSTANCE;
    List<ParserProvider> providers = ps.getAllProviders(true);
    int lastPrio = Integer.MIN_VALUE;
    // Verify names and ordering
    int idx = 0;
    for (ParserProvider provider : providers) {
      Assert.assertEquals("Name of registered core parser providers has to match!", CORE_PROVIDER_NAMES[idx++], provider.info().name());
      Assert.assertTrue("#getAllProviders call should returned sorted providers based on priorities", lastPrio < provider.info().priority());
      lastPrio = provider.info().priority();
    }
  }
}
