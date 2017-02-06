package water.rapids.ast.prims.assign;

import org.junit.Ignore;
import water.Futures;
import water.Key;
import water.fvec.AppendableVec;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.parser.BufferedString;

@Ignore("Helper class for AstRectangleAssign tests, no actual tests here")
public class AstRecAssignTestUtils {

  static Vec seqStrVec(int... runs) {
    Key k = Vec.VectorGroup.VG_LEN1.addVec();
    Futures fs = new Futures();
    AppendableVec avec = new AppendableVec(k, Vec.T_STR);
    NewChunk chunk = new NewChunk(avec, 0);
    int seq = 0;
    for (int r : runs) {
      if (seq > 0) chunk.addStr(null);
      for (int i = 0; i < r; i++) chunk.addStr(String.valueOf(seq++));
    }
    chunk.close(0, fs);
    Vec vec = avec.layout_and_close(fs);
    fs.blockForPending();
    return vec;
  }

  static double[] vec2array(Vec v) {
    Vec.Reader ovr = v.new Reader();
    assert ovr.length() < Integer.MAX_VALUE;
    final int len = (int) ovr.length();
    double[] array = new double[len];
    for (int i = 0; i < len; i++) array[i] = ovr.at(i);
    return array;
  }

  static String[] catVec2array(Vec v) {
    double[] raw = vec2array(v);
    String[] cats = new String[raw.length];
    for (int i = 0; i < cats.length; i++) cats[i] = Double.isNaN(raw[i]) ? null: v.factor((long) raw[i]);
    return cats;
  }

  static String[] strVec2array(Vec v) {
    Vec.Reader ovr = v.new Reader();
    assert ovr.length() < Integer.MAX_VALUE;
    final int len = (int) ovr.length();
    BufferedString bs = new BufferedString();
    String[] array = new String[len];
    for (int i = 0; i < len; i++) {
      BufferedString s = ovr.atStr(bs, i);
      if (s != null) array[i] = s.toString();
    }
    return array;
  }

}
