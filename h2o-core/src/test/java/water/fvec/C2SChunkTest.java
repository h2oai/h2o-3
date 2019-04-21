package water.fvec;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Futures;
import water.MRTask;
import water.Scope;
import water.TestUtil;
import water.util.PrettyPrint;

import java.io.IOException;
import java.util.Arrays;
import static org.junit.Assert.assertTrue;

public class C2SChunkTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  @Test
  public void test_inflate_impl() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(null, 0);

      // -32.767, 0.34, 0, 32.767, NA for l==0
      // NA, -32.767, 0.34, 0, 32.767, NA for l==1
      long[] man = new long[]{-32767, 34, 0, 32767};
      int[] exp = new int[]{-3, -2, 1, -3};
      if (l==1) nc.addNA(); //-32768
      for (int i = 0; i < man.length; ++i) nc.addNum(man[i], exp[i]);
      nc.addNA();
      int len = nc.len();
      Chunk cc = nc.compress();
      Assert.assertEquals(man.length + 1 + l, cc._len);
      Assert.assertTrue(cc instanceof C2SChunk);
      if (l==1) {
        Assert.assertTrue(cc.isNA(0));
        Assert.assertTrue(cc.isNA_abs(0));
      }
      for (int i = 0; i < man.length; ++i) {
        Assert.assertEquals(PrettyPrint.pow10(man[i],exp[i]), cc.atd(l + i), 0);
        Assert.assertEquals(PrettyPrint.pow10(man[i],exp[i]), cc.at_abs(l + i), 0);
      }
      Assert.assertTrue(cc.isNA(man.length + l));
      Assert.assertTrue(cc.isNA_abs(man.length + l));
      double[] densevals = new double[cc.len()];
      cc.getDoubles(densevals,0,cc.len());
      for (int i = 0; i < densevals.length; ++i) {
        if (cc.isNA(i)) Assert.assertTrue(Double.isNaN(densevals[i]));
        else Assert.assertEquals(densevals[i],cc.atd(i),0);
      }

      nc = cc.extractRows(new NewChunk(null, 0),0,len);
      Assert.assertEquals(man.length + 1 + l, nc._len);
      Assert.assertEquals(man.length + 1 + l, nc._sparseLen);
      if (l==1) {
        Assert.assertTrue(nc.isNA(0));
        Assert.assertTrue(nc.isNA_abs(0));
      }
      for (int i = 0; i < man.length; ++i) {
        Assert.assertEquals(PrettyPrint.pow10(man[i],exp[i]), nc.atd(l + i), 0);
        Assert.assertEquals(PrettyPrint.pow10(man[i],exp[i]), nc.at_abs(l + i), 0);
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
        Assert.assertEquals(PrettyPrint.pow10(man[i],exp[i]), cc2.atd(l + i), 0);
        Assert.assertEquals(PrettyPrint.pow10(man[i],exp[i]), cc2.at_abs(l + i), 0);
      }
      Assert.assertTrue(cc2.isNA(man.length + l));
      Assert.assertTrue(cc2.isNA_abs(man.length + l));
      Assert.assertTrue(cc2 instanceof C2SChunk);

      Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }
  @Test public void test_inflate_impl2() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(null, 0);

      long[] man = new long[]{-12767, 34, 0, 52767};
      int[] exp = new int[]{-3, -2, 1, -3};
      if (l==1) nc.addNA(); //-32768
      for (int i = 0; i < man.length; ++i) nc.addNum(man[i], exp[i]);
      nc.addNA();
      int len = nc.len();
      Chunk cc = nc.compress();
      Assert.assertEquals(man.length + 1 + l, cc._len);
      Assert.assertTrue(cc instanceof C2SChunk);
      if (l==1) {
        Assert.assertTrue(cc.isNA(0));
        Assert.assertTrue(cc.isNA_abs(0));
      }
      for (int i = 0; i < man.length; ++i) {
        Assert.assertEquals(PrettyPrint.pow10(man[i],exp[i]), cc.atd(l + i), 0);
        Assert.assertEquals(PrettyPrint.pow10(man[i],exp[i]), cc.at_abs(l + i), 0);
      }
      Assert.assertTrue(cc.isNA(man.length + l));

      nc = cc.extractRows(new NewChunk(null, 0),0,len);
      Assert.assertEquals(man.length + 1 + l, nc._len);
      Assert.assertEquals(man.length + 1 + l, nc._sparseLen);
      if (l==1) {
        Assert.assertTrue(nc.isNA(0));
        Assert.assertTrue(nc.isNA_abs(0));
      }
      for (int i = 0; i < man.length; ++i) {
        Assert.assertEquals(PrettyPrint.pow10(man[i],exp[i]), nc.atd(l + i), 0);
        Assert.assertEquals(PrettyPrint.pow10(man[i],exp[i]), nc.at_abs(l + i), 0);
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
        Assert.assertEquals(PrettyPrint.pow10(man[i],exp[i]), cc2.atd(l + i), 0);
        Assert.assertEquals(PrettyPrint.pow10(man[i],exp[i]), cc2.at_abs(l + i), 0);
      }
      Assert.assertTrue(cc2.isNA(man.length + l));
      Assert.assertTrue(cc2.isNA_abs(man.length + l));
      Assert.assertTrue(cc2 instanceof C2SChunk);

      Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }

  @Test public void test_setNA() {
    // Create a vec with one chunk with 15 elements, and set its numbers
    water.Key key = Vec.newKey();
    Vec vec = new Vec(key, Vec.ESPC.rowLayout(key,new long[]{0,15})).makeZero();
    int[] vals = new int[]{0, 3, 0, 6, 0, 0, 0, -32769, 0, 12, 234, 32765, 0, 0, 19};
    Vec.Writer w = vec.open();
    for (int i =0; i<vals.length; ++i) w.set(i, vals[i]);
    w.close();

    Chunk cc = vec.chunkForChunkIdx(0);
    assert cc instanceof C2SChunk;
    Futures fs = new Futures();
    fs.blockForPending();

    for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at8(i));
    for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at8_abs(i));

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
    Assert.assertTrue(cc2 instanceof C2SChunk);
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
      int[] exponents = new int[]{/*-32,*/-16, -8, -6, -4, -2, -1, 0, 1, 2, 4, 6, 8, 16/*,32*/};
      long[] biases = new long[]{-1234567, -12345, -1234, -1, 0, 1, 1234, 12345, 1234567};
      for (int exponent : exponents) {
        for (long bias : biases) {
          if (exponent == 0 && 100000 >= Math.abs(bias)) continue;
          NewChunk nc = new NewChunk(null, 0);
          double[] expected = new double[nvals];
          int j = 0;
          for (int i = Short.MIN_VALUE + 1; i < Short.MAX_VALUE; i += stepsz) {
            nc.addNum(bias + i, exponent);
            expected[j++] = Double.parseDouble((i + bias) + "e" + exponent);
          }
          assert j == nvals;
          Chunk c = nc.compress().deepCopy();
          String msg = "exp = " + exponent + " b = " + bias + " c = " + c.getClass().getSimpleName();
          Assert.assertTrue(msg, c instanceof C2SChunk);
          for (int i = 0; i < expected.length; ++i)
            Assert.assertEquals(msg + " i: " + i, expected[i], c.atd(i), 0);
        }
      }
    }
  }

  @Test public void testOverflow() throws IOException {
    Scope.enter();
    final long min = 1485333188427000000L;
    int len = 10000;
    try {
      Vec dz = Vec.makeZero(len);
      Vec z = dz.makeZero(); // make a vec consisting of C0LChunks
      Vec v = new MRTask() {
        @Override public void map(Chunk[] cs) {
          for (Chunk c : cs)
            for (int r = 0; r < c._len; r++)
              c.set(r, r + min + c.start());
        }
      }.doAll(z)._fr.vecs()[0];
      Scope.track(dz);
      Scope.track(z);
      Scope.track(v);

      for(long i=0; i<len; i++) {
        if( v.isNA(i) )
          System.out.println("MISSING: " + i);
        assertTrue("i/total: " + i + "/" + len + " min+i: " + (min+i) + " v.at8(i): " + v.at8(i) + " chk= " + v.elem2ChunkIdx(i), v.at8(i) == min + i);
      }

    } finally {
      Scope.exit();
    }
  }
}
