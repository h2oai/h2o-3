package water.fvec;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;
import water.TestUtil;
import water.util.Log;

import java.util.Arrays;

public class C0DChunkTest extends TestUtil {
  @Test void test_inflate_impl() {
    final int K = 1<<16;
    for (Double d : new Double[]{3.14159265358, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MAX_VALUE, Double.NaN}) {
      NewChunk nc = new NewChunk(null, 0);
      for (int i=0;i<K;++i) nc.addNum(d);
      AssertJUnit.assertEquals(K, nc.len());

      Chunk cc = nc.compress();
      AssertJUnit.assertEquals(K, cc.len());
      AssertJUnit.assertTrue(cc instanceof C0DChunk);
      for (int i=0;i<K;++i) AssertJUnit.assertEquals(d, cc.at0(i));

      Chunk cc2 = cc.inflate_impl(new NewChunk(null, 0)).compress();
      AssertJUnit.assertEquals(K, cc2.len());
      AssertJUnit.assertTrue(cc2 instanceof C0DChunk);
      for (int i=0;i<K;++i) AssertJUnit.assertEquals(d, cc2.at0(i));

      AssertJUnit.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }
}
