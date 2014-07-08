package water.fvec;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;
import water.TestUtil;

import java.util.Arrays;

public class C2SChunkTest extends TestUtil {
  @Test void test_inflate_impl() {
    for (int l=0; l<1; ++l) {
      NewChunk nc = new NewChunk(null, 0);

      // -32.767, 0.34, 0, 32.767, NA for l==0
      // NA, -32.767, 0.34, 0, 32.767, NA for l==1
      int[] man = new int[]{-32767, 34, 0, 32767};
      int[] exp = new int[]{-3, -2, 1, -3};
      if (l==1) nc.addNA(); //-32768
      for (int i = 0; i < man.length; ++i) nc.addNum(man[i], exp[i]);
      nc.addNA();

      Chunk cc = nc.compress();
      AssertJUnit.assertEquals(man.length + 1 + l, cc.len());
      AssertJUnit.assertTrue(cc instanceof C2SChunk);
      if (l==1) AssertJUnit.assertTrue(cc.isNA0(0));
      for (int i = 0; i < man.length; ++i)
        AssertJUnit.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc.at0(l + i));
      AssertJUnit.assertTrue(cc.isNA0(man.length + l));

      Chunk cc2 = cc.inflate_impl(new NewChunk(null, 0)).compress();
      AssertJUnit.assertEquals(man.length + 1 + l, cc.len());
      if (l==1) AssertJUnit.assertTrue(cc2.isNA0(0));
      for (int i = 0; i < man.length; ++i)
        AssertJUnit.assertEquals((float) (man[i] * Math.pow(10, exp[i])), (float) cc2.at0(l + i));
      AssertJUnit.assertTrue(cc2.isNA0(man.length + l));
      AssertJUnit.assertTrue(cc2 instanceof C2SChunk);

      AssertJUnit.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }
}
