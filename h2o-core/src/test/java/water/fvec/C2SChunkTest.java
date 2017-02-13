package water.fvec;

import org.junit.*;

import water.Futures;
import water.TestUtil;
import java.util.Arrays;
import java.util.Iterator;

public class C2SChunkTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  @Test
  public void test_inflate_impl() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(Vec.T_NUM);

      // -32.767, 0.34, 0, 32.767, NA for l==0
      // NA, -32.767, 0.34, 0, 32.767, NA for l==1
      long[] man = new long[]{-32767, 34, 0, 32767};
      int[] exp = new int[]{-3, -2, 1, -3};
      if (l==1) nc.addNA(); //-32768
      for (int i = 0; i < man.length; ++i) nc.addNum(man[i], exp[i]);
      nc.addNA();

      C2SChunk cc = (C2SChunk) nc.compress();
      Assert.assertEquals(man.length + 1 + l, cc.len());
      if (l==1)
        Assert.assertTrue(cc.isNA(0));

      for (int i = 0; i < man.length; ++i) {
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc.atd(l + i), 0);
      }
      Assert.assertTrue(cc.isNA(man.length + l));
      double[] densevals = new double[cc.len()];
      cc.getDoubles(densevals,0,cc.len());
      for (int i = 0; i < densevals.length; ++i) {
        if (cc.isNA(i)) Assert.assertTrue(Double.isNaN(densevals[i]));
        else Assert.assertTrue(cc.atd(i)==densevals[i]);
      }

      nc = cc.add2Chunk(new NewChunk(Vec.T_NUM),0,cc.len());
      nc.values(0, nc._len);
      Assert.assertEquals(man.length + 1 + l, nc._len);
      Assert.assertEquals(man.length + 1 + l, nc._sparseLen);
      if (l==1) {
        Assert.assertTrue(nc.isNA(0));
      }
      for (int i = 0; i < man.length; ++i) {
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) nc.atd(l + i), 0);
      }
      Assert.assertTrue(nc.isNA(man.length + l));

      C2SChunk cc2 = (C2SChunk) nc.compress();
      Assert.assertEquals(man.length + 1 + l, cc.len());
      if (l==1) {
        Assert.assertTrue(cc2.isNA(0));
      }
      for (int i = 0; i < man.length; ++i) {
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc2.atd(l + i), 0);
      }
      Assert.assertTrue(cc2.isNA(man.length + l));


      Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }
  @Test public void test_inflate_impl2() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(Vec.T_NUM);

      long[] man = new long[]{-12767, 34, 0, 52767};
      int[] exp = new int[]{-3, -2, 1, -3};
      if (l==1) nc.addNA(); //-32768
      for (int i = 0; i < man.length; ++i) nc.addNum(man[i], exp[i]);
      nc.addNA();
      int len = nc.len();
      C2SChunk cc = (C2SChunk) nc.compress();
      Assert.assertEquals(man.length + 1 + l, cc.len());

      if (l==1) {
        Assert.assertTrue(cc.isNA(0));
      }
      for (int i = 0; i < man.length; ++i) {
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc.atd(l + i), 0);
      }
      Assert.assertTrue(cc.isNA(man.length + l));

      nc = cc.add2Chunk(new NewChunk(Vec.T_NUM),0,len);
      nc.values(0, nc._len);
      Assert.assertEquals(man.length + 1 + l, nc._len);
      Assert.assertEquals(man.length + 1 + l, nc._sparseLen);
      if (l==1) {
        Assert.assertTrue(nc.isNA(0));
      }
      for (int i = 0; i < man.length; ++i) {
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) nc.atd(l + i), 0);
      }
      Assert.assertTrue(nc.isNA(man.length + l));

      C2SChunk cc2 = (C2SChunk) nc.compress();
      Assert.assertEquals(man.length + 1 + l, cc.len());
      if (l==1) {
        Assert.assertTrue(cc2.isNA(0));
      }
      for (int i = 0; i < man.length; ++i) {
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc2.atd(l + i), 0);
      }
      Assert.assertTrue(cc2.isNA(man.length + l));


      Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }

  @Test public void test_setNA() {
    // Create a vec with one chunk with 15 elements, and set its numbers
    water.Key key = Vec.newKey();
    Vec vec = new Vec(key, Vec.ESPC.rowLayout(key,new long[]{0,15}),1).makeZero();
    int[] vals = new int[]{0, 3, 0, 6, 0, 0, 0, -32769, 0, 12, 234, 32765, 0, 0, 19};
    VecAry.Writer w = new VecAry(vec).open();
    for (int i =0; i<vals.length; ++i) w.set(i, vals[i]);
    w.close();

    ChunkAry cc = vec.chunkForChunkIdx(0);
    assert cc.getChunk(0) instanceof C2SChunk;
    Futures fs = new Futures();
    fs.blockForPending();

    for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at8(i));

    int[] NAs = new int[]{1, 5, 2};
    int[] notNAs = new int[]{0, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14};
    for (int na : NAs) cc.setNA(na,0);


    for (int na : NAs) Assert.assertTrue(cc.isNA(na));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA(notna));

    NewChunk nc = new NewChunk(Vec.T_NUM);
    (cc.getChunk(0)).add2Chunk(nc,0,cc._len);
    nc.values(0, nc._len);
    Assert.assertEquals(vals.length, nc._sparseLen);
    Assert.assertEquals(vals.length, nc._len);

    Iterator<NewChunk.Value> it = nc.values(0, vals.length);
    for (int i = 0; i < vals.length; ++i) Assert.assertTrue(it.next().rowId0() == i);
    Assert.assertTrue(!it.hasNext());

    for (int na : NAs) Assert.assertTrue(cc.isNA(na));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA(notna));

    C2SChunk cc2 = (C2SChunk) nc.compress();
    Assert.assertEquals(vals.length, cc._len);
    for (int na : NAs) Assert.assertTrue(cc.isNA(na));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA(notna));

    Assert.assertTrue(Arrays.equals(cc.getChunk(0).asBytes(), cc2._mem));
    vec.remove();
  }
}
