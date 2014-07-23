package water.fvec;

import org.junit.*;

import water.TestUtil;
import java.util.Arrays;

public class C2SChunkTest extends TestUtil {
  @Test
  public void test_inflate_impl() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(null, 0);

      // -32.767, 0.34, 0, 32.767, NA for l==0
      // NA, -32.767, 0.34, 0, 32.767, NA for l==1
      long[] man = new long[]{-32767, 34, 0, 32767};
      int[] exp = new int[]{-3, -2, 1, -3};
      if (l==1) nc.addNA(); //-32768
      for (int i = 0; i < man.length; ++i) nc.addNum(man[i], exp[i]);
      nc.addNA();

      Chunk cc = nc.compress();
      Assert.assertEquals(man.length + 1 + l, cc.len());
      Assert.assertTrue(cc instanceof C2SChunk);
      if (l==1) {
        Assert.assertTrue(cc.isNA0(0));
        Assert.assertTrue(cc.isNA(0));
      }
      for (int i = 0; i < man.length; ++i) {
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc.at0(l + i), 0);
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc.at(l + i), 0);
      }
      Assert.assertTrue(cc.isNA0(man.length + l));
      Assert.assertTrue(cc.isNA(man.length + l));

      nc = cc.inflate_impl(new NewChunk(null, 0));
      Assert.assertEquals(man.length + 1 + l, nc.len());
      Assert.assertEquals(man.length + 1 + l, nc.sparseLen());
      if (l==1) {
        Assert.assertTrue(nc.isNA0(0));
        Assert.assertTrue(nc.isNA(0));
      }
      for (int i = 0; i < man.length; ++i) {
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) nc.at0(l + i), 0);
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) nc.at(l + i), 0);
      }
      Assert.assertTrue(nc.isNA0(man.length + l));
      Assert.assertTrue(nc.isNA(man.length + l));

      Chunk cc2 = nc.compress();
      Assert.assertEquals(man.length + 1 + l, cc.len());
      if (l==1) {
        Assert.assertTrue(cc2.isNA0(0));
        Assert.assertTrue(cc2.isNA(0));
      }
      for (int i = 0; i < man.length; ++i) {
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc2.at0(l + i), 0);
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc2.at(l + i), 0);
      }
      Assert.assertTrue(cc2.isNA0(man.length + l));
      Assert.assertTrue(cc2.isNA(man.length + l));
      Assert.assertTrue(cc2 instanceof C2SChunk);

      Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }
  @Test public void test_inflate_impl2() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(null, 0);

      long[] man = new long[]{-12767, 34, 0, 52767};
      int[] exp = new int[]{-3, -2, 1, -3};
      if (l==1) nc.addNA(); //-32768
      for (int i = 0; i < man.length; ++i) nc.addNum(man[i], exp[i]);
      nc.addNA();

      Chunk cc = nc.compress();
      Assert.assertEquals(man.length + 1 + l, cc.len());
      Assert.assertTrue(cc instanceof C2SChunk);
      if (l==1) {
        Assert.assertTrue(cc.isNA0(0));
        Assert.assertTrue(cc.isNA(0));
      }
      for (int i = 0; i < man.length; ++i) {
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc.at0(l + i), 0);
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc.at(l + i), 0);
      }
      Assert.assertTrue(cc.isNA0(man.length + l));

      nc = cc.inflate_impl(new NewChunk(null, 0));
      Assert.assertEquals(man.length + 1 + l, nc.len());
      Assert.assertEquals(man.length + 1 + l, nc.sparseLen());
      if (l==1) {
        Assert.assertTrue(nc.isNA0(0));
        Assert.assertTrue(nc.isNA(0));
      }
      for (int i = 0; i < man.length; ++i) {
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) nc.at0(l + i), 0);
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) nc.at(l + i), 0);
      }
      Assert.assertTrue(nc.isNA0(man.length + l));
      Assert.assertTrue(nc.isNA(man.length + l));

      Chunk cc2 = nc.compress();
      Assert.assertEquals(man.length + 1 + l, cc.len());
      if (l==1) {
        Assert.assertTrue(cc2.isNA0(0));
        Assert.assertTrue(cc2.isNA(0));
      }
      for (int i = 0; i < man.length; ++i) {
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc2.at0(l + i), 0);
        Assert.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc2.at(l + i), 0);
      }
      Assert.assertTrue(cc2.isNA0(man.length + l));
      Assert.assertTrue(cc2.isNA(man.length + l));
      Assert.assertTrue(cc2 instanceof C2SChunk);

      Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }
}
