package water.fvec;

import org.junit.*;

import water.TestUtil;
import java.util.Arrays;

public class C1NChunkTest extends TestUtil {
  @Test
  public void test_inflate_impl() {
    NewChunk nc = new NewChunk(null, 0);

    int[] vals = new int[]{0,1,3,254};
    for (int v : vals) nc.addNum(v,0);

    Chunk cc = nc.compress();
    Assert.assertEquals(vals.length, cc._len);
    Assert.assertTrue(cc instanceof C1NChunk);
    for (int i=0;i<vals.length;++i) Assert.assertEquals(vals[i], cc.at80(i));
    for (int i=0;i<vals.length;++i) Assert.assertEquals(vals[i], cc.at8(i));

    nc = cc.inflate_impl(new NewChunk(null, 0));
    nc.values(0, nc._len);
    Assert.assertEquals(vals.length, nc._len);
    Assert.assertEquals(vals.length, nc.sparseLen());
    for (int i=0;i<vals.length;++i) Assert.assertEquals(vals[i], nc.at80(i));
    for (int i=0;i<vals.length;++i) Assert.assertEquals(vals[i], nc.at8(i));

    Chunk cc2 = nc.compress();
    Assert.assertEquals(vals.length, cc._len);
    Assert.assertTrue(cc2 instanceof C1NChunk);
    for (int i=0;i<vals.length;++i) Assert.assertEquals(vals[i], cc2.at80(i));
    for (int i=0;i<vals.length;++i) Assert.assertEquals(vals[i], cc2.at8(i));

    Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
  }
}
