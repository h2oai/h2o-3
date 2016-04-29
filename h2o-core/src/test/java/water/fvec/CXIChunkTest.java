package water.fvec;

import org.junit.*;

import water.TestUtil;
import java.util.Arrays;
import java.util.Iterator;

public class CXIChunkTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  @Test
  public void test_inflate_impl() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(null, 0);

      int[] vals = new int[]{0, 0, 0, Integer.MAX_VALUE, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
              0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
              0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Integer.MIN_VALUE+1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
              0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

      if (l==1) nc.addNA();
      for (int v : vals) nc.addNum(v, 0);
      nc.addNA();
      nc.addZeros(40000);
      int pos1 = nc.len();
      nc.addNum(123,0);
      int maxLen = l == 0?65535-1:65535*2;
      nc.addZeros(maxLen - nc._len - 1);
      nc.addNum(456,0);
      Chunk cc = nc.compress();
      Assert.assertEquals(maxLen, cc._len);
      Assert.assertEquals(((CXIChunk)cc)._ridsz,l == 0?2:4);
      Assert.assertTrue(cc instanceof CXIChunk);
      if (l==1) {
        Assert.assertTrue(cc.isNA(0));
        Assert.assertTrue(cc.isNA_abs(0));
      }
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at8(i + l));
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.at8_abs(i + l));
      Assert.assertTrue(cc.isNA(vals.length + l));
      Assert.assertTrue(cc.isNA_abs(vals.length + l));
      Assert.assertEquals(cc.at8(pos1),123);
      Assert.assertEquals(cc.at8(maxLen-1),456);

      double[] sparsevals = new double[cc.sparseLenZero()];
      int[] sparseids = new int[cc.sparseLenZero()];
      cc.asSparseDoubles(sparsevals, sparseids);
      for (int i = 0; i < sparsevals.length; ++i) {
        if (cc.isNA(sparseids[i])) Assert.assertTrue(Double.isNaN(sparsevals[i]));
        else Assert.assertTrue(cc.at8(sparseids[i])==(int)sparsevals[i]);
      }
      double[] densevals = new double[cc.len()];
      cc.getDoubles(densevals,0,cc.len());
      for (int i = 0; i < densevals.length; ++i) {
        if (cc.isNA(i)) Assert.assertTrue(Double.isNaN(densevals[i]));
        else Assert.assertTrue(cc.at8(i)==(int)densevals[i]);
      }

      nc = new NewChunk(null, 0);
      cc.inflate_impl(nc);
      Assert.assertEquals(maxLen, nc._len);
      Assert.assertEquals(2+2+1+l, nc._sparseLen);
      Iterator<NewChunk.Value> it = nc.values(0, vals.length+1+l);
      if (l==1) Assert.assertTrue(it.next().rowId0() == 0);
      Assert.assertTrue(it.next().rowId0() == 3+l);
      Assert.assertTrue(it.next().rowId0() == 101+l);
      Assert.assertTrue(it.next().rowId0() == vals.length+l);
      Assert.assertTrue(!it.hasNext());
      if (l==1) {
        Assert.assertTrue(nc.isNA(0));
        Assert.assertTrue(nc.isNA_abs(0));
      }
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], nc.at8(l + i));
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], nc.at8_abs(l + i));
      Assert.assertTrue(nc.isNA(vals.length + l));
      Assert.assertTrue(nc.isNA_abs(vals.length + l));

      Chunk cc2 = nc.compress();
      Assert.assertEquals(maxLen, cc._len);
      Assert.assertTrue(cc2 instanceof CXIChunk);
      if (l==1) {
        Assert.assertTrue(cc2.isNA(0));
        Assert.assertTrue(cc2.isNA_abs(0));
      }
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc2.at8(i + l));
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc2.at8_abs(i + l));
      Assert.assertTrue(cc2.isNA(vals.length + l));
      Assert.assertTrue(cc2.isNA_abs(vals.length + l));

      Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }
}
