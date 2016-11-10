package water.fvec;

import org.junit.Assert;
import org.junit.Ignore;
import water.DKV;
import water.Futures;
import water.Key;
import water.parser.BufferedString;
import water.util.ArrayUtils;

import java.util.ArrayList;

/**
 * Methods to access frame internals.
 */
@Ignore("Support for tests, but no actual tests here")
public class FrameTestUtil extends Frame {
  public static Frame createFrame(String fname, long[] chunkLayout, String[][] data) {
    ArrayList<String> data2 = new ArrayList<>();
    for(String [] sary:data)
      for(String s:sary)
        data2.add(s);
    return createFrame(fname,chunkLayout,data2.toArray(new String[0]));
  }
  public static Frame createFrame(String fname, long[] chunkLayout, String[] data) {
    AppendableVec av = new AppendableVec(new Vec.VectorGroup().addVec(), Vec.T_STR);
    int r = 0;
    Futures fs = new Futures();
    for(int i = 0; i < chunkLayout.length; ++i){
      NewChunkAry ncs = av.chunkForChunkIdx(i);
      for(int j = 0; j < chunkLayout[i]; ++j)
        ncs.addStr(0,data[r++]);
      ncs.close(fs);
    }
    Frame f = new Frame(Key.<Frame>make(fname),null,av.layout_and_close(fs));
    DKV.put(f._key,f);
    return f;
  }

  public static Frame createFrame(String fname, long[] chunkLayout) {
    AppendableVec av = new AppendableVec(new Vec.VectorGroup().addVec(), Vec.T_NUM);
    int r = 0;
    Futures fs = new Futures();
    for(int i = 0; i < chunkLayout.length; ++i){
      NewChunkAry ncs = av.chunkForChunkIdx(i);
      for(int j = 0; j < chunkLayout[i]; ++j)
        ncs.addNum(r++);
      ncs.close(fs);
    }
    Frame f = new Frame(Key.<Frame>make(fname),null,av.layout_and_close(fs));
    DKV.put(f._key,f);
    return f;
  }

  public static void assertValues(Frame f, String[] expValues) {
    assertValues(f.vec(0), expValues);
  }

  public static void assertValues(Vec v, String[] expValues) {
    Assert.assertEquals("Number of rows", expValues.length, v.length());
    BufferedString tmpStr = new BufferedString();
    for (int i = 0; i < v.length(); i++) {
      if (v.isNA(i)) Assert.assertEquals("NAs should match", null, expValues[i]);
      else Assert.assertEquals("Values should match", expValues[i], v.atStr(tmpStr, i).toString());
    }
  }

  public static String[] collectS(Vec v) {
    String[] res = new String[(int) v.length()];
    BufferedString tmpStr = new BufferedString();
      for (int i = 0; i < v.length(); i++)
        res[i] = v.isNA(i) ? null : v.atStr(tmpStr, i).toString();
    return res;
  }
}
