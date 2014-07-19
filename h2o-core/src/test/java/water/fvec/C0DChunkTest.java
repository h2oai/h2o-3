package water.fvec;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;
import water.TestUtil;

import java.util.Arrays;

public class C0DChunkTest extends TestUtil {
  @Test
  public void test_inflate_impl() {
    final int K = 1<<16;
    for (Double d : new Double[]{3.14159265358, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MAX_VALUE, Double.NaN}) {
      NewChunk nc = new NewChunk(null, 0);
      for (int i=0;i<K;++i) nc.addNum(d);
      AssertJUnit.assertEquals(K, nc.len());
      AssertJUnit.assertEquals(K, nc.sparseLen());

      Chunk cc = nc.compress();
      AssertJUnit.assertEquals(K, cc.len());
      AssertJUnit.assertTrue(cc instanceof C0DChunk);
      for (int i=0;i<K;++i) AssertJUnit.assertEquals(d, cc.at0(i));
      for (int i=0;i<K;++i) AssertJUnit.assertEquals(d, cc.at(i));

      nc = cc.inflate_impl(new NewChunk(null, 0));
      AssertJUnit.assertEquals(K, nc.len());
      AssertJUnit.assertEquals(K, nc.sparseLen());
      for (int i=0;i<K;++i) AssertJUnit.assertEquals(d, nc.at0(i));
      for (int i=0;i<K;++i) AssertJUnit.assertEquals(d, nc.at(i));

      Chunk cc2 = nc.compress();
      AssertJUnit.assertEquals(K, cc2.len());
      AssertJUnit.assertTrue(cc2 instanceof C0DChunk);
      for (int i=0;i<K;++i) AssertJUnit.assertEquals(d, cc2.at0(i));
      for (int i=0;i<K;++i) AssertJUnit.assertEquals(d, cc2.at(i));

      AssertJUnit.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }
}
