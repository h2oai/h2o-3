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
      Chunk cc = nc.compress();
      AssertJUnit.assertTrue(cc instanceof C0DChunk);
      Chunk cc2 = cc.inflate_impl(new NewChunk(null, 0)).compress();
      AssertJUnit.assertTrue(cc2 instanceof C0DChunk);
      AssertJUnit.assertTrue(Arrays.equals(cc._mem, cc2._mem));
      for (int i=0;i<K;++i) AssertJUnit.assertEquals(d, cc2.at0(i));
    }
  }
}
