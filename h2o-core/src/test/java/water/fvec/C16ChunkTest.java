package water.fvec;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;
import water.TestUtil;

import java.util.Arrays;

public class C16ChunkTest extends TestUtil {
  @Test
  public void test_inflate_impl() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(null, 0);

      long[] vals = new long[]{Long.MIN_VALUE, Long.MAX_VALUE, 0l, 12312421425l, 23523523423l, Long.MIN_VALUE+1, Long.MAX_VALUE-1, -823048234l, -123123l};
      if (l==1) nc.addNA();
      for (long v : vals) nc.addUUID(v,v);
      nc.addNA();

      Chunk cc = nc.compress();
      AssertJUnit.assertEquals(vals.length + 1 + l, cc.len());
      AssertJUnit.assertTrue(cc instanceof C16Chunk);
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], cc.at16l0(l+i));
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], cc.at16l(l+i));
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], cc.at16h0(l+i));
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], cc.at16h(l+i));
      AssertJUnit.assertTrue(cc.isNA0(vals.length+l));
      AssertJUnit.assertTrue(cc.isNA(vals.length+l));

      nc = cc.inflate_impl(new NewChunk(null, 0));
      AssertJUnit.assertEquals(vals.length + 1 + l, nc.len());

      // NewChunk has no support for UUID (yet?)
//      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], nc.at16l0(l+i));
//      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], nc.at16l(l+i));
//      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], nc.at16h0(l+i));
//      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], nc.at16h(l+i));
//      AssertJUnit.assertTrue(nc.isNA0(vals.length+l));
//      AssertJUnit.assertTrue(nc.isNA(vals.length+l));

      Chunk cc2 = nc.compress();
      AssertJUnit.assertEquals(vals.length + 1 + l, cc.len());
      AssertJUnit.assertTrue(cc2 instanceof C16Chunk);
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], cc2.at16l0(l+i));
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], cc2.at16l(l+i));
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], cc2.at16h0(l+i));
      for (int i = 0; i < vals.length; ++i) AssertJUnit.assertEquals(vals[i], cc2.at16h(l+i));
      AssertJUnit.assertTrue(cc2.isNA0(vals.length+l));
      AssertJUnit.assertTrue(cc2.isNA(vals.length+l));

      AssertJUnit.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }
}
