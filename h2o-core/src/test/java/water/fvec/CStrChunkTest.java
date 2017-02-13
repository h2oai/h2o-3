package water.fvec;

import org.junit.*;

import water.IcedUtils;
import water.TestUtil;
import water.parser.BufferedString;

import java.util.Arrays;

public class CStrChunkTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  @Test
  public void test_inflate_impl() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(Vec.T_STR);

      BufferedString[] vals = new BufferedString[1000001];
      for (int i = 0; i < vals.length; i++) {
        vals[i] = new BufferedString("Foo"+i);
      }
      if (l==1) nc.addNA();
      for (BufferedString v : vals) nc.addStr(v);
      nc.addNA();

      CStrChunk cc = (CStrChunk) nc.compress();
      Assert.assertEquals(vals.length + 1 + l, cc._len);
      Assert.assertTrue(cc instanceof CStrChunk);
      if (l==1) Assert.assertTrue(cc.isNA(0));
      BufferedString tmpStr = new BufferedString();
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc.atStr(tmpStr, l + i));
      Assert.assertTrue(cc.isNA(vals.length + l));

      CStrChunk cc2 = IcedUtils.deepCopy(cc);
      Assert.assertEquals(vals.length + 1 + l, cc2._len);
      Assert.assertTrue(cc2 instanceof CStrChunk);
      if (l==1) Assert.assertTrue(cc2.isNA(0));
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc2.atStr(tmpStr, l + i));
      Assert.assertTrue(cc2.isNA(vals.length + l));

      nc = cc.inflate_impl(new NewChunk(Vec.T_STR));
      Assert.assertEquals(vals.length + 1 + l, nc._len);

      if (l==1) Assert.assertTrue(nc.isNA(0));
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], nc.atStr(tmpStr, l + i));
      Assert.assertTrue(nc.isNA(vals.length + l));

      cc2 = (CStrChunk) nc.compress();
      Assert.assertEquals(vals.length + 1 + l, cc._len);
      Assert.assertTrue(cc2 instanceof CStrChunk);
      if (l==1) Assert.assertTrue(cc2.isNA(0));
      for (int i = 0; i < vals.length; ++i) Assert.assertEquals(vals[i], cc2.atStr(tmpStr, l + i));
      Assert.assertTrue(cc2.isNA(vals.length + l));

      Assert.assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }

  @Test
  public void test_writer(){
    stall_till_cloudsize(1);
    Frame frame = null;
    try {
      frame = parse_test_file("smalldata/junit/iris.csv");

      //Create a label vector
      byte[] typeArr = {Vec.T_STR};
      VecAry labels = new VecAry(frame.anyVec().makeCons(1, 0, null, typeArr));
      VecAry.Writer writer = labels.open();
      int rowCnt = (int)frame.lastVec().length();
      for (int r = 0; r < rowCnt; r++) // adding labels in reverse order
        writer.set(rowCnt-r-1, "Foo"+(r+1));
      writer.close();

      //Append label vector and spot check
      frame.add("Labels", labels);
      Assert.assertTrue("Failed to create a new String based label column", frame.lastVec().atStr(new BufferedString(), 42).compareTo(new BufferedString("Foo108"))==0);
    } finally {
      if (frame != null) frame.delete();
    }
  }
}


