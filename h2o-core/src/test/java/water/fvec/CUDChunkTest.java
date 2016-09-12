package water.fvec;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.AutoBuffer;
import water.IcedUtils;
import water.TestUtil;

import java.util.Arrays;

public class CUDChunkTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  @Test
  public void test_inflate_impl() {
    NewChunk nc = new NewChunk(null, 0);
    final double a = -3.1415926e-118;
    final double b = 23423423.234234234;
    final double c = 0.00103E217;
    double[] vals = new double[]{
            Double.NaN, Double.MIN_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, a, 0, b, c, Double.MAX_VALUE,
            Double.NaN, Double.MIN_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, a, 0, b, c, Double.MAX_VALUE,
            Double.NaN, Double.MIN_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, a, 0, b, c, Double.MAX_VALUE,
            a, Double.NaN, Double.MIN_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, a, 0, b, c, Double.MAX_VALUE,
            Double.NaN, Double.MIN_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, a, 0, b, c, Double.MAX_VALUE,
            Double.NaN, b, Double.MIN_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, a, 0, b, c, Double.MAX_VALUE,
            Double.NaN, Double.MIN_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, a, 0, b, c, Double.MAX_VALUE,
            Double.NaN, Double.MIN_VALUE, c, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, a, 0, b, c, Double.MAX_VALUE,
            Double.NaN, Double.MIN_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, a, 0, b, c, Double.MAX_VALUE,
            Double.NaN, Double.MIN_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, a, a, 0, b, c, Double.MAX_VALUE,
            Double.NaN, b, Double.MIN_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, a, 0, b, c, Double.MAX_VALUE,
            Double.NaN, Double.MIN_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, a, 0, b, c, Double.MAX_VALUE,
            Double.NaN, Double.MIN_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, a, 0, b, c, Double.MAX_VALUE,
            Double.NaN, Double.MIN_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, a, 0, b, c, Double.MAX_VALUE,
            Double.NaN, Double.MIN_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, a, 0, b, c, Double.MAX_VALUE,
            Double.NaN, Double.MIN_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, a, 0, b, c, Double.MAX_VALUE,
    };
    for (double v : vals) nc.addNum(v);
    nc.addNA();

    Chunk cc = nc.compress();
    Assert.assertEquals(vals.length + 1, cc._len);
    Assert.assertTrue(cc instanceof CUDChunk);
    for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.atd(i), Math.ulp(vals[i]));
    for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at_abs(i), Math.ulp(vals[i]));
    Assert.assertTrue(cc.isNA(vals.length));
    Assert.assertTrue(cc.isNA_abs(vals.length));

    Chunk cc2 = IcedUtils.deepCopy(cc);
    Assert.assertEquals(cc._len, cc2._len);
    Assert.assertEquals(vals.length + 1, cc2._len);
    Assert.assertTrue(cc2 instanceof CUDChunk);
    for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc2.atd(i), Math.ulp(vals[i]));
    for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc2.at_abs(i), Math.ulp(vals[i]));
    Assert.assertTrue(cc2.isNA(vals.length));
    Assert.assertTrue(cc2.isNA_abs(vals.length));

    // randomly writing one of the unique values is fine
    cc.set_impl(vals.length-1, a);
    Assert.assertTrue(cc.atd(vals.length - 1) == a);
    vals[vals.length-1]=a;

    nc = cc.inflate_impl(new NewChunk(null, 0));
    nc.values(0, nc._len);
    Assert.assertEquals(vals.length + 1, nc._len);
    for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], nc.atd(i), Math.ulp(vals[i]));
    for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], nc.at_abs(i), Math.ulp(vals[i]));
    Assert.assertTrue(nc.isNA(vals.length));
    Assert.assertTrue(nc.isNA_abs(vals.length));

    cc2 = nc.compress();
    Assert.assertEquals(vals.length + 1, cc._len);
    Assert.assertTrue(cc2 instanceof CUDChunk);
    for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc2.atd(i), Math.ulp(vals[i]));
    for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc2.at_abs(i), Math.ulp(vals[i]));
    Assert.assertTrue(cc2.isNA(vals.length));
    Assert.assertTrue(cc2.isNA_abs(vals.length));
    Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
  }
}
