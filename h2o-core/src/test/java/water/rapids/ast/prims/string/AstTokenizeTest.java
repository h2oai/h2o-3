package water.rapids.ast.prims.string;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.vals.ValFrame;

public class AstTokenizeTest extends TestUtil {
  @BeforeClass static public void setup() { stall_till_cloudsize(1); }

  @Test
  public void testTokenize() {
    Frame fr = makeTestFrame();
    Vec expected = svec(
            "Foot", "on", "the", "pedal", "Never", "ever", "false", "metal", null,
            "Engine", "running", "hotter", "than", "a", "boiling", "kettle", "My", "job", "ain't", "a", "job", null,
            "It's", "a", "damn", "good", "time", "City", "to", "city", "I'm", "running", "my", "rhymes", null);
    Frame res = null;
    try {
      ValFrame val = (ValFrame) Rapids.exec("(tmp= py_1 (tokenize data \"\\\\s\"))");
      res = val.getFrame();
      Vec actual = res.anyVec();
      assertStringVecEquals(expected, actual);
    } finally {
      fr.remove();
      expected.remove();
      if (res != null) res.remove();
    }
  }

  private Frame makeTestFrame() {
    return new TestFrameBuilder()
      .withName("data")
      .withColNames("ColA", "ColB")
      .withVecTypes(Vec.T_STR, Vec.T_STR)
      .withDataForCol(0, ar("Foot on the pedal", "Engine running hotter than a boiling kettle", "It's a damn good time"))
      .withDataForCol(1, ar("Never ever false metal", "My job ain't a job", "City to city I'm running my rhymes"))
      .withChunkLayout(2, 1)
      .build();
  }

}