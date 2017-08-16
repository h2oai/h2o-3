package water.fvec;

import org.junit.*;

import water.TestUtil;
import water.util.PrettyPrint;

import java.util.Arrays;

public class C4SChunkTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  @Test
  public void test_inflate_impl() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(null, 0);

      // -2.147483647, 0, 0.0000215, 2.147583647, NA for l==0
      // NA, -2.147483647, 0, 0.0000215, 2.147583647, NA for l==1
      long[] man = new long[]{Integer.MIN_VALUE+1, 0, 215, 188001238, Integer.MAX_VALUE};
      int[] exp = new int[]{-9, 1, -6, -8, -9};
      if (l==1) nc.addNA(); //-2147483648
      for (int i = 0; i < man.length; ++i) nc.addNum(man[i], exp[i]);
      nc.addNA();
      int len = nc.len();
      Chunk cc = nc.compress();
      Assert.assertEquals(man.length + 1 + l, cc._len);
      Assert.assertTrue(cc instanceof C4SChunk);
      if (l==1) {
        Assert.assertTrue(cc.isNA(0));
        Assert.assertTrue(cc.isNA_abs(0));
      }
      for (int i = 0; i < man.length; ++i) {
        Assert.assertEquals( PrettyPrint.pow10(man[i], exp[i]),cc.atd(l + i),0);
        Assert.assertEquals( PrettyPrint.pow10(man[i], exp[i]),cc.atd(l + i),0);
      }
      Assert.assertTrue(cc.isNA(man.length + l));
      Assert.assertTrue(cc.isNA_abs(man.length + l));
      double[] densevals = new double[cc.len()];
      cc.getDoubles(densevals,0,cc.len());
      for (int i = 0; i < densevals.length; ++i) {
        if (cc.isNA(i)) Assert.assertTrue(Double.isNaN(densevals[i]));
        else Assert.assertTrue(cc.atd(i)==densevals[i]);
      }

      nc = cc.extractRows(new NewChunk(null, 0),0,len);
      Assert.assertEquals(man.length + 1 + l, nc._len);
      if (l==1) {
        Assert.assertTrue(nc.isNA(0));
        Assert.assertTrue(nc.isNA_abs(0));
      }
      for (int i = 0; i < man.length; ++i) {
        Assert.assertTrue("Expected: " + man[i] * Math.pow(10, exp[i]) + ", but is " + nc.atd(l + i), Math.abs((man[i] * Math.pow(10, exp[i])) - nc.atd(l + i)) < 1e-10);
        Assert.assertTrue("Expected: " + man[i] * Math.pow(10, exp[i]) + ", but is " + nc.at_abs(l + i), Math.abs((man[i] * Math.pow(10, exp[i])) - nc.at_abs(l + i)) < 1e-10);
      }
      Assert.assertTrue(nc.isNA(man.length + l));
      Assert.assertTrue(nc.isNA_abs(man.length + l));

      Chunk cc2 = nc.compress();
      Assert.assertEquals(man.length + 1 + l, cc._len);
      if (l==1) {
        Assert.assertTrue(cc2.isNA(0));
        Assert.assertTrue(cc2.isNA_abs(0));
      }
      for (int i = 0; i < man.length; ++i) {
        Assert.assertTrue("Expected: " + man[i] * Math.pow(10, exp[i]) + ", but is " + cc2.atd(l + i), Math.abs((man[i] * Math.pow(10, exp[i])) - cc2.atd(l + i)) < 1e-10);
        Assert.assertTrue("Expected: " + man[i] * Math.pow(10, exp[i]) + ", but is " + cc2.at_abs(l + i), Math.abs((man[i] * Math.pow(10, exp[i])) - cc2.at_abs(l + i)) < 1e-10);
      }
      Assert.assertTrue(cc2.isNA(man.length + l));
      Assert.assertTrue(cc2.isNA_abs(man.length + l));
      Assert.assertTrue(cc2 instanceof C4SChunk);

      Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }
  @Test public void test_inflate_impl2() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(null, 0);

      // different bias and scale than above, but still using the full 32-bit range (~4.29 billion different integers from -8.1b to -3.8b)
      long[] man = new long[]{(long)(Integer.MIN_VALUE+1)*100000l - 613030080700000l, -5999999700000l, -58119987600000l, (long)Integer.MAX_VALUE*100000l - 613030080700000l};
      int[] exp = new int[]{-19, -17, -18, -19};

      if (l==1) nc.addNA(); //-2147483648
      for (int i = 0; i < man.length; ++i) nc.addNum(man[i], exp[i]);
      nc.addNA();
      int len = nc.len();
      Chunk cc = nc.compress();
      Assert.assertEquals(man.length + 1 + l, cc._len);
      Assert.assertTrue(cc instanceof C4SChunk);
      if (l==1) {
        Assert.assertTrue(cc.isNA(0));
        Assert.assertTrue(cc.isNA_abs(0));
      }
      for (int i = 0; i < man.length; ++i) {
        Assert.assertTrue("Expected: " + man[i] * Math.pow(10, exp[i]) + ", but is " + cc.atd(l + i), Math.abs((man[i] * Math.pow(10, exp[i])) - cc.atd(l + i)) < 1e-10);
        Assert.assertTrue("Expected: " + man[i] * Math.pow(10, exp[i]) + ", but is " + cc.at_abs(l + i), Math.abs((man[i] * Math.pow(10, exp[i])) - cc.at_abs(l + i)) < 1e-10);
      }
      Assert.assertTrue(cc.isNA(man.length + l));
      Assert.assertTrue(cc.isNA_abs(man.length + l));

      nc = cc.extractRows(new NewChunk(null, 0),0,len);
      Assert.assertEquals(man.length + 1 + l, nc._len);
      if (l==1) {
        Assert.assertTrue(nc.isNA(0));
        Assert.assertTrue(nc.isNA_abs(0));
      }
      for (int i = 0; i < man.length; ++i) {
        Assert.assertTrue("Expected: " + man[i] * Math.pow(10, exp[i]) + ", but is " + nc.atd(l + i), Math.abs((man[i] * Math.pow(10, exp[i])) - nc.atd(l + i)) < 1e-10);
        Assert.assertTrue("Expected: " + man[i] * Math.pow(10, exp[i]) + ", but is " + nc.at_abs(l + i), Math.abs((man[i] * Math.pow(10, exp[i])) - nc.at_abs(l + i)) < 1e-10);
      }
      Assert.assertTrue(nc.isNA(man.length + l));
      Assert.assertTrue(nc.isNA_abs(man.length + l));

      Chunk cc2 = nc.compress();
      Assert.assertEquals(man.length + 1 + l, cc._len);
      if (l==1) {
        Assert.assertTrue(cc2.isNA(0));
        Assert.assertTrue(cc2.isNA_abs(0));
      }
      for (int i = 0; i < man.length; ++i) {
        Assert.assertTrue("Expected: " + man[i] * Math.pow(10, exp[i]) + ", but is " + cc2.atd(l + i), Math.abs((man[i] * Math.pow(10, exp[i])) - cc2.atd(l + i)) < 1e-10);
        Assert.assertTrue("Expected: " + man[i] * Math.pow(10, exp[i]) + ", but is " + cc2.at_abs(l + i), Math.abs((man[i] * Math.pow(10, exp[i])) - cc2.at_abs(l + i)) < 1e-10);
      }
      Assert.assertTrue(cc2.isNA(man.length + l));
      Assert.assertTrue(cc2.isNA_abs(man.length + l));
      Assert.assertTrue(cc2 instanceof C4SChunk);

      Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }

  @Test
  public void test_precision() {
    int dec_cntr = 0, scl_cntr = 0;
    for (final int stepsz : new int[]{1/*,7, 17, 23, 31*/}) {
      int nvals = (Short.MAX_VALUE - Short.MIN_VALUE + 200) / stepsz;
      int[] exponents = new int[]{/*-32,*/-16, -8, -6, -4, -2, -1};
      long[] biases = new long[]{-1234567, -12345, -1234, -1, 0, 1, 1234, 12345, 1234567};
      for (int exponent : exponents) {
        for (long bias : biases) {
          NewChunk nc = new NewChunk(null, 0);
          double[] expected = new double[nvals];
          int j = 0;
          for (int i = Short.MIN_VALUE -100; i < Short.MAX_VALUE + 100; i += stepsz) {
            nc.addNum(bias + i, exponent);
            expected[j++] = Double.parseDouble((i + bias) + "e" + exponent);
          }
          assert j == nvals;
          Chunk c = nc.compress();
          if (!(c instanceof C4SChunk))
            System.out.println("exp = " + exponent + " b = " + bias + " c = " + c.getClass().getSimpleName());
          Assert.assertTrue(c instanceof C4SChunk);
          for (int i = 0; i < expected.length; ++i) {
            Assert.assertEquals(expected[i], c.atd(i), 0);
          }
        }
      }
    }
    System.out.println("There were " + dec_cntr + " decimal chunks versus " + scl_cntr + " standard c2s chunks");
  }
}
