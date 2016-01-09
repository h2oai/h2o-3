package ai.h2o.automl.tasks;

import ai.h2o.automl.TestUtil;
import junit.framework.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.fvec.Frame;


public class VIFTest extends TestUtil {
  @BeforeClass
  public static void setup() { stall_till_cloudsize(1); }

  @Test public void testVIFs() {
    Frame fr = null;
    try {
      fr = parse_test_file(Key.make("a.hex"), "/0xdata/h2o-3/smalldata/iris/iris_wheader.csv");
      VIF[] vifs = VIF.make(fr._key, new String[]{"sepal_len", "sepal_wid","petal_len","petal_wid"}, fr.names());
      VIF.launchVIFs(vifs);
      int idx=0;
      double[] vifsGolden = new double[]{
              7.103113532646519,
              2.0990386110226114,
              31.397292492151717,
              16.14156350559764};
      for(VIF vif: vifs)
        Assert.assertTrue(vif.vif() == vifsGolden[idx++]);

    } finally {
      if(fr!=null)  fr.delete();
    }
  }
}
