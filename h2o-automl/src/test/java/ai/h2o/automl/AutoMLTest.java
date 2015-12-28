package ai.h2o.automl;


import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.fvec.Frame;

public class AutoMLTest extends TestUtil {

  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  @Test public void histSmallCats() {
    Frame fr = parse_test_file(Key.make("a.hex"),"/0xdata/h2o-3/smalldata/iris/iris_wheader.csv");
    AutoML aml = new AutoML(fr, 4, "", -1, -1, false, null, true);
    aml.learn();
    fr.delete();
    aml.delete();
  }

  @Test public void checkMeta() {
    Frame fr = parse_test_file(Key.make("a.hex"),"/0xdata/h2o-3/smalldata/iris/iris_wheader.csv");
    AutoML aml = new AutoML(fr, 4, "", -1, -1, false, null, true);
    aml.learn();

    // cleanup
    fr.delete();
    aml.delete();


    // sepal_len column
    // check the third & fourth moment computations
    Assert.assertTrue(aml._fm._cols[0]._thirdMoment == 0.17642222222222248);
    Assert.assertTrue(aml._fm._cols[0]._fourthMoment == 1.1332434671886653);

    // check skew and kurtosis
    Assert.assertTrue(aml._fm._cols[0]._skew == 0.31071214388181395);
    Assert.assertTrue(aml._fm._cols[0]._kurtosis == 2.410255837401182);

  }

  // TODO: test for poking model leader
}
