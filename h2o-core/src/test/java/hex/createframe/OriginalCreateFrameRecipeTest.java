package hex.createframe;

import hex.createframe.recipes.OriginalCreateFrameRecipe;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.api.schemas4.input.CreateFrameOriginalIV4;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;

import static org.junit.Assert.*;


/**
 * Test for the {@link OriginalCreateFrameRecipe} class (and the overall {@link CreateFrameExecutor} mechanism).
 */
public class OriginalCreateFrameRecipeTest extends TestUtil {

  @BeforeClass() public static void setup() {
    stall_till_cloudsize(1);
  }

  /**
   * Simple initial test: verify that the random frame can be created, that it has the correct
   * dimensions and column names (response, C1, C2, C3, ...)
   */
  @Test public void basicTest() {
    CreateFrameOriginalIV4 s = new CreateFrameOriginalIV4().fillFromImpl();
    s.rows = (int)(Math.random() * 200) + 50;
    s.cols = (int)(Math.random() * 10) + 5;
    s.categorical_fraction = 0.1;
    s.integer_fraction = 1 - s.categorical_fraction;
    s.binary_fraction = 0;
    s.factors = 4;
    s.response_factors = 2;
    s.positive_response = false;
    s.has_response = true;
    s.seed = 1234;
    OriginalCreateFrameRecipe cf = s.createAndFillImpl();
    Frame frame = cf.exec().get();
    assertNotNull(frame);
    assertEquals(s.cols + 1, frame.numCols());
    assertEquals(s.rows, frame.numRows());
    assertEquals("response", frame.name(0));
    for (int i = 1; i < s.cols; i++)
      assertEquals("C" + i, frame.name(i));
    Log.info(frame.toString());
    frame.delete();
  }

  /**
   * Creates frame with binary columns, and test that the <code>binary_ones_fraction</code> setting is respected.
   * This test is non-deterministic and may fail with probability 0.001%.
   */
  @Test public void binaryFrameTest() {
    CreateFrameOriginalIV4 s = new CreateFrameOriginalIV4().fillFromImpl();
    s.rows = 25000;
    s.cols = 6;
    s.categorical_fraction = 0;
    s.integer_fraction = 0;
    s.binary_fraction = 1;
    s.binary_ones_fraction = 0.2;
    s.missing_fraction = 0;
    s.has_response = true;
    s.response_factors = 2;  // binomial response
    Frame frame = s.createAndFillImpl().exec().get();
    assertNotNull(frame);
    assertEquals("response", frame.name(0));
    assertEquals(s.cols + 1, frame.numCols());
    assertEquals(s.rows, frame.numRows());
    long totalCount = 0;
    for (int i = 0; i < s.cols + 1; i++) {
      assertTrue(frame.vec(i).isBinary());
      if (i > 0)  // response column is skipped because its proportion of 1s is always 0.5
        totalCount += Math.round(s.rows * frame.vec(i).mean());
    }
    double N = s.rows * s.cols;
    double p = s.binary_ones_fraction;
    double ttest = Math.abs(totalCount - N * p) / Math.sqrt(N * p * (1 - p));
    assertTrue("Count of 1s is more than 4.417 sigmas away from the expected value: t = " + ttest,
        ttest < 4.417);
    frame.delete();
  }

  /**
   * Test that the produced number of missing values is the same as requested.
   */
  @Test public void missingValuesTest() {
    CreateFrameOriginalIV4 s = new CreateFrameOriginalIV4().fillFromImpl();
    s.rows = 25000;
    s.cols = 4;
    s.categorical_fraction = 0;
    s.integer_fraction = 0;
    s.binary_fraction = 0;
    s.string_fraction = 0;
    s.time_fraction = 0;
    s.missing_fraction = 0.1;
    s.has_response = true;
    s.response_factors = 1;
    Frame frame = s.createAndFillImpl().exec().get();
    assertNotNull(frame);
    assertEquals(s.cols + 1, frame.numCols());
    assertEquals(s.rows, frame.numRows());
    long missingCount = 0;
    for (int i = 0; i < s.cols + 1; i++) {
      missingCount += frame.vec(i).naCnt();
    }
    double N = s.rows * (s.cols + 1);
    double p = s.missing_fraction;
    double ttest = Math.abs(missingCount - N * p) / Math.sqrt(N * p * (1 - p));
    assertTrue("Count of NAs is more than 4.417 sigmas away from the expected value: t = " + ttest,
        ttest < 4.417);
    frame.delete();
  }

