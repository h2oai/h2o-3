package water.fvec;

import org.junit.*;

import water.Futures;
import water.Key;
import water.TestUtil;
import java.util.Arrays;
import java.util.Iterator;

public class C2ChunkTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  @Test
  public void test_inflate_impl() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(Vec.T_NUM);

      int[] vals = new int[]{-32767, 0, 32767};
      if (l==1) nc.addNA();
      for (int v : vals) nc.addNum(v, 0);
      nc.addNA(); //-32768

      C2Chunk cc = (C2Chunk) nc.compress();
      Assert.assertEquals(vals.length + 1 + l, cc.len());
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

      C2Chunk cc2 = (C2Chunk) nc.compress();
      Assert.assertEquals(vals.length + 1 + l, cc.len());

      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc2.at8(l + i));

      Assert.assertTrue(cc2.isNA(vals.length + l));


      Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }

  @Test public void test_setNA() {
    // Create a vec with one chunk with 15 elements, and set its numbers
    Key key= Vec.newKey();
    Vec vec = new Vec(key, Vec.ESPC.rowLayout(key, new long[]{0,15}),1).makeZero();
    int[] vals = new int[]{0, 3, 0, 6, 0, 0, 0, -32767, 0, 12, 234, 32767, 0, 0, 19};
    VecAry.Writer w = new VecAry(vec).open();
    for (int i =0; i<vals.length; ++i) w.set(i, vals[i]);
    w.close();

    ChunkAry cc = vec.chunkForChunkIdx(0);
    assert cc.getChunk(0) instanceof C2Chunk;
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

    C2Chunk cc2 = (C2Chunk) nc.compress();
    Assert.assertEquals(vals.length, cc._len);
    for (int na : NAs) Assert.assertTrue(cc.isNA(na));
    for (int notna : notNAs) Assert.assertTrue(!cc.isNA(notna));
    Assert.assertTrue(Arrays.equals(((ByteArraySupportedChunk)cc.getChunk(0))._mem, cc2._mem));
    vec.remove();
  }
}
