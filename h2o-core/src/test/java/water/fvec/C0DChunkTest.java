package water.fvec;

import org.junit.*;

import water.TestUtil;
import java.util.Arrays;

public class C0DChunkTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  @Test
  public void test_inflate_impl() {
    final int K = 1<<16;
    for (Double d : new Double[]{3.14159265358, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MAX_VALUE}) {
      NewChunk nc = new NewChunk(Vec.T_NUM);
      for (int i=0;i<K;++i) nc.addNum(d);
      Assert.assertEquals(K, nc._len);
      Assert.assertEquals(Double.isNaN(d) ? 0: K, nc._sparseLen);
      Assert.assertEquals(K, nc.sparseNA());
      Assert.assertEquals(Double.isNaN(d) ? 0: K, nc._sparseLen);

      int len = nc._len;
      Chunk cc = nc.compress();

      Assert.assertTrue(cc instanceof C0DChunk);
      for (int i=0;i<K;++i) Assert.assertEquals(d, cc.atd(i), Math.ulp(d));


      double[] sparsevals = new double[K];
      int[] sparseids = new int[K];
      cc.asSparseDoubles(sparsevals, sparseids);
      for (int i = 0; i < sparsevals.length; ++i) {
        if (cc.isNA(sparseids[i])) Assert.assertTrue(Double.isNaN(sparsevals[i]));
        else Assert.assertTrue(cc.atd(sparseids[i])==sparsevals[i]);
      }
      double[] densevals = new double[len];
      cc.getDoubles(densevals,0,len);
      for (int i = 0; i < densevals.length; ++i) {
        if (cc.isNA(i)) Assert.assertTrue(Double.isNaN(densevals[i]));
        else Assert.assertTrue(cc.atd(i)==densevals[i]);
      }

      nc = cc.add2Chunk(new NewChunk(Vec.T_NUM),0,len);
      nc.values(0, nc._len);
      Assert.assertEquals(K, nc._len);
      Assert.assertEquals(Double.isNaN(d) ? 0: K, nc._sparseLen);
      Assert.assertEquals(K, nc._sparseLen);
      Assert.assertEquals(Double.isNaN(d) ? 0: K, nc._sparseLen);
      for (int i=0;i<K;++i) Assert.assertEquals(d, nc.atd(i), Math.ulp(d));

      Chunk cc2 = nc.compress();
      Assert.assertEquals(K, len);
      Assert.assertTrue(cc2 instanceof C0DChunk);
      for (int i=0;i<K;++i) Assert.assertEquals(d, cc2.atd(i), Math.ulp(d));
      Assert.assertTrue(Arrays.equals(cc.asBytes(), cc2.asBytes()));
    }
  }
}
