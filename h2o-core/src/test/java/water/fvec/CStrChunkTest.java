package water.fvec;

import org.junit.*;

import water.TestUtil;
import water.parser.ValueString;
import java.util.Arrays;

public class CStrChunkTest extends TestUtil {
  @Test
  public void test_inflate_impl() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(null, 0);

      ValueString [] vals = new ValueString [1000001];
      for (int i = 0; i < vals.length; i++) {
        vals[i] = new ValueString();
        vals[i].setTo("Foo"+i);
      }
      if (l==1) nc.addNA();
      for (ValueString v : vals) nc.addStr(v);
      nc.addNA();

      Chunk cc = nc.compress();
      Assert.assertEquals(vals.length + 1 + l, cc._len);
      Assert.assertTrue(cc instanceof CStrChunk);
      if (l==1) Assert.assertTrue(cc.isNA0(0));
      if (l==1) Assert.assertTrue(cc.isNA(0));
      ValueString vs = new ValueString();
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.atStr0(vs, l+i));
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.atStr(vs, l+i));
      Assert.assertTrue(cc.isNA0(vals.length+l));
      Assert.assertTrue(cc.isNA(vals.length+l));

      nc = cc.inflate_impl(new NewChunk(null, 0));
      Assert.assertEquals(vals.length + 1 + l, nc._len);

      if (l==1) Assert.assertTrue(nc.isNA0(0));
      if (l==1) Assert.assertTrue(nc.isNA(0));
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], nc.atStr0(vs, l+i));
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], nc.atStr(vs, l+i));
      Assert.assertTrue(nc.isNA0(vals.length+l));
      Assert.assertTrue(nc.isNA(vals.length+l));

      Chunk cc2 = nc.compress();
      Assert.assertEquals(vals.length + 1 + l, cc._len);
      Assert.assertTrue(cc2 instanceof CStrChunk);
      if (l==1) Assert.assertTrue(cc2.isNA0(0));
      if (l==1) Assert.assertTrue(cc2.isNA(0));
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc2.atStr0(vs, l+i));
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc2.atStr(vs, l+i));
      Assert.assertTrue(cc2.isNA0(vals.length+l));
      Assert.assertTrue(cc2.isNA(vals.length+l));

      Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }
}


