package water.fvec;

import org.junit.*;

import water.TestUtil;
import java.util.Arrays;

public class C1NChunkTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  @Test
  public void test_inflate_impl() {
    NewChunk nc = new NewChunk(null, 0);

    int[] vals = new int[]{0,1,3,254};
    for (int v : vals) nc.addNum(v,0);

    Chunk cc = nc.compress();
    Assert.assertEquals(vals.length, cc._len);
    Assert.assertTrue(cc instanceof C1NChunk);
    for (int i=0;i<vals.length;++i) Assert.assertEquals(vals[i], cc.at8(i));
    for (int i=0;i<vals.length;++i) Assert.assertEquals(vals[i], cc.at8_abs(i));
    double[] densevals = new double[cc.len()];
    cc.getDoubles(densevals,0,cc.len());
    for (int i = 0; i < densevals.length; ++i) {
      if (cc.isNA(i)) Assert.assertTrue(Double.isNaN(densevals[i]));
      else Assert.assertTrue(cc.at8(i)==(int)densevals[i]);
    }

    nc = cc.inflate_impl(new NewChunk(null, 0));
    nc.values(0, nc._len);
    Assert.assertEquals(vals.length, nc._len);
    Assert.assertEquals(vals.length, nc._sparseLen);
    for (int i=0;i<vals.length;++i) Assert.assertEquals(vals[i], nc.at8(i));
    for (int i=0;i<vals.length;++i) Assert.assertEquals(vals[i], nc.at8_abs(i));

    Chunk cc2 = nc.compress();
    Assert.assertEquals(vals.length, cc._len);
    Assert.assertTrue(cc2 instanceof C1NChunk);
    for (int i=0;i<vals.length;++i) Assert.assertEquals(vals[i], cc2.at8(i));
    for (int i=0;i<vals.length;++i) Assert.assertEquals(vals[i], cc2.at8_abs(i));

    Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
  }
}
