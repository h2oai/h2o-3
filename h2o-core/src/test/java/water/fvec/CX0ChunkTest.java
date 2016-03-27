package water.fvec;

import org.junit.*;

import water.TestUtil;
import java.util.Arrays;
import java.util.Iterator;

public class CX0ChunkTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  @Test
  public void test_inflate_impl() {
    NewChunk nc = new NewChunk(null, 0);

    int[] vals = new int[]{0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};

    for (int v : vals) nc.addNum(v, 0);

    Chunk cc = nc.compress();
    Assert.assertEquals(vals.length, cc._len);
    Assert.assertTrue(cc instanceof CX0Chunk);
    for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at8(i));

    nc = new NewChunk(null, 0);
    cc.inflate_impl(nc);
    nc.values(0, nc._len);
    Assert.assertEquals(vals.length , nc._len);
    Assert.assertEquals(2, nc._sparseLen);
    Iterator<NewChunk.Value> it = nc.values(0, vals.length);
    Assert.assertTrue(it.next().rowId0() == 3);
    Assert.assertTrue(it.next().rowId0() == 101);
    Assert.assertTrue(!it.hasNext());
    for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], nc.at8(i));
    for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], nc.at8_abs(i));

    double[] sparsevals = new double[cc.sparseLenZero()];
    int[] sparseids = new int[cc.sparseLenZero()];
    cc.asSparseDoubles(sparsevals, sparseids);
    for (int i = 0; i < sparsevals.length; ++i) {
      Assert.assertTrue(cc.at8(sparseids[i])==(int)sparsevals[i]);
    }
    double[] densevals = new double[cc.len()];
    cc.getDoubles(densevals,0,cc.len());
    for (int i = 0; i < densevals.length; ++i) {
      if (cc.isNA(i)) Assert.assertTrue(Double.isNaN(densevals[i]));
      else Assert.assertTrue(cc.at8(i)==(int)densevals[i]);
    }

    Chunk cc2 = nc.compress();
    Assert.assertEquals(vals.length , cc._len);
    Assert.assertTrue(cc2 instanceof CX0Chunk);
    for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc2.at8(i));
    for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc2.at8_abs(i));

    Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
  }
}
