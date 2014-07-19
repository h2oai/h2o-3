package water.fvec;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;
import water.TestUtil;

import java.util.Arrays;

public class C8DChunkTest extends TestUtil {
  @Test
  public void test_inflate_impl() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(null, 0);

      double[] vals = new double[]{Double.NaN, Double.MIN_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -3.1415926e-118, 0, 23423423.234234234, 0.00103E217, Double.MAX_VALUE};
      if (l==1) nc.addNA();
      for (double v : vals) nc.addNum(v);
      nc.addNA(); //-9223372036854775808l

      Chunk cc = nc.compress();
      AssertJUnit.assertEquals(vals.length + 1 + l, cc.len());
      AssertJUnit.assertTrue(cc instanceof C8DChunk);
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], cc.at0(l+i));
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], cc.at(l+i));
      AssertJUnit.assertTrue(cc.isNA0(vals.length+l));
      AssertJUnit.assertTrue(cc.isNA(vals.length+l));

      nc = cc.inflate_impl(new NewChunk(null, 0));
      AssertJUnit.assertEquals(vals.length + 1 + l, nc.len());
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], nc.at0(l+i));
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], nc.at(l+i));
      AssertJUnit.assertTrue(nc.isNA0(vals.length+l));
      AssertJUnit.assertTrue(nc.isNA(vals.length+l));

      Chunk cc2 = nc.compress();
      AssertJUnit.assertEquals(vals.length + 1 + l, cc.len());
      AssertJUnit.assertTrue(cc2 instanceof C8DChunk);
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], cc2.at0(l+i));
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], cc2.at(l+i));
      AssertJUnit.assertTrue(cc2.isNA0(vals.length+l));
      AssertJUnit.assertTrue(cc2.isNA(vals.length+l));

      AssertJUnit.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }
}
