package water.fvec;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;
import water.TestUtil;

import java.util.Arrays;

public class C8ChunkTest extends TestUtil {
  @Test void test_inflate_impl() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(null, 0);

      long[] vals = new long[]{Long.MIN_VALUE+1, Integer.MIN_VALUE, 0, Integer.MAX_VALUE, Long.MAX_VALUE};
      if (l==1) nc.addNA();
      for (long v : vals) nc.addNum(v, 0);
      nc.addNA(); //-9223372036854775808l

      Chunk cc = nc.compress();
      AssertJUnit.assertEquals(vals.length + 1 + l, cc.len());
      AssertJUnit.assertTrue(cc instanceof C8Chunk);
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], cc.at80(l+i));
      AssertJUnit.assertTrue(cc.isNA0(vals.length+l));

      Chunk cc2 = cc.inflate_impl(new NewChunk(null, 0)).compress();
      AssertJUnit.assertEquals(vals.length + 1 + l, cc.len());
      AssertJUnit.assertTrue(cc2 instanceof C8Chunk);
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], cc2.at80(l+i));
      AssertJUnit.assertTrue(cc2.isNA0(vals.length+l));

      AssertJUnit.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }
}
