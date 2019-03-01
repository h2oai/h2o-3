package hex.splitframe;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.MRTask;
import water.Scope;
import water.TestUtil;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.parser.BufferedString;

import java.util.Arrays;

import static water.fvec.FrameTestUtil.createFrame;
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
    Frame f = createFrame("ShuffleSplitTest1.hex", chunkLayout, data);
    testScenario(f, flat(data));

    chunkLayout = ar(3L, 3L);
    data = ar(ar("A", null, "B"), ar("C", "D", "E"));
    f = createFrame("test2.hex", chunkLayout, data);
    testScenario(f, flat(data));
  }

  private Frame[] getSplitsFromTitanicDataset(String responseColumnName, long splitSeed) {
    Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");

    double[] ratios = ard(0.7, 0.3);
    Key<Frame>[] keys = aro(Key.<Frame>make(), Key.<Frame>make());
    Frame[] splits = null;
    splits = ShuffleSplitFrame.shuffleSplitFrame(fr, keys, ratios, splitSeed);
    fr.delete();
    return splits;
  }
  
  @Test
  public void testThatSplitIsConsistentWithSeedBeingSet() {
    Scope.enter();
    Frame etalonTrain = null;
    Frame etalonValid = null;
    try {
      long singleSeed = 2345;
      for (int attempt = 0; attempt < 100; attempt++) {

        Frame[] splits = getSplitsFromTitanicDataset("survived", singleSeed);
        if (etalonTrain == null) {
          etalonTrain = splits[0];
          etalonValid = splits[1];
        } else {
          Assert.assertTrue(isBitIdentical(etalonTrain, splits[0]));
          Assert.assertTrue(isBitIdentical(etalonValid, splits[1]));
          splits[0].delete();
          splits[1].delete();
        }
      }
    } finally {
      etalonTrain.delete();
      etalonValid.delete();
      Scope.exit();
    }
  }

  @Test /* this test makes sure that the rows of the split frames are preserved (including UUID) */
  public void testShuffleSplitWithMultipleColumns() {
    long[] chunkLayout = ar(2L, 2L, 3L);
    String[][] data = ar(ar("1", "2"), ar(null, "3"), ar("4", "5", "6"));
    Frame f = null;
    Frame tmpFrm = createFrame("ShuffleSplitMCTest1.hex", chunkLayout, data);
    try {
      f = new MRTask() {
        @Override
        public void map(Chunk[] cs, NewChunk[] ncs) {
          for (int i = 0; i < cs[0]._len; i++) {
            BufferedString bs = cs[0].atStr(new BufferedString(), i);
            int val = bs == null ? 0 : Integer.parseInt(bs.toString());
            ncs[0].addStr(bs);
            ncs[1].addNum(val);
            ncs[2].addNum(i);
            ncs[3].addUUID(i, val);
          }
        }
      }.doAll(new byte[]{Vec.T_STR, Vec.T_NUM, Vec.T_NUM, Vec.T_UUID}, tmpFrm).outputFrame();
    } finally {
      tmpFrm.delete();
    }
    testScenario(f, flat(data), new MRTask() {
      @Override
      public void map(Chunk[] cs) {
        for (int i = 0; i < cs[0]._len; i++) {
          BufferedString bs = cs[0].atStr(new BufferedString(), i);
          int expectedVal = bs == null ? 0 : Integer.parseInt(bs.toString());
          int expectedIndex = (int) cs[2].atd(i);
          Assert.assertEquals((double) expectedVal, cs[1].atd(i), 0.00001);
          Assert.assertEquals(expectedIndex, (int) cs[3].at16l(i));
          Assert.assertEquals(expectedVal, (int) cs[3].at16h(i));
        }
      }
    });
  }

  static void testScenario(Frame f, String[] expValues) { testScenario(f, expValues, null); }

  /** Simple testing scenario, splitting frame in the middle and comparing the values */
  static void testScenario(Frame f, String[] expValues, MRTask chunkAssertions) {
    double[] ratios = ard(0.5, 0.5);
    Key<Frame>[] keys = aro(Key.<Frame>make("test.hex"), Key.<Frame>make("train.hex"));
    Frame[] splits = null;
    try {
      splits = ShuffleSplitFrame.shuffleSplitFrame(f, keys, ratios, 42);
      Assert.assertEquals("Expecting 2 splits", 2, splits.length);
      // Collect values from both splits
      String[] values = append(
              collectS(splits[0].vec(0)),
              collectS(splits[1].vec(0)));
      // Sort values, but first replace all nulls by unique value
      Arrays.sort(replaceNulls(expValues));
      Arrays.sort(replaceNulls(values));
      Assert.assertArrayEquals("Values should match", expValues, values);
      if (chunkAssertions != null) {
        for (Frame s: splits) chunkAssertions.doAll(s).getResult();
      }
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

