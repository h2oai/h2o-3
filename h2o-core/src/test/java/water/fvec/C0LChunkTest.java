package water.fvec;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;
import water.TestUtil;

import java.util.Arrays;
import java.util.Iterator;

public class C0LChunkTest extends TestUtil {
  @Test
  public void test_inflate_impl() {
    final int K = 1<<18;
    for (long l : new long[]{Long.MIN_VALUE, Long.MAX_VALUE, 23420384l, 0l, -23423423400023l}) {
      NewChunk nc = new NewChunk(null, 0);
      for (int i=0;i<K;++i) nc.addNum(l,0);
      AssertJUnit.assertEquals(K, nc.len());
      if (l != 0l) AssertJUnit.assertEquals(l == 0l ? 0 : K, nc.sparseLen()); //special case for sparse length

      Chunk cc = nc.compress();
      AssertJUnit.assertEquals(K, cc.len());
      AssertJUnit.assertTrue(cc instanceof C0LChunk);
      for (int i=0;i<K;++i) AssertJUnit.assertEquals(l, cc.at80(i));

      nc = new NewChunk(null, 0);
      cc.inflate_impl(nc);
      AssertJUnit.assertEquals(K, nc.len());
      AssertJUnit.assertEquals(l == 0 ? 0 : K, nc.sparseLen());

      Iterator<NewChunk.Value> it = nc.values(0, K);
      if (l != 0l) { //if all 0s, then we're already at end
        for (int i = 0; i < K; ++i) AssertJUnit.assertTrue(it.next().rowId0() == i);
      }
      AssertJUnit.assertTrue(!it.hasNext());
      for (int i = 0; i < K; ++i) AssertJUnit.assertEquals(l, nc.at80(i));

      Chunk cc2 = nc.compress();
      AssertJUnit.assertEquals(K, cc2.len());
      AssertJUnit.assertTrue(cc2 instanceof C0LChunk);
      for (int i=0;i<K;++i) AssertJUnit.assertEquals(l, cc2.at80(i));

      AssertJUnit.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }
}

