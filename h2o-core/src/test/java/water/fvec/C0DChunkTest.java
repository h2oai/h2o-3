package water.fvec;

import org.junit.*;

import water.TestUtil;
import java.util.Arrays;

public class C0DChunkTest extends TestUtil {
  @Test
  public void test_inflate_impl() {
    final int K = 1<<16;
    for (Double d : new Double[]{3.14159265358, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MAX_VALUE, Double.NaN}) {
      NewChunk nc = new NewChunk(null, 0);
      for (int i=0;i<K;++i) nc.addNum(d);
      Assert.assertEquals(K, nc.len());
      Assert.assertEquals(K, nc.sparseLen());

      Chunk cc = nc.compress();
      Assert.assertEquals(K, cc.len());
      Assert.assertTrue(cc instanceof C0DChunk);
      for (int i=0;i<K;++i) Assert.assertEquals(d, cc.at0(i), Math.ulp(d));
      for (int i=0;i<K;++i) Assert.assertEquals(d, cc.at (i), Math.ulp(d));

      nc = cc.inflate_impl(new NewChunk(null, 0));
      nc.values(0, nc.len());
      Assert.assertEquals(K, nc.len());
      Assert.assertEquals(K, nc.sparseLen());
      for (int i=0;i<K;++i) Assert.assertEquals(d, nc.at0(i), Math.ulp(d));
      for (int i=0;i<K;++i) Assert.assertEquals(d, nc.at (i), Math.ulp(d));

      Chunk cc2 = nc.compress();
      Assert.assertEquals(K, cc2.len());
      Assert.assertTrue(cc2 instanceof C0DChunk);
      for (int i=0;i<K;++i) Assert.assertEquals(d, cc2.at0(i), Math.ulp(d));
      for (int i=0;i<K;++i) Assert.assertEquals(d, cc2.at (i), Math.ulp(d));

      Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }
}
