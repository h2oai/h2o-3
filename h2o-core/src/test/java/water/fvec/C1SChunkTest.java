package water.fvec;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;
import water.TestUtil;

import java.util.Arrays;

public class C1SChunkTest extends TestUtil {
  @Test void test_inflate_impl() {
    NewChunk nc = new NewChunk(null, 0);

    // 0, 0.2, 0.3, 2.54, NA
    int[] man = new int[]{0,2,3,254};
    int[] exp = new int[]{1,-1,-1,-2};
    for (int i=0;i<man.length;++i) nc.addNum(man[i],exp[i]);
    nc.addNA();

    Chunk cc = nc.compress();
    AssertJUnit.assertEquals(man.length+1, cc.len());
    AssertJUnit.assertTrue(cc instanceof C1SChunk);
    for (int i=0;i<man.length;++i) AssertJUnit.assertEquals((float)(man[i] * Math.pow(10,exp[i])), (float)cc.at0(i));
    AssertJUnit.assertTrue(cc.isNA0(man.length));

    Chunk cc2 = cc.inflate_impl(new NewChunk(null, 0)).compress();
    AssertJUnit.assertEquals(man.length+1, cc.len());
    AssertJUnit.assertTrue(cc2 instanceof C1SChunk);
    for (int i=0;i<man.length;++i) AssertJUnit.assertEquals((float)(man[i] * Math.pow(10,exp[i])), (float)cc2.at0(i));
    AssertJUnit.assertTrue(cc2.isNA0(man.length));

    AssertJUnit.assertTrue(Arrays.equals(cc._mem, cc2._mem));
  }
}
