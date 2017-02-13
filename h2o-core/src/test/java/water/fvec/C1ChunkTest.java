package water.fvec;

import org.junit.*;

import water.Futures;
import water.Key;
import water.TestUtil;
import java.util.Arrays;
import java.util.Iterator;

public class C1ChunkTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  @Test
  public void test_inflate_impl() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(Vec.T_NUM);

      int[] vals = new int[]{0, 1, 3, 254};
      if (l==1) nc.addNA();
      for (int v : vals) nc.addNum(v, 0);
      nc.addNA();

      C1Chunk c1c = (C1Chunk) nc.compress();
      Assert.assertEquals(vals.length + 1 + l, c1c._mem.length);
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], c1c.at8(l + i));

      Assert.assertTrue(c1c.isNA(vals.length + l));

      double[] densevals = new double[c1c._mem.length];
      c1c.getDoubles(densevals,0,c1c._mem.length);
      for (int i = 0; i < densevals.length; ++i) {
        if (c1c.isNA(i)) Assert.assertTrue(Double.isNaN(densevals[i]));
        else Assert.assertTrue(c1c.at8(i)==(int)densevals[i]);
      }

      nc = new NewChunk(Vec.T_NUM);
      c1c.add2Chunk(nc,0,c1c._mem.length);
      nc.values(0, nc._len);
      if (l==1) Assert.assertTrue(c1c.isNA(0));
      Assert.assertEquals(vals.length+l+1, nc._sparseLen);
      Assert.assertEquals(vals.length+l+1, nc._len);
      Iterator<NewChunk.Value> it = nc.values(0, vals.length+1+l);
      for (int i = 0; i < vals.length+1+l; ++i) Assert.assertTrue(it.next().rowId0() == i);
      Assert.assertTrue(!it.hasNext());
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], nc.at8(l + i));

      Assert.assertTrue(c1c.isNA(vals.length + l));

      C1Chunk cc2 = (C1Chunk) nc.compress();
      Assert.assertEquals(vals.length + 1 + l, cc2._mem.length);
      if (l==1) Assert.assertTrue(cc2.isNA(0));

      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc2.at8(l + i));

      Assert.assertTrue(cc2.isNA(vals.length + l));

      Assert.assertTrue(Arrays.equals(c1c._mem, cc2._mem));
    }
  }

  @Test public void test_setNA() {
    // Create a vec with one chunk, and set its numbers
    Key key = Vec.newKey();
    Vec vec = new Vec(key, Vec.ESPC.rowLayout(key, new long[]{0,15}),1).makeZero();
    int[] vals = new int[]{0, 1, 0, 5, 0, 0, 0, 21, 0, 111, 0, 8, 0, 1};
    VecAry.Writer w = new VecAry(vec).open();
    for (int i =0; i<vals.length; ++i) w.set(i, vals[i]);
    w.setNA(14); //extra NA to make this a C1Chunk, not a C1NChunk
    w.close();

    ChunkAry cc = vec.chunkForChunkIdx(0);
    assert cc.getChunk(0) instanceof C1Chunk;
    Futures fs = new Futures();
    fs.blockForPending();

    for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at8(i));


    int[] NAs = new int[]{1, 5, 2, 14};
    int[] notNAs = new int[]{0, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13};
    for (int na : NAs) cc.setNA(na,0);

    for (int na : NAs) Assert.assertTrue(cc.isNA(na));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA(notna));

    NewChunk nc = new NewChunk(Vec.T_NUM);
    cc.getChunk(0).add2Chunk(nc,0,vals.length);
    nc.values(0, nc._len);
    Assert.assertEquals(vals.length+1, nc._sparseLen);
    Assert.assertEquals(vals.length+1, nc._len);

    Iterator<NewChunk.Value> it = nc.values(0, vals.length);
    for (int i = 0; i < vals.length; ++i) Assert.assertTrue(it.next().rowId0() == i);
    Assert.assertTrue(!it.hasNext());

    for (int na : NAs) Assert.assertTrue(cc.isNA(na));

    for (int notna : notNAs) Assert.assertTrue(!cc.isNA(notna));


    C1Chunk cc2 = (C1Chunk) nc.compress();
    Assert.assertEquals(vals.length+1, cc._len);
    for (int na : NAs) Assert.assertTrue(cc.isNA(na));

    for (int notna : notNAs) Assert.assertTrue(!cc.isNA(notna));

    Assert.assertTrue(Arrays.equals(((ByteArraySupportedChunk)cc.getChunk(0))._mem, cc2._mem));
    vec.remove();
  }
}
