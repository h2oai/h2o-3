package water.fvec;

import org.junit.Assert;
import org.junit.Ignore;
import water.DKV;
import water.Key;
import water.parser.BufferedString;

/**
 * Methods to access frame internals.
 */
@Ignore("Support for tests, but no actual tests here")
public class FrameTestUtil {

  public static Frame createFrame(String fname, long[] chunkLayout, String[][] data) {
    Frame f = new Frame(Key.<Frame>make(fname));
    f.preparePartialFrame(new String[]{"C0"});
    f.update();
    // Create chunks
    byte[] types = new byte[] {Vec.T_STR};
    for (int i=0; i<chunkLayout.length; i++) {
      createNC(fname, data[i], i, (int) chunkLayout[i], types);
    }
    // Reload frame from DKV
    f = DKV.get(fname).get();
    // Finalize frame
    f.finalizePartialFrame(chunkLayout, new String[][] { null }, types);
    return f;
  }

  public static NewChunk createNC(String fname, String[] data, int cidx, int len, byte[] types) {
    NewChunk[] nchunks = Frame.createNewChunks(fname, types, cidx);
    for (int i=0; i<len; i++) {
      nchunks[0].addStr(data[i] != null ? data[i] : null);
    }
    Frame.closeNewChunks(nchunks);
    return nchunks[0];
  }

  public static Frame createFrame(String fname, long[] chunkLayout) {
    // Create a frame
    Frame f = new Frame(Key.<Frame>make(fname));
    f.preparePartialFrame(new String[]{"C0"});
    f.update();
    byte[] types = new byte[] {Vec.T_NUM};
    // Create chunks
    for (int i=0; i<chunkLayout.length; i++) {
      createNC(fname, i, (int) chunkLayout[i], types);
    }
    // Reload frame from DKV
    f = DKV.get(fname).get();
    // Finalize frame
    f.finalizePartialFrame(chunkLayout, new String[][] { null }, types);
    return f;
  }

  public static NewChunk createNC(String fname, int cidx, int len, byte[] types) {
    NewChunk[] nchunks = Frame.createNewChunks(fname, types, cidx);
    int starVal = cidx * 1000;
    for (int i=0; i<len; i++) {
      nchunks[0].addNum(starVal + i);
    }
    Frame.closeNewChunks(nchunks);
    return nchunks[0];
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
