package water.fvec;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;
import water.TestUtil;

import java.util.Arrays;
import java.util.Iterator;

public class C2ChunkTest extends TestUtil {
  @Test
  public void test_inflate_impl() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(null, 0);

      int[] vals = new int[]{-32767, 0, 32767};
      if (l==1) nc.addNA();
      for (int v : vals) nc.addNum(v, 0);
      nc.addNA(); //-32768

      Chunk cc = nc.compress();
      AssertJUnit.assertEquals(vals.length + 1 + l, cc.len());
      AssertJUnit.assertTrue(cc instanceof C2Chunk);
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], cc.at80(l+i));
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], cc.at8(l+i));
      AssertJUnit.assertTrue(cc.isNA0(vals.length+l));
      AssertJUnit.assertTrue(cc.isNA(vals.length+l));

      nc = new NewChunk(null, 0);
      cc.inflate_impl(nc);
      if (l==1) AssertJUnit.assertTrue(cc.isNA0(0));
      AssertJUnit.assertEquals(vals.length+l+1, nc.sparseLen());
      AssertJUnit.assertEquals(vals.length+l+1, nc.len());
      Iterator<NewChunk.Value> it = nc.values(0, vals.length+1+l);
      for (int i = 0; i < vals.length+1+l; ++i) AssertJUnit.assertTrue(it.next().rowId0() == i);
      AssertJUnit.assertTrue(!it.hasNext());
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], nc.at80(l+i));
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], nc.at8(l+i));
      AssertJUnit.assertTrue(cc.isNA0(vals.length+l));

      Chunk cc2 = nc.compress();
      AssertJUnit.assertEquals(vals.length + 1 + l, cc.len());
      AssertJUnit.assertTrue(cc2 instanceof C2Chunk);
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], cc2.at80(l+i));
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], cc2.at8(l+i));
      AssertJUnit.assertTrue(cc2.isNA0(vals.length+l));
      AssertJUnit.assertTrue(cc2.isNA(vals.length+l));

      AssertJUnit.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }
}
