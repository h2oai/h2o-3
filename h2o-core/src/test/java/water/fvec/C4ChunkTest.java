package water.fvec;

import org.junit.*;

import water.Futures;
import water.TestUtil;
import java.util.Arrays;
import java.util.Iterator;

public class C4ChunkTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  @Test
  public void test_inflate_impl() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(Vec.T_NUM);

      int[] vals = new int[]{-2147483647, 0, 2147483647};
      if (l==1) nc.addNA();
      for (int v : vals) nc.addNum(v, 0);
      nc.addNA(); //-2147483648

      ByteArraySupportedChunk cc = (ByteArraySupportedChunk) nc.compress();
      Assert.assertEquals(vals.length + 1 + l, cc.len());
      Assert.assertTrue(cc instanceof C4Chunk);
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at8(l + i));

      Assert.assertTrue(cc.isNA(vals.length + l));

      double[] densevals = new double[cc.len()];
      cc.getDoubles(densevals,0,cc.len());
      for (int i = 0; i < densevals.length; ++i) {
        if (cc.isNA(i)) Assert.assertTrue(Double.isNaN(densevals[i]));
        else Assert.assertTrue(cc.at8(i)==(int)densevals[i]);
      }

      nc = new NewChunk(Vec.T_NUM);
      cc.add2Chunk(nc,0,cc.len());
      nc.values(0, nc._len);
      if (l==1) Assert.assertTrue(cc.isNA(0));
      Assert.assertEquals(vals.length+l+1, nc._sparseLen);
      Assert.assertEquals(vals.length+l+1, nc._len);
      Iterator<NewChunk.Value> it = nc.values(0, vals.length+1+l);
      for (int i = 0; i < vals.length+1+l; ++i) Assert.assertTrue(it.next().rowId0() == i);
      Assert.assertTrue(!it.hasNext());
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], nc.at8(l + i));

      Assert.assertTrue(cc.isNA(vals.length + l));

      ByteArraySupportedChunk cc2 = (ByteArraySupportedChunk) nc.compress();
      Assert.assertEquals(vals.length + 1 + l, cc.len());
      Assert.assertTrue(cc2 instanceof C4Chunk);
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc2.at8(l + i));

      Assert.assertTrue(cc2.isNA(vals.length + l));


      Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }

  @Test public void test_setNA() {
    // Create a vec with one chunk with 15 elements, and set its numbers
    water.Key key = Vec.newKey();
    Vec vec = new Vec(key, Vec.ESPC.rowLayout(key,new long[]{0,15}),1).makeZero();
    int[] vals = new int[]{0, 3, 0, 6, 0, 0, 0, Integer.MIN_VALUE+1, 0, 12, Integer.MAX_VALUE, 32767, 0, 0, 19};
    VecAry.Writer w = new VecAry(vec).open();
    for (int i =0; i<vals.length; ++i) w.set(i, vals[i]);
    w.close();

    ChunkAry cc = vec.chunkForChunkIdx(0);
    assert cc.getChunk(0) instanceof C4Chunk;
    Futures fs = new Futures();
    fs.blockForPending();

    for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at8(i));


    int[] NAs = new int[]{1, 5, 2};
    int[] notNAs = new int[]{0, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14};
    for (int na : NAs) cc.getChunk(0).setNA_impl(na);

    for (int na : NAs) Assert.assertTrue(cc.isNA(na));

    for (int notna : notNAs) Assert.assertTrue(!cc.isNA(notna));


    NewChunk nc = new NewChunk(Vec.T_NUM);
    cc.getChunk(0).add2Chunk(nc,0,cc._len);
    nc.values(0, nc._len);
    Assert.assertEquals(vals.length, nc._sparseLen);
    Assert.assertEquals(vals.length, nc._len);

    Iterator<NewChunk.Value> it = nc.values(0, vals.length);
    for (int i = 0; i < vals.length; ++i) Assert.assertTrue(it.next().rowId0() == i);
    Assert.assertTrue(!it.hasNext());

    for (int na : NAs) Assert.assertTrue(cc.isNA(na));

    for (int notna : notNAs) Assert.assertTrue(!cc.isNA(notna));


    ByteArraySupportedChunk cc2 = (ByteArraySupportedChunk) nc.compress();
    Assert.assertEquals(vals.length, cc._len);
    Assert.assertTrue(cc2 instanceof C4Chunk);
    for (int na : NAs) Assert.assertTrue(cc.isNA(na));

    for (int notna : notNAs) Assert.assertTrue(!cc.isNA(notna));


    Assert.assertTrue(Arrays.equals(cc.getChunk(0).asBytes(), cc2._mem));
    vec.remove();
  }
}
