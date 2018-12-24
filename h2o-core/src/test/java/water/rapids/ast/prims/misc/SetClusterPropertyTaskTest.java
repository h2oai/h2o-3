package water.rapids.ast.prims.misc;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.MRTask;
import water.TestUtil;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.vals.ValStr;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class SetClusterPropertyTaskTest {

  @Test
  public void testSetClusterProperty() {
    Val val = Rapids.exec("(setproperty 'test.set.cluster.property' 'test-value')");
    assertTrue(val instanceof ValStr);
    new MRTask() {
      @Override
      protected void setupLocal() {
        if (! "test-value".equals(System.getProperty("test.set.cluster.property"))) {
          throw new IllegalStateException("Value was not set");
        }
      }
    }.doAllNodes();
    Val oldVal = Rapids.exec("(setproperty 'test.set.cluster.property' 'test-value2')");
    assertTrue(oldVal.getStr().startsWith("Old values of test.set.cluster.property (per node): test-value"));
  }

}
