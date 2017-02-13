package water.fvec;

import org.junit.*;

import water.Futures;
import water.TestUtil;
import java.util.Arrays;
import java.util.Iterator;

public class C1SChunkTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  @Test
  public void test_inflate_impl() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(Vec.T_NUM);
      // 0, 0.2, 0.3, 2.54, NA for l==0
      // NA, 0, 0.2, 0.3, 2.54, NA for l==1
      long[] man = new long[]{0, 2, 3, 254};
      int[] exp = new int[]{1, -1, -1, -2};
      if (l==1) nc.addNA();
      for (int i = 0; i < man.length; ++i) nc.addNum(man[i], exp[i]);
      nc.addNA();

      C1SChunk cc = (C1SChunk) nc.compress();
      if (l==1) {
        Assert.assertTrue(cc.isNA(0));
      }
      for (int i = 0; i < man.length; ++i) {
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc.atd(l + i), 0);

      }
      Assert.assertTrue(cc.isNA(man.length + l));

      double[] densevals = new double[man.length];
      cc.getDoubles(densevals,0,densevals.length);
      for (int i = 0; i < densevals.length; ++i) {
        if (cc.isNA(i)) Assert.assertTrue(Double.isNaN(densevals[i]));
        else Assert.assertTrue(cc.atd(i)==densevals[i]);
      }

      nc = cc.add2Chunk(new NewChunk(Vec.T_NUM),0,densevals.length);
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


      C1SChunk cc2 = (C1SChunk) nc.compress();

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
      long[] man = new long[]{-1228, -997, -9740};
      int[] exp = new int[]{-4, -4, -5};
      if (l==1) nc.addNA();
      for (int i = 0; i < man.length; ++i) nc.addNum(man[i], exp[i]);
      nc.addNA();

      C1SChunk cc = (C1SChunk) nc.compress();

      Assert.assertTrue(cc instanceof C1SChunk);
      if (l==1) Assert.assertTrue(cc.isNA(0));
      for (int i = 0; i < man.length; ++i)
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc.atd(l + i), 0);
      Assert.assertTrue(cc.isNA(man.length + l));

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


      C1SChunk cc2 = (C1SChunk) nc.compress();
      Assert.assertEquals(man.length + 1 + l, cc.len());
      Assert.assertTrue(cc2 instanceof C1SChunk);
      if (l==1) Assert.assertTrue(cc2.isNA(0));
      for (int i = 0; i < man.length; ++i)
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc2.atd(l + i), 0);
      Assert.assertTrue(cc2.isNA(man.length + l));

      Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }

  @Test public void test_setNA() {
    // Create a vec with one chunk with 15 elements, and set its numbers
    water.Key key = Vec.newKey();
    Vec vec = new Vec(key, Vec.ESPC.rowLayout(key,new long[]{0,15}),1).makeZero();
    int[] vals = new int[]{0, 3, 0, 6, 0, 0, 0, -128, 0, 12, 0, 126, 0, 0, 19};
    VecAry.Writer w = new VecAry(vec).open();
    for (int i =0; i<vals.length; ++i) w.set(i, vals[i]);
    w.close();

    ChunkAry cc = vec.chunkForChunkIdx(0);
    assert cc.getChunk(0) instanceof C1SChunk;
    Futures fs = new Futures();
    fs.blockForPending();

    for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at8(i));


    int[] NAs = new int[]{1, 5, 2};
    int[] notNAs = new int[]{0, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14};
    for (int na : NAs) cc.setNA(na,0);

    for (int na : NAs) Assert.assertTrue(cc.isNA(na));

    for (int notna : notNAs) Assert.assertTrue(!cc.isNA(notna));


    NewChunk nc = new NewChunk(Vec.T_NUM);
    ((ByteArraySupportedChunk)cc.getChunk(0)).add2Chunk(nc,0,cc._len);
    nc.values(0, nc._len);
    Assert.assertEquals(vals.length, nc._sparseLen);
    Assert.assertEquals(vals.length, nc._len);

    Iterator<NewChunk.Value> it = nc.values(0, vals.length);
    for (int i = 0; i < vals.length; ++i) Assert.assertTrue(it.next().rowId0() == i);
    Assert.assertTrue(!it.hasNext());

    for (int na : NAs) Assert.assertTrue(cc.isNA(na));

    for (int notna : notNAs) Assert.assertTrue(!cc.isNA(notna));

    C1SChunk cc2 = (C1SChunk) nc.compress();
    Assert.assertEquals(vals.length, cc._len);
    Assert.assertTrue(cc2 instanceof C1SChunk);
    for (int na : NAs) Assert.assertTrue(cc.isNA(na));

    for (int notna : notNAs) Assert.assertTrue(!cc.isNA(notna));


    Assert.assertTrue(Arrays.equals(((ByteArraySupportedChunk)cc.getChunk(0))._mem, cc2._mem));
    vec.remove();
  }

}
