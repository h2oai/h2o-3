package water.fvec;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;
import water.TestUtil;

import java.util.Arrays;

public class CX0ChunkTest extends TestUtil {
  @Test void test_inflate_impl() {
    NewChunk nc = new NewChunk(null, 0);

    int[] vals = new int[]{0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};

    for (int v : vals) nc.addNum(v, 0);

    Chunk cc = nc.compress();
    AssertJUnit.assertEquals(vals.length, cc.len());
    AssertJUnit.assertTrue(cc instanceof CX0Chunk);
    for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], cc.at80(i));

    Chunk cc2 = cc.inflate_impl(new NewChunk(null, 0)).compress();
    AssertJUnit.assertEquals(vals.length , cc.len());
    AssertJUnit.assertTrue(cc2 instanceof CX0Chunk);
    for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], cc2.at80(i));

    AssertJUnit.assertTrue(Arrays.equals(cc._mem, cc2._mem));
  }
}
