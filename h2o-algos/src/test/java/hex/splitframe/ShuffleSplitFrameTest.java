package hex.splitframe;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.FrameTestUtil;
import water.fvec.VecAry;

import java.util.Arrays;

import static water.fvec.FrameTestUtil.collectS;
import static water.util.ArrayUtils.flat;
import static water.util.ArrayUtils.append;

/**
 * Tests for shuffle split frame.
 */
public class ShuffleSplitFrameTest extends TestUtil {

  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  /** Reported as PUBDEV-452 */
  @Test
  public void testShuffleSplitOnStringColumn() {
    long[] chunkLayout = ar(2L, 2L, 3L);
    String[][] data = ar(ar("A", "B"), ar(null, "C"), ar("D", "E", "F"));
    Vec v = FrameTestUtil.makeStringVec(data);
    Frame f = new Frame(Key.make("test1.hex"), new Frame.Names("data"), new VecAry(v));
    testScenario(f, flat(data));

    chunkLayout = ar(3L, 3L);
    data = ar(ar("A", null, "B"), ar("C", "D", "E"));
    v = FrameTestUtil.makeStringVec(data);
    f = new Frame(Key.make("test2.hex"), new Frame.Names("data"), new VecAry(v));
    testScenario(f, flat(data));
  }

  /** Simple testing scenario, splitting frame in the middle and comparing the values */
  static void testScenario(Frame f, String[] expValues) {
    double[] ratios = ard(0.5, 0.5);
    Key[] keys = aro(Key.make("test.hex"), Key.make("train.hex"));
    Frame[] splits = null;
    try {
      splits = ShuffleSplitFrame.shuffleSplitFrame(f, keys, ratios, 42);
      Assert.assertEquals("Expecting 2 splits", 2, splits.length);
      // Collect values from both splits
      String[] values = append(
              collectS(splits[0].vecs()),
              collectS(splits[1].vecs()));
      // Sort values, but first replace all nulls by unique value
      Arrays.sort(replaceNulls(expValues));
      Arrays.sort(replaceNulls(values));
      Assert.assertArrayEquals("Values should match", expValues, values);
    } finally {
      f.delete();
      if (splits!=null) for(Frame s: splits) s.delete();
    }
  }

  private static String[] replaceNulls(String[] ary) {
    return replaceNulls(ary, "_NA_#");
  }
  private static String[] replaceNulls(String[] ary, String replacement) {
    for (int i = 0; i < ary.length; i++ ) {
      if (ary[i] == null) ary[i] = replacement;
    }
    return ary;
  }

}

