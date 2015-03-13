package water.fvec;

import org.junit.*;

import water.TestUtil;
import java.util.Arrays;
import java.util.Iterator;

public class C0LChunkTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  @Test
  public void test_inflate_impl() {
    final int K = 1<<18;
    for (long l : new long[]{Long.MIN_VALUE, Long.MAX_VALUE, 23420384l, 0l, -23423423400023l}) {
      NewChunk nc = new NewChunk(null, 0);
      for (int i=0;i<K;++i) nc.addNum(l,0);
      Assert.assertEquals(K, nc._len);
      if (l != 0l) Assert.assertEquals(l == 0l ? 0 : K, nc.sparseLen()); //special case for sparse length

      Chunk cc = nc.compress();
      Assert.assertEquals(K, cc._len);
      Assert.assertTrue(cc instanceof C0LChunk);
      for (int i=0;i<K;++i) Assert.assertEquals(l, cc.at8(i));

      nc = new NewChunk(null, 0);
      cc.inflate_impl(nc);
      nc.values(0, nc._len);
      Assert.assertEquals(K, nc._len);
      Assert.assertEquals(l == 0 ? 0 : K, nc.sparseLen());

      Iterator<NewChunk.Value> it = nc.values(0, K);
      if (l != 0l) { //if all 0s, then we're already at end
        for (int i = 0; i < K; ++i) Assert.assertTrue(it.next().rowId0() == i);
      }
      Assert.assertTrue(!it.hasNext());
      for (int i = 0; i < K; ++i) Assert.assertEquals(l, nc.at8(i));

      Chunk cc2 = nc.compress();
      Assert.assertEquals(K, cc2._len);
      Assert.assertTrue(cc2 instanceof C0LChunk);
      for (int i=0;i<K;++i) Assert.assertEquals(l, cc2.at8(i));

      Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }
}

