package water.rapids.ast.prims.advmath;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.assertEquals;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class AstUniqueTest extends TestUtil {


  @Test
  public void uniqueCategoricalTest() {
    Scope.enter();
    try {
      final Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB", "ColC")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ard(1, 1))
              .withDataForCol(1, ard(1, 1))
              .withDataForCol(2, ar("3", "6"))
              .build();
      final String expression = "(unique (cols testFrame [2]) false)";
      final Val val = Rapids.exec(expression);
      final Frame res = Scope.track(val.getFrame());

      assertEquals(2, res.numRows());
      assertEquals("3", res.vec(0).stringAt(0));
      assertEquals("6", res.vec(0).stringAt(1));

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void uniqueNumericalTest() {
    Scope.enter();
    try {
      final Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB", "ColC")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ard(1, 1))
              .withDataForCol(1, ard(1, 1))
              .withDataForCol(2, ar(3, 6))
              .build();
      final String expression = "(unique (cols testFrame [2]) false)";
      final Val val = Rapids.exec(expression);
      final Frame res = Scope.track(val.getFrame());

      final Vec expected = vec(6, 3);
      assertVecEquals(expected, res.vec(0), 1e-6); // TODO Why order of 6 and 3 is as if it is desc. sorted ?
      expected.remove();
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void numericalNasRecognized() {
    Scope.enter();
    try {
      final Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB", "ColC")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ard(1, Double.NaN))
              .withDataForCol(1, ard(1, 1))
              .withDataForCol(2, ar("3", "6"))
              .build();
      final String expression = "(unique (cols testFrame [0]) true)";
      final Val val = Rapids.exec(expression);
      final Frame res = Scope.track(val.getFrame());

      assertEquals(2, res.numRows());
      assertEquals(1, res.numCols());
      final Vec vec = res.vec(0);
      assertEquals(Double.NaN, vec.at(0), 0d);
      assertEquals(1, vec.at(1), 0d);

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void categoricalNasRecognized() {
    Scope.enter();
    try {
      final Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB", "ColC")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ard(1, Double.NaN))
              .withDataForCol(1, ard(1, 1))
              .withDataForCol(2, ar("LEVEL1", null))
              .build();
      final String expression = "(unique (cols testFrame [2]) true)";
      final Val val = Rapids.exec(expression);
      final Frame res = Scope.track(val.getFrame());

      assertEquals(2, res.numRows());
      assertEquals(1, res.numCols());
      final Vec vec = res.vec(0);
      assertEquals(0, vec.at(0), 0d);
      assertEquals("LEVEL1", vec.stringAt(0));
      assertEquals(Double.NaN, vec.at(1), 0d);
      assertEquals("null", vec.stringAt(1));

    } finally {
      Scope.exit();
    }
  }


}
