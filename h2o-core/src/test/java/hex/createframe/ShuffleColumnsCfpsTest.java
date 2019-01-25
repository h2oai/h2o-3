package hex.createframe;

import hex.createframe.postprocess.ShuffleColumnsCfps;
import org.junit.Assert;
import org.junit.Test;
import water.TestUtil;

/**
 */
public class ShuffleColumnsCfpsTest extends TestUtil {

  @Test
  public void removeNumericSuffixTest() {
    Assert.assertEquals("C", ShuffleColumnsCfps.removeNumericSuffix("C1"));
    Assert.assertEquals("Ch", ShuffleColumnsCfps.removeNumericSuffix("Ch0"));
    Assert.assertEquals("abc", ShuffleColumnsCfps.removeNumericSuffix("abc1234567890"));
    Assert.assertEquals("response", ShuffleColumnsCfps.removeNumericSuffix("response"));
    Assert.assertEquals("test1test", ShuffleColumnsCfps.removeNumericSuffix("test1test2"));
    Assert.assertEquals("-", ShuffleColumnsCfps.removeNumericSuffix("-999"));
    Assert.assertEquals("", ShuffleColumnsCfps.removeNumericSuffix("0123"));
    Assert.assertEquals("", ShuffleColumnsCfps.removeNumericSuffix(""));
  }
}
