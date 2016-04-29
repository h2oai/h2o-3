package water.test.util;

import water.util.PojoUtils;
import hex.kmeans.KMeansModel;
import hex.tree.gbm.GBMModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;

public class PojoUtilsTest extends TestUtil {
  @BeforeClass()
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void testGetFieldValue() {
    GBMModel.GBMParameters o = new GBMModel.GBMParameters();
    Assert.assertEquals(50, PojoUtils.getFieldValue(o, "_ntrees", PojoUtils.FieldNaming.CONSISTENT));
  }
}
