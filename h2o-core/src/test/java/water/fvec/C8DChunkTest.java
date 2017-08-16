package water.fvec;

import org.junit.*;

import water.Futures;
import water.TestUtil;
import java.util.Arrays;
import java.util.Iterator;

public class C8DChunkTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  @Test
  public void test_inflate_impl() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(null, 0);

      double[] vals = new double[]{Double.NaN, Double.MIN_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -3.1415926e-118, 0, 23423423.234234234, 0.00103E217, Double.MAX_VALUE};
      if (l==1) nc.addNA();
      for (double v : vals) nc.addNum(v);
      nc.addNA(); //-9223372036854775808l
      int len = nc.len();
      Chunk cc = nc.compress();
      Assert.assertEquals(vals.length + 1 + l, cc._len);
      Assert.assertTrue(cc instanceof C8DChunk);
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.atd(l + i), Math.ulp(vals[i]));
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at_abs(l + i), Math.ulp(vals[i]));
      Assert.assertTrue(cc.isNA(vals.length + l));
      Assert.assertTrue(cc.isNA_abs(vals.length + l));
      double[] densevals = new double[cc.len()];
      cc.getDoubles(densevals,0,cc.len());
      for (int i = 0; i < densevals.length; ++i) {
        if (cc.isNA(i)) Assert.assertTrue(Double.isNaN(densevals[i]));
        else Assert.assertTrue(cc.atd(i)==densevals[i]);
      }

      nc = cc.extractRows(new NewChunk(null, 0),0,len);
      Assert.assertEquals(vals.length + 1 + l, nc._len);
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], nc.atd(l + i), Math.ulp(vals[i]));
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], nc.at_abs(l + i), Math.ulp(vals[i]));
      Assert.assertTrue(nc.isNA(vals.length + l));
      Assert.assertTrue(nc.isNA_abs(vals.length + l));

      Chunk cc2 = nc.compress();
      Assert.assertEquals(vals.length + 1 + l, cc._len);
      Assert.assertTrue(cc2 instanceof C8DChunk);
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc2.atd(l + i), Math.ulp(vals[i]));
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc2.at_abs(l + i), Math.ulp(vals[i]));
      Assert.assertTrue(cc2.isNA(vals.length + l));
      Assert.assertTrue(cc2.isNA_abs(vals.length + l));

      Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }

  @Test public void test_setNA() {
    // Create a vec with one chunk with 15 elements, and set its numbers
    water.Key key = Vec.newKey();
    Vec vec = new Vec(key, Vec.ESPC.rowLayout(key,new long[]{0,15})).makeZero();
    double[] vals = new double[]{Double.MIN_VALUE, 0.1, 0, 0.2, 0, 0.1, 0, 0.3, 0, 0.2, 3.422, 3.767f, 0, 0, Double.MAX_VALUE};
    Vec.Writer w = vec.open();
    for (int i =0; i<vals.length; ++i) w.set(i, vals[i]);
    w.close();

    Chunk cc = vec.chunkForChunkIdx(0);
    assert cc instanceof C8DChunk;
    Futures fs = new Futures();
    fs.blockForPending();

    for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.atd(i), Double.MIN_VALUE);
    for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at_abs(i), Double.MIN_VALUE);

    int[] NAs = new int[]{1, 5, 2};
    int[] notNAs = new int[]{0, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14};
    for (int na : NAs) cc.setNA_abs(na);

    for (int na : NAs) Assert.assertTrue(cc.isNA(na));
    for (int na : NAs) Assert.assertTrue(cc.isNA_abs(na));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA(notna));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA_abs(notna));

    NewChunk nc = new NewChunk(null, 0);
    cc.extractRows(nc,0,(int)vec.length());
    Assert.assertEquals(vals.length, nc._sparseLen);
    Assert.assertEquals(vals.length, nc._len);

    for (int na : NAs) Assert.assertTrue(cc.isNA(na));
    for (int na : NAs) Assert.assertTrue(cc.isNA_abs(na));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA(notna));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA_abs(notna));

    Chunk cc2 = nc.compress();
    Assert.assertEquals(vals.length, cc._len);
    Assert.assertTrue(cc2 instanceof C8DChunk);
    for (int na : NAs) Assert.assertTrue(cc.isNA(na));
    for (int na : NAs) Assert.assertTrue(cc.isNA_abs(na));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA(notna));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA_abs(notna));

    Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    vec.remove();
  }

  @Test
  public void test_precision() {
    for (final int stepsz : new int[]{1/*,7, 17, 23, 31*/}) {
      int nvals = (Short.MAX_VALUE - Short.MIN_VALUE - 1) / stepsz + ((Short.MAX_VALUE - Short.MIN_VALUE - 1) % stepsz == 0?0:1);
      int[] exponents = new int[]{/*-32,*/-16, -8, -6, -4, -2, -1, 16/*,32*/};
      long[] biases = new long[]{-1234567, -12345, -1234, -1, 0, 1, 1234, 12345, 1234567};
      for (int exponent : exponents) {
        for (long bias : biases) {
          if (exponent == 0 && 100000 >= Math.abs(bias)) continue;
          NewChunk nc1 = new NewChunk(null, 0);
          NewChunk nc2 = new NewChunk(null, 0);
          double[] expected = new double[nvals+2];
          int j = 0;
          for (int i = Short.MIN_VALUE + 1; i < Short.MAX_VALUE; i += stepsz) {
            nc1.addNum(bias + i, exponent);
            nc2.addNum(bias + i, exponent);
            expected[j++] = Double.parseDouble((i + bias) + "e" + exponent);
          }
          nc1.addNum((long)Integer.MAX_VALUE+bias+1l,exponent);
          nc1.addNum(Integer.MIN_VALUE+bias-1l,exponent);
          expected[j++] = Double.parseDouble((Integer.MAX_VALUE+1l + bias) + "e" + exponent);
          expected[j++] = Double.parseDouble((Integer.MIN_VALUE-1l + bias) + "e" + exponent);
          nc2.addNum(expected[j-2]);
          nc2.addNum(expected[j-1]);
          assert j == nvals+2;
          Chunk c1 = nc1.compress();
          Chunk c2 = nc2.compress();
          if (!(c1 instanceof C8DChunk))
            System.out.println("exp = " + exponent + " b = " + bias + " c = " + c1.getClass().getSimpleName());
          Assert.assertTrue(c1 instanceof C8DChunk);
          Assert.assertTrue(c2 instanceof C8DChunk);
          for (int i = 0; i < expected.length; ++i) {
            Assert.assertEquals(expected[i], c1.atd(i), 0);
            Assert.assertEquals(expected[i], c2.atd(i), 0);
          }
        }
      }
    }
  }

}
