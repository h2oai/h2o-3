package water.rapids.ast.prims.mungers;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.ast.AstRoot;
import water.rapids.ast.params.AstNumList;
import water.rapids.vals.ValFrame;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class AstScaleTest extends TestUtil {

  @Rule
  public transient ExpectedException ee = ExpectedException.none();
  
  @BeforeClass
  public static void setup() { stall_till_cloudsize(1); }

  @Parameterized.Parameters
  public static Object[] scaleFunc() { return new Object[]{"scale", "scale_inplace"}; };
  
  @Parameterized.Parameter
  public String scaleFunc;
  
  @Test
  public void testScaleNumeric() {
    Scope.enter();
    try {
      Frame fr = new TestFrameBuilder()
              .withColNames("C1", "C2")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ard(0, 2, 4, 6))
              .withDataForCol(1, ard(0, 0, 0, 1))
              .build();
      Frame expected = new TestFrameBuilder()
              .withColNames("C1", "C2")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ard(-1.161895003862225, -0.3872983346207417, 0.3872983346207417, 1.161895003862225))
              .withDataForCol(1, ard(-0.5, -0.5, -0.5, 1.5))
              .build();

      ValFrame v = (ValFrame) Rapids.exec("(" + scaleFunc + " " + fr._key + " 1 1)");

      compareFrames(expected, v.getFrame(), 1e-10);
      checkInPlace(fr, v);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testScaleNumericWithCategoricals() {
    Scope.enter();
    try {
      Frame fr = new TestFrameBuilder()
              .withColNames("C1", "C2")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "c", "d"))
              .withDataForCol(1, ard(0, 0, 0, 1))
              .build();
      Frame expected = new TestFrameBuilder()
              .withColNames("C1", "C2")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "c", "d"))
              .withDataForCol(1, ard(-0.5, -0.5, -0.5, 1.5))
              .build();

      ValFrame v = (ValFrame) Rapids.exec("(" + scaleFunc + " " + fr._key + " 1 1)");

      compareFrames(expected, v.getFrame(), 1e-10);
      checkInPlace(fr, v);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testScaleNoNumeric() {
    Scope.enter();
    try {
      Frame fr = new TestFrameBuilder()
              .withColNames("C1", "C2")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "c", "d"))
              .withDataForCol(1, ar("a", "b", "c", "d"))
              .build();
      Frame expected = new TestFrameBuilder()
              .withColNames("C1", "C2")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "c", "d"))
              .withDataForCol(1, ar("a", "b", "c", "d"))
              .build();

      ValFrame v = (ValFrame) Rapids.exec("(" + scaleFunc + " " + fr._key + " 1 1)");

      compareFrames(expected, v.getFrame());
      checkInPlace(fr, v);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testCalcMeans_invalidCols() {
    Frame origFr = mock(Frame.class);
    Frame fr = mock(Frame.class);
    AstRoot meanSpec = mock(AstNumList.class);

    when(fr.numCols()).thenReturn(43);
    when(((AstNumList) meanSpec).expand()).thenReturn(new double[4]);

    ee.expectMessage("Values must be the same length as is the number of columns of the Frame to scale" +
            " (fill 0 for non-numeric columns).");
    
    AstScale.calcMeans(null, meanSpec, fr, origFr);
  }

  @Test
  public void testCalcMults_invalidCols() {
    Frame origFr = mock(Frame.class);
    Frame fr = mock(Frame.class);
    AstRoot multSpec = mock(AstNumList.class);

    when(fr.numCols()).thenReturn(43);
    when(((AstNumList) multSpec).expand()).thenReturn(new double[4]);

    ee.expectMessage("Values must be the same length as is the number of columns of the Frame to scale" +
            " (fill 0 for non-numeric columns).");

    AstScale.calcMults(null, multSpec, fr, origFr);
  }

  private void checkInPlace(Frame input, ValFrame result) {
    Frame output = result.getFrame();
    if ("scale_inplace".equals(scaleFunc)) {
      assertFrameEquals(input, output, 0);
    } else {
      for (int i = 0; i < input.numCols(); i++) {
        if (input.vec(i).get_type() == Vec.T_NUM) {
          Assert.assertNotEquals(input.vec(i).max(), output.vec(i).max());
        } else {
          assertCatVecEquals(input.vec(i), output.vec(i));
        }
      }
    }
  }
  
}
