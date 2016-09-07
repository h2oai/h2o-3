package water.fvec;

import org.junit.Assert;
import org.junit.Ignore;
import water.Futures;
import water.parser.BufferedString;

/**
 * Methods to access frame internals.
 */
@Ignore("Support for tests, but no actual tests here")
public class FrameTestUtil {

  public static Vec makeStringVec(String[]... vals) {
    AppendableVec av = new AppendableVec(Vec.newKey(),Vec.T_STR);
    Futures fs = new Futures();
    for(int i = 0; i < vals.length; ++i) {
      NewChunk nc = new NewChunk(av,i);
      for(int j = 0; j < vals[i].length; ++j)
        nc.addStr(vals[i][j]);
      nc.close(fs);
    }
    VecAry vecs = av.layout_and_close(fs);
    fs.blockForPending();
    return (Vec) vecs.getVecRaw(0);
  }

  public static void assertValues(Frame f, String[] expValues) {
    assertValues(f.vecs(), expValues);
  }

  public static void assertValues(VecAry v, String[] expValues) {
    Assert.assertEquals("Number of rows", expValues.length, v.numRows());
    BufferedString tmpStr = new BufferedString();
    VecAry.Reader r = v.reader(false);
    for (int i = 0; i < v.numRows(); i++) {
      if (r.isNA(i,0)) Assert.assertEquals("NAs should match", null, expValues[i]);
      else Assert.assertEquals("Values should match", expValues[i], r.atStr(tmpStr,i,0).toString());
    }
  }

  public static String[] collectS(VecAry v) {
    String[] res = new String[(int) v.numRows()];
    BufferedString tmpStr = new BufferedString();
    VecAry.Reader r = v.reader(false);
      for (int i = 0; i < v.numRows(); i++)
        res[i] = r.isNA(i,0) ? null : r.atStr(tmpStr, i,0).toString();
    return res;
  }
}
