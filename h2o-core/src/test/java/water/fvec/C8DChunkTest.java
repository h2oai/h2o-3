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

      Chunk cc = nc.compress();
      Assert.assertEquals(vals.length + 1 + l, cc._len);
      Assert.assertTrue(cc instanceof C8DChunk);
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.atd(l + i), Math.ulp(vals[i]));
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at_abs(l + i), Math.ulp(vals[i]));
      Assert.assertTrue(cc.isNA(vals.length + l));
      Assert.assertTrue(cc.isNA_abs(vals.length + l));

      nc = cc.inflate_impl(new NewChunk(null, 0));
      nc.values(0, nc._len);
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
    Vec vec = new Vec(Vec.newKey(), new long[]{0,15}).makeZero();
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
    cc.inflate_impl(nc);
    nc.values(0, nc._len);
    Assert.assertEquals(vals.length, nc.sparseLen());
    Assert.assertEquals(vals.length, nc._len);

    Iterator<NewChunk.Value> it = nc.values(0, vals.length);
    for (int i = 0; i < vals.length; ++i) Assert.assertTrue(it.next().rowId0() == i);
    Assert.assertTrue(!it.hasNext());

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
}
