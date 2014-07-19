package water.fvec;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;
import water.TestUtil;

import java.util.Arrays;

public class C1SChunkTest extends TestUtil {
  @Test
  public void test_inflate_impl() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(null, 0);
      // 0, 0.2, 0.3, 2.54, NA for l==0
      // NA, 0, 0.2, 0.3, 2.54, NA for l==1
      long[] man = new long[]{0, 2, 3, 254};
      int[] exp = new int[]{1, -1, -1, -2};
      if (l==1) nc.addNA();
      for (int i = 0; i < man.length; ++i) nc.addNum(man[i], exp[i]);
      nc.addNA();

      Chunk cc = nc.compress();
      AssertJUnit.assertEquals(man.length + 1 + l, cc.len());
      AssertJUnit.assertTrue(cc instanceof C1SChunk);
      if (l==1) {
        AssertJUnit.assertTrue(cc.isNA0(0));
        AssertJUnit.assertTrue(cc.isNA(0));
      }
      for (int i = 0; i < man.length; ++i) {
        AssertJUnit.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc.at0(l + i), 0);
        AssertJUnit.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc.at(l + i), 0);
      }
      AssertJUnit.assertTrue(cc.isNA0(man.length + l));
      AssertJUnit.assertTrue(cc.isNA(man.length + l));

      nc = cc.inflate_impl(new NewChunk(null, 0));
      AssertJUnit.assertEquals(man.length + 1 + l, nc.len());
      AssertJUnit.assertEquals(man.length + 1 + l, nc.sparseLen());
      if (l==1) {
        AssertJUnit.assertTrue(nc.isNA0(0));
        AssertJUnit.assertTrue(nc.isNA(0));
      }
      for (int i = 0; i < man.length; ++i) {
        AssertJUnit.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) nc.at0(l + i), 0);
        AssertJUnit.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) nc.at(l + i), 0);
      }
      AssertJUnit.assertTrue(nc.isNA0(man.length + l));
      AssertJUnit.assertTrue(nc.isNA(man.length + l));

      Chunk cc2 = nc.compress();
      AssertJUnit.assertEquals(man.length + 1 + l, cc.len());
      AssertJUnit.assertTrue(cc2 instanceof C1SChunk);
      if (l==1) {
        AssertJUnit.assertTrue(cc2.isNA0(0));
        AssertJUnit.assertTrue(cc2.isNA(0));
      }
      for (int i = 0; i < man.length; ++i) {
        AssertJUnit.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc2.at0(l + i), 0);
        AssertJUnit.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc2.at(l + i), 0);
      }
      AssertJUnit.assertTrue(cc2.isNA0(man.length + l));
      AssertJUnit.assertTrue(cc2.isNA(man.length + l));

      AssertJUnit.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }
  @Test public void test_inflate_impl2() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(null, 0);
      long[] man = new long[]{-1228, -997, -9740};
      int[] exp = new int[]{-4, -4, -5};
      if (l==1) nc.addNA();
      for (int i = 0; i < man.length; ++i) nc.addNum(man[i], exp[i]);
      nc.addNA();

      Chunk cc = nc.compress();
      AssertJUnit.assertEquals(man.length + 1 + l, cc.len());
      AssertJUnit.assertTrue(cc instanceof C1SChunk);
      if (l==1) AssertJUnit.assertTrue(cc.isNA0(0));
      for (int i = 0; i < man.length; ++i)
        AssertJUnit.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc.at0(l + i), 0);
      AssertJUnit.assertTrue(cc.isNA0(man.length + l));

      nc = cc.inflate_impl(new NewChunk(null, 0));
      AssertJUnit.assertEquals(man.length + 1 + l, nc.len());
      AssertJUnit.assertEquals(man.length + 1 + l, nc.sparseLen());
      if (l==1) {
        AssertJUnit.assertTrue(nc.isNA0(0));
        AssertJUnit.assertTrue(nc.isNA(0));
      }
      for (int i = 0; i < man.length; ++i) {
        AssertJUnit.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) nc.at0(l + i), 0);
        AssertJUnit.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) nc.at(l + i), 0);
      }
      AssertJUnit.assertTrue(nc.isNA0(man.length + l));
      AssertJUnit.assertTrue(nc.isNA(man.length + l));

      Chunk cc2 = nc.compress();
      AssertJUnit.assertEquals(man.length + 1 + l, cc.len());
      AssertJUnit.assertTrue(cc2 instanceof C1SChunk);
      if (l==1) AssertJUnit.assertTrue(cc2.isNA0(0));
      for (int i = 0; i < man.length; ++i)
        AssertJUnit.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc2.at0(l + i), 0);
      AssertJUnit.assertTrue(cc2.isNA0(man.length + l));

      AssertJUnit.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }
}
