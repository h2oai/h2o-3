package water.rapids.ast.prims.string;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;

import java.util.Arrays;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class AstGrepTest extends TestUtil {

  @Parameterized.Parameters(name= "{index}: {4}")
  public static Iterable<? extends Object> data() { return Arrays.asList(
          new Object[]{"[B-D]", 0, 1, 0, "insensitive,strings"},
          new Object[]{"[b-d]", 1, 1, 0, "sensitive,strings"},
          new Object[]{"[B-D]", 0, 2, 0, "insensitive,categoricals"},
          new Object[]{"[b-d]", 1, 2, 0, "sensitive,categoricals"},
          new Object[]{"[B-D]", 0, 1, 1, "insensitive,strings,invert"},
          new Object[]{"[b-d]", 1, 1, 1, "sensitive,strings,invert"},
          new Object[]{"[B-D]", 0, 2, 1, "insensitive,categoricals,invert"},
          new Object[]{"[b-d]", 1, 2, 1, "sensitive,categoricals,invert"}
  ); }

  @BeforeClass
  static public void setup() {
    stall_till_cloudsize(1);
  }

  @Parameterized.Parameter( )
  public String _regex;
  @Parameterized.Parameter(1)
  public int _ignoreCase;
  @Parameterized.Parameter(2)
  public int _col;
  @Parameterized.Parameter(3)
  public int _invert;
  @Parameterized.Parameter(4)
  public String _description; // not used

  @Test
  public void testGrep() throws Exception {
    final Frame data = makeTestFrame();
    Frame output = null;
    try {
      String rapids = "(tmp= tst (grep (cols data [" + _col + "]) \"" + _regex + "\" " + _ignoreCase + " " + _invert + " 0))";
      Val val = Rapids.exec(rapids);
      output = val.getFrame();
      int length = (int) output.vec(0).length();
      int lastPos = -1;
      for (int i = 0; i < length; i++) {
        int pos = (int) output.vec(0).at8(i);
        for (int j = lastPos + 1; j < pos; j++) {
          assertEquals(0L, data.vec(0).at8(j));
        }
        assertEquals(1L, data.vec(0).at8(pos));
        lastPos = pos;
      }
    } finally {
      data.delete();
      if (output != null) {
        output.delete();
      }
    }
  }

  @Test
  public void testGrep_outputLogical() throws Exception {
    final Frame data = makeTestFrame();
    Frame output = null;
    try {
      String rapids = "(tmp= tst (grep (cols data [" + _col + "]) \"" + _regex + "\" " + _ignoreCase + " " + _invert + " 1))";
      Val val = Rapids.exec(rapids);
      output = val.getFrame();
      assertVecEquals(data.vec(0), output.vec(0), 0.0);
    } finally {
      data.delete();
      if (output != null) {
        output.delete();
      }
    }
  }

  private Frame makeTestFrame() {
    int len = 'Z' - 'A';
    double numData[] = new double[len];
    String[] strData = new String[len];
    String[] catData = new String[len];
    for (int i = 0; i < len; i++) {
      char c = (char) ('A' + i);
      numData[i] = ((c >= 'B' && c <= 'D' ? 1 : 0) + _invert) % 2;
      strData[i] = Character.toString(c);
      catData[i] = Character.toString(c);
    }
    return new TestFrameBuilder()
            .withName("data")
            .withColNames("Expected", "Str", "Cat")
            .withVecTypes(Vec.T_NUM, Vec.T_STR, Vec.T_CAT)
            .withDataForCol(0, numData)
            .withDataForCol(1, strData)
            .withDataForCol(2, catData)
            .withChunkLayout(10, 2, len - 12)
            .build();
  }

}