  /**
   * Test that columns of all types can be created, and that there is the correct number of each
   * in the resulting frame.
   */
  @Test public void testAllColumnTypes() {
    CreateFrameOriginalIV4 s = new CreateFrameOriginalIV4().fillFromImpl();
    s.rows = 100;
    s.cols = 100;
    s.categorical_fraction = 0.10000000000001;
    s.integer_fraction = 0.099999999999998;
    s.binary_fraction = 0.10000000000003;
    s.time_fraction = 0.1200045762024587;
    s.string_fraction = 0.16000204587202;
    s.binary_ones_fraction = 0.1;
    s.factors = 5;
    s.response_factors = 5;  // response is also categorical
    s.positive_response = false;
    s.has_response = true;
    s.seed = 1234567;
    Frame frame = s.createAndFillImpl().exec().get();
    assertNotNull(frame);
    assertEquals("response", frame.name(0));
    assertEquals(s.cols + 1, frame.numCols());
    assertEquals(s.rows, frame.numRows());
    assertEquals(Math.round(s.cols * s.categorical_fraction) + 1, countVecsOfType(frame, "enum"));
    assertEquals(Math.round(s.cols * s.time_fraction), countVecsOfType(frame, "time"));
    assertEquals(Math.round(s.cols * s.string_fraction), countVecsOfType(frame, "str"));
    assertEquals(Math.round(s.cols * s.integer_fraction), countVecsOfType(frame, "int"));
    assertEquals(Math.round(s.cols * s.binary_fraction), countVecsOfType(frame, "bool"));
    Log.info(frame.toString());
    frame.delete();
  }

  /**
   * This test attempts to create the same dataset twice starting from the same seed, and then checks that
   * the result came out exactly the same both times.
   * We also verify that the test frame has multiple chunks, since most of the breakages will happen because of
   * nondeterministic chunk execution.
   */
  @Test public void testReproducibility() {
    CreateFrameOriginalIV4 s = new CreateFrameOriginalIV4().fillFromImpl();
    s.rows = 5000;
    s.cols = 20;
    s.time_fraction = 0.1;
    s.categorical_fraction = 0.2;
    s.integer_fraction = 0.2;
    s.binary_fraction = 0.2;
    s.string_fraction = 0.1;
    s.missing_fraction = 0.05;
    s.has_response = false;
    s.seed = (long)(Math.random() * 100000000000L);
    Log.info("Using seed " + s.seed);
    Frame frame1 = s.createAndFillImpl().exec().get();
    assertNotNull(frame1);
    Log.info(frame1.toString());
    assertTrue("Please adjust test parameters to have more than 1 chunk in the frame", frame1.vec(0).nChunks() > 1);
    Frame frame2 = s.createAndFillImpl().exec().get();
    assertNotNull(frame2);

    assertTrue(isBitIdentical(frame1, frame2));
    frame1.delete();
    frame2.delete();
  }

  private static int countVecsOfType(Frame fr, String type) {
    int count = 0;
    for (Vec v : fr.vecs()) {
      boolean test = false;
      switch (type) {
        case "enum": test = v.isCategorical(); break;
        case "time": test = v.isTime(); break;
        case "str":  test = v.isString(); break;
        case "int":  test = v.isInt() && !v.isTime() && !v.isCategorical() && !v.isBinary(); break;
        case "bool": test = v.isBinary(); break;
      }
      if (test) count++;
    }
    return count;
  }
}
