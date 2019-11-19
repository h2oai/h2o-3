package hex.tree.xgboost;

import org.junit.Assume;
import org.junit.Test;
import water.H2O;

import static org.junit.Assert.fail;


/**
 * Once this test proves to be working we can move logic to TestUtil's rules
 */
public class IsTestMultiNodeTest {
  
  @Test
  public void testThatTestWillBeSkippedForMultinodeModeIfWeDontHaveProperCloud() {

    String isTestMultiNodeProperty = System.getProperty("IS_TEST_MULTI_NODE");
    if(isTestMultiNodeProperty != null) {
      boolean isTestMultiNodeMode = Boolean.parseBoolean(isTestMultiNodeProperty);
      if (isTestMultiNodeMode) // we can actually just rely on property existence if we set it only in testMultiNode.sh 
        Assume.assumeTrue(H2O.CLOUD.size() > 10); // 10 is just to test this logic as usually we build cloud of size 4

      fail("This failure is not expected as we have JUnit's Assume in place to skip test when we expect multi-node environment but have only single node");
    }
    
  }

}
