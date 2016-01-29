package ai.h2o.automl.collectors;


import ai.h2o.automl.TestUtil;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;

public class DynamicHistTest extends TestUtil {

  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  @Test public void histSmall1() {
    Frame fr = parse_test_file(Key.make("a.hex"),"smalldata/iris.csv");
    int vec = 0;
    Vec v = fr.vec(vec);
    MetaCollector.DynamicHisto h = new MetaCollector.DynamicHisto(fr.name(vec), 50, 50, (byte)(v.isCategorical() ? 2 : (v.isInt()?1:0)), v.min(), v.max());
    h.doAll(v);
    fr.delete();

    double[] expected = new double[]{4.3,4.372,4.444,4.516,4.588,4.66,4.732,4.804,4.876,
            4.948, 5.0200000000000005,5.0920000000000005,5.164,5.236000000000001,
            5.308,5.38,5.452,5.524,5.596,5.668,5.74,5.812,5.884,5.956,6.0280000000000005,
            6.1000000000000005,6.172000000000001,6.244000000000001,6.316000000000001,
            6.388,6.460000000000001,6.532,6.604000000000001,6.676,6.748000000000001,
            6.82,6.892000000000001,6.964,7.036000000000001,7.1080000000000005,
            7.1800000000000015,7.252000000000001,7.324000000000001,7.396000000000001,
            7.468000000000001,7.540000000000001,7.612000000000001,7.684000000000001,
            7.756000000000001,7.828000000000001};

    double[] actual = new double[h._h._nbin];
    for(int i=0; i < actual.length; ++i) actual[i] = h.binAt(i);
    Assert.assertArrayEquals(expected, actual, 0);
  }

  @Test public void histSmall2() {
    Frame fr = parse_test_file(Key.make("a.hex"),"smalldata/iris.csv");
    int vec=1;
    Vec v = fr.vec(vec);
    MetaCollector.DynamicHisto h = new MetaCollector.DynamicHisto(fr.name(vec), 10, 50, (byte)(v.isCategorical() ? 2 : (v.isInt()?1:0)), v.min(), v.max());
    h.doAll(v);
    fr.delete();
  }

  @Test public void histSmallCats() {
    Frame fr = parse_test_file(Key.make("a.hex"),"smalldata/iris.csv");
    int vec=1;
    Vec v = fr.vec(vec);
    MetaCollector.DynamicHisto h = new MetaCollector.DynamicHisto(fr.name(vec), 10, 50, (byte)(v.isCategorical() ? 2 : (v.isInt()?1:0)), v.min(), v.max());
    h.doAll(v);
    fr.delete();
  }
}