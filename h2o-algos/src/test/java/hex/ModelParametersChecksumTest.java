package hex;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import hex.tree.drf.DRFModel;
import water.TestUtil;
import water.fvec.Frame;

/**
 * Tests to catch problems with hash collisions on model parameters.
 */
public class ModelParametersChecksumTest extends TestUtil {

  @BeforeClass()
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void testPubDev2075() {
    Frame fr = null;

    try {
      fr = parse_test_file("smalldata/junit/cars_20mpg.csv");
      fr.replace(fr.find("cylinders"), fr.vec("cylinders").toCategoricalVec()).remove();

      DRFModel.DRFParameters p1 = new DRFModel.DRFParameters();
      p1._train = fr._key;
      p1._response_column = "economy_20mpg";
      p1._ignored_columns = new String[]{"name", "columns", "cylinders"};
      p1._ntrees = 2;
      p1._max_depth = 5;
      p1._nbins = 6;
      p1._mtries = 2;
      p1._seed = 8887264963748798740L;

      DRFModel.DRFParameters p2 = new DRFModel.DRFParameters();
      p2._train = fr._key;
      p2._response_column = "economy_20mpg";
      p2._ignored_columns = new String[]{"name", "columns", "cylinders"};
      p2._ntrees = 5;
      p2._max_depth = 1;
      p2._nbins = 3;
      p2._mtries = 4;
      p2._seed = 8887264963748798740L;

      Assert.assertNotEquals(p1.checksum(), p2.checksum());
    } finally {
      if (fr != null) {
        fr.delete();
      }
    }

  }

}
