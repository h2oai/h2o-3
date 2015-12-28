package ai.h2o.automl;


import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.fvec.Frame;

public class AutoMLTest extends TestUtil {

  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  @Test public void histSmallCats() {
    Frame fr = parse_test_file(Key.make("a.hex"),"/0xdata/h2o-3/smalldata/iris/iris_wheader.csv");
    int vec=1;
    AutoML aml = new AutoML(fr, 4, "", -1, -1, false, null, true);
    aml.learn();
    fr.delete();
    aml.delete();
  }
}
