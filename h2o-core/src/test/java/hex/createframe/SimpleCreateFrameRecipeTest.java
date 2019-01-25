package hex.createframe;

import hex.createframe.recipes.SimpleCreateFrameRecipe;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.api.schemas4.input.CreateFrameSimpleIV4;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;

import static hex.createframe.recipes.SimpleCreateFrameRecipe.ResponseType;
import static org.junit.Assert.*;

/**
 * Test for the {@link SimpleCreateFrameRecipe} class.
 */
public class SimpleCreateFrameRecipeTest extends TestUtil {

  @BeforeClass() public static void setup() {
    stall_till_cloudsize(1);
  }


  /** Test that the frame with all default arguments can be constructed. */
  @Test
  public void emptyTest() {
    Scope.enter();
    try {
      CreateFrameSimpleIV4 s = new CreateFrameSimpleIV4().fillFromImpl();
      SimpleCreateFrameRecipe cf = s.createAndFillImpl();
      Frame frame = cf.exec().get();
      Scope.track(frame);
      Log.info(frame);
      assertNotNull(frame);
      assertEquals(0, frame.numCols());
      assertEquals(0, frame.numRows());
    } finally {
      Scope.exit();
    }
  }

  /**
   * Simple initial test: verify that the random frame can be created, that it has the correct
   * dimensions and column names.
   */
  @Test
  public void basicTest() {
    Scope.enter();
    try {
      CreateFrameSimpleIV4 s = new CreateFrameSimpleIV4().fillFromImpl();
      s.nrows = (int) (Math.random() * 200) + 50;
      s.ncols_int = (int) (Math.random() * 10) + 3;
      s.ncols_real = (int) (Math.random() * 10) + 3;
      s.ncols_bool = (int) (Math.random() * 10) + 3;
      s.ncols_enum = (int) (Math.random() * 10) + 3;
      s.ncols_time = (int) (Math.random() * 10) + 3;
      s.ncols_str = (int) (Math.random() * 5) + 2;
      SimpleCreateFrameRecipe cf = s.createAndFillImpl();
      Frame frame = cf.exec().get();
      Scope.track(frame);
      assertNotNull(frame);
      assertEquals(s.nrows, frame.numRows());
      for (int i = frame.numCols() - 1; i >= 0; i--) {
        char firstLetter = frame.name(i).charAt(0);
        int num = Integer.parseInt(frame.name(i).substring(1));
        Vec v = frame.vec(i);
        switch (firstLetter) {
          case 'B':
            assertTrue(v.isBinary());
            assertEquals(num, s.ncols_bool--);
            break;
          case 'E':
            assertTrue(v.isCategorical());
            assertEquals(num, s.ncols_enum--);
            break;
          case 'I':
            assertTrue(v.isInt());
            assertEquals(num, s.ncols_int--);
            break;
          case 'R':
            assertTrue(v.isNumeric() && !v.isInt());
            assertEquals(num, s.ncols_real--);
            break;
          case 'S':
            assertTrue(v.isString());
            assertEquals(num, s.ncols_str--);
            break;
          case 'T':
            assertTrue(v.isTime());
            assertEquals(num, s.ncols_time--);
            break;
        }
      }
      assertTrue(s.ncols_bool == 0 && s.ncols_enum == 0 && s.ncols_int == 0 && s.ncols_real == 0 &&
                 s.ncols_str == 0 && s.ncols_time == 0);
      Log.info(frame.toString());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testResponses() {
    Scope.enter();
    try {
      CreateFrameSimpleIV4 s = new CreateFrameSimpleIV4().fillFromImpl();
      s.nrows = 10;
      s.ncols_int = 1;
      s.ncols_real = 1;
      s.ncols_bool = 1;
      for (ResponseType rt : ResponseType.values()) {
        if (rt == ResponseType.NONE) continue;
        s.response_type = rt;
        SimpleCreateFrameRecipe cf = s.createAndFillImpl();
        Frame frame = cf.exec().get();
        Scope.track(frame);

        assertNotNull(frame);
        assertEquals(10, frame.numRows());
        assertEquals(4, frame.numCols());
        assertEquals("response", frame.name(0));
        Vec vres = frame.vec(0);
        switch (rt) {
          case BOOL:
            assertTrue(vres.isBinary());
            break;
          case INT:
            assertTrue(vres.isInt());
            break;
          case TIME:
            assertTrue(vres.isTime());
            break;
          case ENUM:
            assertTrue(vres.isCategorical());
            break;
          case REAL:
            assertTrue(vres.isNumeric());
            break;
        }
      }
    } finally {
      Scope.exit();
    }
  }

}
