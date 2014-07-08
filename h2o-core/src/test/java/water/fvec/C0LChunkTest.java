package water.fvec;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;
import water.TestUtil;

import java.util.Arrays;

public class C0LChunkTest extends TestUtil {
  @Test void test_inflate_impl() {
    final int K = 1<<1;
    for (long l : new long[]{Long.MIN_VALUE, Long.MAX_VALUE, 23420384l, 0l, -23423423400023l}) {
      NewChunk nc = new NewChunk(null, 0);
      for (int i=0;i<K;++i) nc.addNum(l,0);

      Chunk cc = nc.compress();
      AssertJUnit.assertTrue(cc instanceof C0LChunk);
      for (int i=0;i<K;++i) AssertJUnit.assertEquals(l, cc.at80(i));

      Chunk cc2 = cc.inflate_impl(new NewChunk(null, 0)).compress();
      AssertJUnit.assertTrue(cc2 instanceof C0LChunk);
      for (int i=0;i<K;++i) AssertJUnit.assertEquals(l, cc2.at80(i));

      AssertJUnit.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }
}
