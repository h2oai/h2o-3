package water.rapids.ast.prims.search;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;

import java.util.Random;

public class AstMatchTest extends TestUtil {

  @BeforeClass
  static public void setup() { stall_till_cloudsize(1); }

  @Test
  public void testMatchNumList() {
    final Frame data = makeTestFrame();
    Frame output = null;
    try {
      String numList = idx(data.vec(3), "cB", "cC", "cD");
      String rapids = "(tmp= tst (match (cols data [3]) [" + numList + "] -1 ignored false))";
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

  @Test
  public void testMatchCatList() {
    final Frame data = makeTestFrame();
    Frame output = null;
    try {
      String rapids = "(tmp= tst (match (cols data [3]) [\"cD\",\"cC\",\"cB\"] -1 ignored false))";
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

  @Test
  public void testMatchStrList() {
    final Frame data = makeTestFrame();
    Frame output = null;
    try {
      String rapids = "(tmp= tst (match (cols data [2]) [\"sD\",\"sC\",\"sB\"] -1 ignored false))";
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

  @Test
  public void testMatchNumListIndexes() {
    final Frame data = makeTestFrame();
    Frame output = null;
    try {
      String numList = idx(data.vec(3), "cB", "cC", "cD");
      String rapids = "(tmp= tst (match (cols data [3]) [" + numList + "] -1 ignored true))";
      Val val = Rapids.exec(rapids);
      output = val.getFrame();
      assertVecEquals(data.vec(1), output.vec(0), 0.0);
    } finally {
      data.delete();
      if (output != null) {
        output.delete();
      }
    }
  }

  @Test
  public void testMatchCatListIndexes() {
    final Frame data = makeTestFrame();
    Frame output = null;
    try {
      String rapids = "(tmp= tst (match (cols data [3]) [\"cB\",\"cC\",\"cD\"] -1 ignored true))";
      Val val = Rapids.exec(rapids);
      output = val.getFrame();
      assertVecEquals(data.vec(1), output.vec(0), 0.0);
    } finally {
      data.delete();
      if (output != null) {
        output.delete();
      }
    }
  }

  @Test
  public void testMatchStrListIndexes() {
    final Frame data = makeTestFrame();
    Frame output = null;
    try {
      String rapids = "(tmp= tst (match (cols data [2]) [\"sB\",\"sC\",\"sD\"] -1 ignored true))";
      Val val = Rapids.exec(rapids);
      output = val.getFrame();
      assertVecEquals(data.vec(1), output.vec(0), 0.0);
    } finally {
      data.delete();
      if (output != null) {
        output.delete();
      }
    }
  }

  private Frame makeTestFrame() {
    Random rnd = new Random();
    final int len = 55000;
    double numData[] = new double[len];
    double[] numDataIndexes = new double[len];
    String[] strData = new String[len];
    String[] catData = new String[len];
    for (int i = 0; i < len; i++) {
      char c = (char) ('A' + rnd.nextInt('Z' - 'A'));
      numData[i] = c >= 'B' && c <= 'D' ? 1 : -1;
      numDataIndexes[i] = c == 'B' ? 0 : c == 'C' ? 1 : c == 'D' ? 2 : -1;
      strData[i] = "s" + c;
      catData[i] = "c" + c;
    }
    return new TestFrameBuilder()
            .withName("data")
            .withColNames("Expected", "Expected_idexes", "Str", "Cat")
            .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_STR, Vec.T_CAT)
            .withDataForCol(0, numData)
            .withDataForCol(1, numDataIndexes)
            .withDataForCol(2, strData)
            .withDataForCol(3, catData)
            .withChunkLayout(10000, 10000, 10000, 20000, 5000)
            .build();
  }

  private String idx(Vec v, String... cats) {
    String[] domain = v.domain();
    StringBuilder sb = new StringBuilder();
    for (String cat : cats) {
      if (sb.length() > 0) sb.append(",");
      for (int i = 0; i < domain.length; i++) {
        if (cat.equals(domain[i])) {
          sb.append(i);
          break;
        }
      }
    }
    return sb.toString();
  }

}
