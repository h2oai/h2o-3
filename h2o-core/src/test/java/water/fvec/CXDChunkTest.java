package water.fvec;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;
import water.TestUtil;

import java.util.Arrays;
import java.util.Iterator;

public class CXDChunkTest extends TestUtil {
  @Test
  public void test_inflate_impl() {
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
      AssertJUnit.assertEquals(vals.length + 1 + l, cc.len());
      AssertJUnit.assertTrue(cc instanceof CXDChunk);
      if (l==1) {
        AssertJUnit.assertTrue(cc.isNA0(0));
        AssertJUnit.assertTrue(cc.isNA(0));
      }
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], cc.at0(i+l), 0);
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], cc.at(i+l), 0);
      AssertJUnit.assertTrue(cc.isNA0(vals.length+l));
      AssertJUnit.assertTrue(cc.isNA(vals.length+l));

      nc = new NewChunk(null, 0);
      cc.inflate_impl(nc);
      AssertJUnit.assertEquals(vals.length+l+1, nc.len());
      AssertJUnit.assertEquals(2+1+l, nc.sparseLen());
      Iterator<NewChunk.Value> it = nc.values(0, vals.length+l+1);
      if (l==1) AssertJUnit.assertTrue(it.next().rowId0() == 0);
      AssertJUnit.assertTrue(it.next().rowId0() == 3+l);
      AssertJUnit.assertTrue(it.next().rowId0() == 101+l);
      AssertJUnit.assertTrue(it.next().rowId0() == vals.length+l);
      AssertJUnit.assertTrue(!it.hasNext());
      if (l==1) {
        AssertJUnit.assertTrue(nc.isNA0(0));
        AssertJUnit.assertTrue(nc.isNA(0));
      }
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], nc.at0(l+i), 0);
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], nc.at(l+i), 0);
      AssertJUnit.assertTrue(nc.isNA0(vals.length+l));
      AssertJUnit.assertTrue(nc.isNA(vals.length+l));

      Chunk cc2 = nc.compress();
      AssertJUnit.assertEquals(vals.length+1+l, cc.len());
      AssertJUnit.assertTrue(cc2 instanceof CXDChunk);
      if (l==1) {
        AssertJUnit.assertTrue(cc2.isNA0(0));
        AssertJUnit.assertTrue(cc2.isNA(0));
      }
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], cc2.at0(i+l), 0);
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], cc2.at(i+l), 0);
      AssertJUnit.assertTrue(cc2.isNA0(vals.length+l));
      AssertJUnit.assertTrue(cc2.isNA(vals.length+l));

      AssertJUnit.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }
}
