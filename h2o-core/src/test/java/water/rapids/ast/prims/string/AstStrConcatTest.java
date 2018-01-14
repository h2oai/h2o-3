package water.rapids.ast.prims.string;

import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.vals.ValFrame;

import static org.junit.Assert.*;

public class AstStrConcatTest extends TestUtil {

  @BeforeClass
  public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void testStrConcat() {
    try {
      Scope.enter();
      final Frame input = new TestFrameBuilder()
              .withName("input")
              .withColNames("ColA", "ColB", "ColC")
              .withVecTypes(Vec.T_STR, Vec.T_STR, Vec.T_STR)
              .withDataForCol(0, ar("0", "1", "2", "3", "4", "5", "6"))
              .withDataForCol(1, ar("A", "B", "C", "D", "E", "F", "G"))
              .withDataForCol(2, ar("a", "b", "c", "d", "e", "f", "g"))
              .withChunkLayout(2, 5)
              .build();
      Scope.track(input);

      final Frame expected = new TestFrameBuilder()
              .withName("expected")
              .withColNames("C1")
              .withVecTypes(Vec.T_STR)
              .withDataForCol(0, ar("0Aa", "1Bb", "2Cc", "3Dd", "4Ee", "5Ff", "6Gg"))
              .withChunkLayout(2, 5)
              .build();
      Scope.track(expected);

      Val val = Rapids.exec("(tmp= output (strconcat input))");

      assertTrue(val instanceof ValFrame);
      Frame output = val.getFrame();
      Scope.track(output);

      assertEquals(1, output.numCols());

      assertStringVecEquals(expected.anyVec(), output.anyVec());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testStrConcatWithNAs() {
    try {
      Scope.enter();
      final Frame input = new TestFrameBuilder()
              .withName("input")
              .withColNames("ColA", "ColB", "ColC")
              .withVecTypes(Vec.T_STR, Vec.T_STR, Vec.T_STR)
              .withDataForCol(0, ar(null, "1", "2", "3", "4", "5", "6"))
              .withDataForCol(1, ar("A", "B", "C", null, "E", "F", "G"))
              .withDataForCol(2, ar("a", "b", "c", "d", "e", "f", null))
              .withChunkLayout(2, 5)
              .build();
      Scope.track(input);

      final Frame expected = new TestFrameBuilder()
              .withName("expected")
              .withColNames("C1")
              .withVecTypes(Vec.T_STR)
              .withDataForCol(0, ar("Aa", "1Bb", "2Cc", "3d", "4Ee", "5Ff", "6G"))
              .withChunkLayout(2, 5)
              .build();
      Scope.track(expected);

      Val val = Rapids.exec("(tmp= output (strconcat input))");

      assertTrue(val instanceof ValFrame);
      Frame output = val.getFrame();
      Scope.track(output);

      assertEquals(1, output.numCols());

      assertStringVecEquals(expected.anyVec(), output.anyVec());
    } finally {
      Scope.exit();
    }
  }

}