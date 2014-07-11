package water.fvec;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;
import water.TestUtil;

import java.util.Arrays;

public class CXDChunkTest extends TestUtil {
  @Test void test_inflate_impl() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(null, 0);

      double[] vals = new double[]{0, 0, 0, Double.MAX_VALUE, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
              0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
              0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Double.MIN_VALUE, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
              0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

      if (l==1) nc.addNA();
      for (double v : vals) nc.addNum(v);
      nc.addNA();

      Chunk cc = nc.compress();
      AssertJUnit.assertEquals(vals.length+1+l, cc.len());
      AssertJUnit.assertTrue(cc instanceof CXDChunk);
      if (l==1) AssertJUnit.assertTrue(cc.isNA0(0));
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], cc.at0(i+l));
      AssertJUnit.assertTrue(cc.isNA0(vals.length+l));

      Chunk cc2 = cc.inflate_impl(new NewChunk(null, 0)).compress();
      AssertJUnit.assertEquals(vals.length+1+l, cc.len());
      AssertJUnit.assertTrue(cc2 instanceof CXDChunk);
      if (l==1) AssertJUnit.assertTrue(cc2.isNA0(0));
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], cc2.at0(i+l));
      AssertJUnit.assertTrue(cc2.isNA0(vals.length+l));

      AssertJUnit.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }
}
