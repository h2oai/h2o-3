package water.fvec;

import org.junit.*;

import water.Futures;
import water.TestUtil;
import java.util.Arrays;
import java.util.Iterator;

public class C1ChunkTest extends TestUtil {
  @Test
  public void test_inflate_impl() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(null, 0);

      int[] vals = new int[]{0, 1, 3, 254};
      if (l==1) nc.addNA();
      for (int v : vals) nc.addNum(v, 0);
      nc.addNA();

      Chunk cc = nc.compress();
      Assert.assertEquals(vals.length + 1 + l, cc.len());
      Assert.assertTrue(cc instanceof C1Chunk);
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at80(l+i));
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at8(l+i));
      Assert.assertTrue(cc.isNA0(vals.length+l));
      Assert.assertTrue(cc.isNA(vals.length+l));

      nc = new NewChunk(null, 0);
      cc.inflate_impl(nc);
      nc.values(0, nc.len());
      if (l==1) Assert.assertTrue(cc.isNA0(0));
      Assert.assertEquals(vals.length+l+1, nc.sparseLen());
      Assert.assertEquals(vals.length+l+1, nc.len());
      Iterator<NewChunk.Value> it = nc.values(0, vals.length+1+l);
      for (int i = 0; i < vals.length+1+l; ++i) Assert.assertTrue(it.next().rowId0() == i);
      Assert.assertTrue(!it.hasNext());
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], nc.at80(l+i));
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], nc.at8(l+i));
      Assert.assertTrue(cc.isNA0(vals.length+l));

      Chunk cc2 = nc.compress();
      Assert.assertEquals(vals.length + 1 + l, cc.len());
      Assert.assertTrue(cc2 instanceof C1Chunk);
      if (l==1) Assert.assertTrue(cc2.isNA0(0));
      if (l==1) Assert.assertTrue(cc2.isNA(0));
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc2.at80(l+i));
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc2.at8(l+i));
      Assert.assertTrue(cc2.isNA0(vals.length+l));
      Assert.assertTrue(cc2.isNA(vals.length+l));

      Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }

  @Test public void test_setNA() {
    // Create a vec with one chunk, and set its numbers
    Vec vec = new Vec(Vec.newKey(), new long[]{0,15}).makeZeros(1,null,null,null,null)[0];
    int[] vals = new int[]{0, 1, 0, 5, 0, 0, 0, 21, 0, 111, 0, 8, 0, 1};
    Vec.Writer w = vec.open();
    for (int i =0; i<vals.length; ++i) w.set(i, vals[i]);
    w.setNA(14); //extra NA to make this a C1Chunk, not a C1NChunk
    w.close();

    Chunk cc = vec.chunkForChunkIdx(0);
    assert cc instanceof C1Chunk;
    Futures fs = new Futures();
    fs.blockForPending();

    for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at80(i));
    for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at8(i));

    int[] NAs = new int[]{1, 5, 2, 14};
    int[] notNAs = new int[]{0, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13};
    for (int na : NAs) cc.setNA0(na);
    for (int na : NAs) cc.setNA(na);
    for (int na : NAs) Assert.assertTrue(cc.isNA0(na));
    for (int na : NAs) Assert.assertTrue(cc.isNA(na));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA0(notna));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA(notna));

    NewChunk nc = new NewChunk(null, 0);
    cc.inflate_impl(nc);
    nc.values(0, nc.len());
    Assert.assertEquals(vals.length+1, nc.sparseLen());
    Assert.assertEquals(vals.length+1, nc.len());

    Iterator<NewChunk.Value> it = nc.values(0, vals.length);
    for (int i = 0; i < vals.length; ++i) Assert.assertTrue(it.next().rowId0() == i);
    Assert.assertTrue(!it.hasNext());

    for (int na : NAs) Assert.assertTrue(cc.isNA0(na));
    for (int na : NAs) Assert.assertTrue(cc.isNA(na));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA0(notna));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA(notna));

    Chunk cc2 = nc.compress();
    Assert.assertEquals(vals.length+1, cc.len());
    Assert.assertTrue(cc2 instanceof C1Chunk);
    for (int na : NAs) Assert.assertTrue(cc.isNA0(na));
    for (int na : NAs) Assert.assertTrue(cc.isNA(na));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA0(notna));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA(notna));

    Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    vec.remove();
  }
}
