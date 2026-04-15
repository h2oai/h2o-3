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
      String numList = idx(data.vec(3), "cC", "cB", "cD");
      String rapids = "(tmp= tst (match (cols data [3]) [" + numList + "] -1 0))";
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
  public void testMatchNumListStart() {
    final Frame data = makeTestFrame();
    Frame output = null;
    try {
      String numList = idx(data.vec(3), "cC", "cB", "cD");
      String rapids = "(tmp= tst (match (cols data [3]) [" + numList + "] 0 1))";
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
  public void testMatchCatList() {
    final Frame data = makeTestFrame();
    Frame output = null;
    try {
      String rapids = "(tmp= tst (match (cols data [3]) [\"cC\",\"cB\",\"cD\"] -1 0))";
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
      String rapids = "(tmp= tst (match (cols data [2]) [\"sC\",\"sB\",\"sD\"] -1 0))";
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

  @Test(expected = IllegalArgumentException.class)
  public void testMatchStrListNumericDomain() {
    final Frame data = makeTestFrame();
    Frame output = null;
    try {
      String rapids = "(tmp= tst (match (cols data [2]) [1,2,3] -1 0))";
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

  @Test(expected = IllegalArgumentException.class)
  public void testMatchNumListStringDomain() {
    final Frame data = makeTestFrame();
    Frame output = null;
    try {
      String rapids = "(tmp= tst (match (cols data [3]) [[\"sB\",\"sC\",\"sD\"] -1 0))";
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

  @Test()
  public void testMatchSameValueInMatchList() {
    final Frame data = makeTestFrame();
    Frame output = null;
    try {
      String rapids = "(tmp= tst (match (cols data [3]) [2,1,3,1] -1 0))";
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
    Random rnd = new Random();
    final int len = 55000;
    double numData[] = new double[len];
    double numDataStart[] = new double[len];
    String[] strData = new String[len];
    String[] catData = new String[len];
    for (int i = 0; i < len; i++) {
      char c = (char) ('A' + rnd.nextInt('Z' - 'A'));
      numData[i] = c == 'B' ? 1 : c == 'C' ? 0 : c == 'D' ? 2 : -1;
      numDataStart[i] = c == 'B' ? 2 : c == 'C' ? 1 : c == 'D' ? 3 : 0;
      strData[i] = "s" + c;
      catData[i] = "c" + c;
    }
    return new TestFrameBuilder()
            .withName("data")
            .withColNames("Expected", "ExpectedStart", "Str", "Cat")
            .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_STR, Vec.T_CAT)
            .withDataForCol(0, numData)
            .withDataForCol(1, numDataStart)
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
