package water.fvec;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;
import water.TestUtil;

import java.util.Arrays;

public class C4SChunkTest extends TestUtil {
  @Test void test_inflate_impl() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(null, 0);

      // -2.147483647, 0, 0.0000215, 2.147583647, NA for l==0
      // NA, -2.147483647, 0, 0.0000215, 2.147583647, NA for l==1
      long[] man = new long[]{-2147483647, 0, 215, 188001238, 2147483647};
      int[] exp = new int[]{-9, 1, -6, -8, -9};
      if (l==1) nc.addNA(); //-2147483648
      for (int i = 0; i < man.length; ++i) nc.addNum(man[i], exp[i]);
      nc.addNA();

      Chunk cc = nc.compress();
      AssertJUnit.assertEquals(man.length + 1 + l, cc.len());
      AssertJUnit.assertTrue(cc instanceof C4SChunk);
      if (l==1) AssertJUnit.assertTrue(cc.isNA0(0));
      for (int i = 0; i < man.length; ++i)
        AssertJUnit.assertTrue("Expected: " + man[i] * Math.pow(10, exp[i]) + ", but is " + cc.at0(l+i), Math.abs((man[i] * Math.pow(10, exp[i])) - cc.at0(l+i)) < 1e-10);
      AssertJUnit.assertTrue(cc.isNA0(man.length + l));

      Chunk cc2 = cc.inflate_impl(new NewChunk(null, 0)).compress();
      AssertJUnit.assertEquals(man.length + 1 + l, cc.len());
      if (l==1) AssertJUnit.assertTrue(cc2.isNA0(0));
      for (int i = 0; i < man.length; ++i)
        AssertJUnit.assertTrue("Expected: " + man[i] * Math.pow(10, exp[i]) + ", but is " + cc2.at0(l+i), Math.abs((man[i] * Math.pow(10, exp[i])) - cc2.at0(l+i)) < 1e-10);
      AssertJUnit.assertTrue(cc2.isNA0(man.length + l));
      AssertJUnit.assertTrue(cc2 instanceof C4SChunk);

      AssertJUnit.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }
  @Test void test_inflate_impl2() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(null, 0);

      // different bias and scale than above, but still using the full 32-bit range (~4.29 billion different integers from -8.1b to -3.8b)
      long[] man = new long[]{-814748364700000l, -5999999700000l, -58119987600000l, -385251635300000l};
      int[] exp = new int[]{-19, -17, -18, -19};

      if (l==1) nc.addNA(); //-2147483648
      for (int i = 0; i < man.length; ++i) nc.addNum(man[i], exp[i]);
      nc.addNA();

      Chunk cc = nc.compress();
      AssertJUnit.assertEquals(man.length + 1 + l, cc.len());
      AssertJUnit.assertTrue(cc instanceof C4SChunk);
      if (l==1) AssertJUnit.assertTrue(cc.isNA0(0));
      for (int i = 0; i < man.length; ++i)
        AssertJUnit.assertTrue("Expected: " + man[i] * Math.pow(10, exp[i]) + ", but is " + cc.at0(l+i), Math.abs((man[i] * Math.pow(10, exp[i])) - cc.at0(l+i)) < 1e-10);
      AssertJUnit.assertTrue(cc.isNA0(man.length + l));

      Chunk cc2 = cc.inflate_impl(new NewChunk(null, 0)).compress();
      AssertJUnit.assertEquals(man.length + 1 + l, cc.len());
      if (l==1) AssertJUnit.assertTrue(cc2.isNA0(0));
      for (int i = 0; i < man.length; ++i)
      AssertJUnit.assertTrue("Expected: " + man[i] * Math.pow(10, exp[i]) + ", but is " + cc2.at0(l+i), Math.abs((man[i] * Math.pow(10, exp[i])) - cc2.at0(l+i)) < 1e-10);
      AssertJUnit.assertTrue(cc2.isNA0(man.length + l));
      AssertJUnit.assertTrue(cc2 instanceof C4SChunk);

      AssertJUnit.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }
}
