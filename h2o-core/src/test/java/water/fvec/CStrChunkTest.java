package water.fvec;

import org.junit.*;

import water.IcedUtils;
import water.TestUtil;
import water.parser.BufferedString;

import java.util.Arrays;
import static org.junit.Assert.*;

public class CStrChunkTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  @Test
  public void test_inflate_impl() {
    for (int l=0; l<2; ++l) {
      NewChunk nc = new NewChunk(null, 0);

      BufferedString[] vals = new BufferedString[1000001];
      for (int i = 0; i < vals.length; i++) {
        vals[i] = new BufferedString("Foo"+i);
      }
      if (l==1) nc.addNA();
      for (BufferedString v : vals) nc.addStr(v);
      nc.addNA();

      Chunk cc = nc.compress();
      assertEquals(vals.length + 1 + l, cc._len);
      assertTrue(cc instanceof CStrChunk);
      if (l==1) assertTrue(cc.isNA(0));
      if (l==1) assertTrue(cc.isNA_abs(0));
      BufferedString tmpStr = new BufferedString();
      for (int i = 0; i < vals.length; ++i) assertEquals(vals[i], cc.atStr(tmpStr, l + i));
      for (int i = 0; i < vals.length; ++i) assertEquals(vals[i], cc.atStr_abs(tmpStr, l + i));
      assertTrue(cc.isNA(vals.length + l));
      assertTrue(cc.isNA_abs(vals.length + l));

      Chunk cc2 = IcedUtils.deepCopy(cc);
      assertEquals(vals.length + 1 + l, cc2._len);
      assertTrue(cc2 instanceof CStrChunk);
      if (l==1) assertTrue(cc2.isNA(0));
      if (l==1) assertTrue(cc2.isNA_abs(0));
      for (int i = 0; i < vals.length; ++i) assertEquals(vals[i], cc2.atStr(tmpStr, l + i));
      for (int i = 0; i < vals.length; ++i) assertEquals(vals[i], cc2.atStr_abs(tmpStr, l + i));
      assertTrue(cc2.isNA(vals.length + l));
      assertTrue(cc2.isNA_abs(vals.length + l));

      nc = cc.inflate_impl(new NewChunk(null, 0));
      assertEquals(vals.length + 1 + l, nc._len);

      if (l==1) assertTrue(nc.isNA(0));
      if (l==1) assertTrue(nc.isNA_abs(0));
      for (int i = 0; i < vals.length; ++i) assertEquals(vals[i], nc.atStr(tmpStr, l + i));
      for (int i = 0; i < vals.length; ++i) assertEquals(vals[i], nc.atStr_abs(tmpStr, l + i));
      assertTrue(nc.isNA(vals.length + l));
      assertTrue(nc.isNA_abs(vals.length + l));

      cc2 = nc.compress();
      assertEquals(vals.length + 1 + l, cc._len);
      assertTrue(cc2 instanceof CStrChunk);
      if (l==1) assertTrue(cc2.isNA(0));
      if (l==1) assertTrue(cc2.isNA_abs(0));
      for (int i = 0; i < vals.length; ++i) assertEquals(vals[i], cc2.atStr(tmpStr, l + i));
      for (int i = 0; i < vals.length; ++i) assertEquals(vals[i], cc2.atStr_abs(tmpStr, l + i));
      assertTrue(cc2.isNA(vals.length + l));
      assertTrue(cc2.isNA_abs(vals.length + l));

      assertTrue(Arrays.equals(cc._mem, cc2._mem));
    }
  }

  @Test
  public void test_writer(){
    Frame frame = null;
    try {
      frame = parse_test_file("smalldata/junit/iris.csv");

      //Create a label vector
      byte[] typeArr = {Vec.T_STR};
      Vec labels = frame.lastVec().makeCons(1, 0, null, typeArr)[0];
      Vec.Writer writer = labels.open();
      int rowCnt = (int)frame.lastVec().length();
      for (int r = 0; r < rowCnt; r++) // adding labels in reverse order
        writer.set(rowCnt-r-1, "Foo"+(r+1));
      writer.close();

      //Append label vector and spot check
      frame.add("Labels", labels);
      assertTrue("Failed to create a new String based label column", frame.lastVec().atStr(new BufferedString(), 42).compareTo(new BufferedString("Foo108"))==0);
    } finally {
      if (frame != null) frame.delete();
    }
  }

  @Test
  public void test_sparse() {
    NewChunk nc = new NewChunk(null, 0);
    for( int i=0; i<100; i++ )
      nc.addNA();
    nc.addStr(new BufferedString("foo"));
    nc.addNA();
    nc.addStr(new BufferedString("bar"));
    Chunk c = nc.compress();
    assertTrue("first 100 entries are NA",c.isNA(0) && c.isNA(99));
    assertEquals("foo",c.atStr(new BufferedString(),100).toString());
    assertTrue("NA",c.isNA(101));
    assertEquals("bar",c.atStr(new BufferedString(),102).toString());
  }
}


