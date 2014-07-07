package water.fvec;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;
import water.TestUtil;
import java.util.Arrays;

public class C1ChunkTest extends TestUtil {
  @Test void test_inflate_impl() {
    NewChunk nc = new NewChunk(null, 0);
    nc.addNum(0,0);
    nc.addNum(1,0);
    nc.addNum(3,0);
    nc.addNum(254,0);
    nc.addNA();
    Chunk cc = nc.compress();
    AssertJUnit.assertTrue(cc instanceof C1Chunk);
    NewChunk nc2 = new NewChunk(null, 0);
    nc2 = cc.inflate_impl(nc2);
    Chunk cc2 = nc2.compress();
    AssertJUnit.assertTrue(cc2 instanceof C1Chunk);
    AssertJUnit.assertTrue(Arrays.equals(cc._mem, cc2._mem));
  }
}
