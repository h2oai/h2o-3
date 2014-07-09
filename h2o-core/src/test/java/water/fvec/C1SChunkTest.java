package water.fvec;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;
import water.TestUtil;

import java.util.Arrays;

public class C1SChunkTest extends TestUtil {
  @Test void test_inflate_impl() {
    NewChunk nc = new NewChunk(null, 0);

    for (int l=0; l<1; ++l) {
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
      if (l==1) AssertJUnit.assertTrue(cc.isNA0(0));
      for (int i = 0; i < man.length; ++i)
        AssertJUnit.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc.at0(l + i));
      AssertJUnit.assertTrue(cc.isNA0(man.length + l));

      Chunk cc2 = cc.inflate_impl(new NewChunk(null, 0)).compress();
      AssertJUnit.assertEquals(man.length + 1 + l, cc.len());
      AssertJUnit.assertTrue(cc2 instanceof C1SChunk);
      if (l==1) AssertJUnit.assertTrue(cc2.isNA0(0));
      for (int i = 0; i < man.length; ++i)
        AssertJUnit.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc2.at0(l + i));
      AssertJUnit.assertTrue(cc2.isNA0(man.length + l));

      AssertJUnit.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }
  @Test void test_inflate_impl2() {
    NewChunk nc = new NewChunk(null, 0);

    for (int l=0; l<1; ++l) {
      // -22.8, 2, 0.3, 2.6, NA for l==0
      // NA, -22.8, 2, 0.3, 2.6, NA for l==0
      long[] man = new long[]{-228, 2, 3, 26}; //max range: 255 numbers in steps of 0.1 + NA
      int[] exp = new int[]{-1, 0, -1, -1};
      if (l==1) nc.addNA();
      for (int i = 0; i < man.length; ++i) nc.addNum(man[i], exp[i]);
      nc.addNA();

      Chunk cc = nc.compress();
      AssertJUnit.assertEquals(man.length + 1 + l, cc.len());
      AssertJUnit.assertTrue(cc instanceof C1SChunk);
      if (l==1) AssertJUnit.assertTrue(cc.isNA0(0));
      for (int i = 0; i < man.length; ++i)
        AssertJUnit.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc.at0(l + i));
      AssertJUnit.assertTrue(cc.isNA0(man.length + l));

      Chunk cc2 = cc.inflate_impl(new NewChunk(null, 0)).compress();
      AssertJUnit.assertEquals(man.length + 1 + l, cc.len());
      AssertJUnit.assertTrue(cc2 instanceof C1SChunk);
      if (l==1) AssertJUnit.assertTrue(cc2.isNA0(0));
      for (int i = 0; i < man.length; ++i)
        AssertJUnit.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc2.at0(l + i));
      AssertJUnit.assertTrue(cc2.isNA0(man.length + l));

      AssertJUnit.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }
}
