package water.fvec;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

import static org.junit.Assert.assertTrue;

/**
 * Tests for Frame.java
 */
public class FrameTest extends TestUtil {
  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void testNonEmptyChunks() {
    try {
      Scope.enter();
      final Frame train1 = Scope.track(new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "Response")
              .withVecTypes(Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ard(1, 2, 3, 4, 0))
              .withDataForCol(1, ar("A", "B", "C", "A", "B"))
              .withChunkLayout(1, 0, 0, 2, 1, 0, 1)
              .build());
      assertEquals(4, train1.anyVec().nonEmptyChunks());
      final Frame train2 = Scope.track(new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "Response")
              .withVecTypes(Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ard(1, 2, 3, 4, 0))
              .withDataForCol(1, ar("A", "B", "C", "A", "B"))
              .withChunkLayout(1, 2, 1, 1)
              .build());
      assertEquals(4, train2.anyVec().nonEmptyChunks());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testRemoveColumn() {
    Scope.enter();
    Frame testData = parse_test_file(Key.make("test_deep_select_1"), "smalldata/sparse/created_frame_binomial.svm.zip");
    Set<Vec> removedVecs = new HashSet<>();

    try {
      // dataset to split
      int initialSize = testData.numCols();
      removedVecs.add(testData.remove(-1));
      assertEquals(initialSize, testData.numCols());
      removedVecs.add(testData.remove(0));
      assertEquals(initialSize - 1, testData.numCols());
      assertEquals("C2", testData._names[0]);
      removedVecs.add(testData.remove(initialSize - 2));
      assertEquals(initialSize - 2, testData.numCols());
      assertEquals("C" + (initialSize - 1), testData._names[initialSize - 3]);
      removedVecs.add(testData.remove(42));
      assertEquals(initialSize - 3, testData.numCols());
      assertEquals("C43", testData._names[41]);
      assertEquals("C45", testData._names[42]);
    } finally {
      Scope.exit();
      for (Vec v : removedVecs) if (v != null) v.remove();
      testData.delete();
      H2O.STORE.clear();
    }
  }

  // _names=C1,... - C10001
  @Ignore
  @Test public void testDeepSelectSparse() {
    Scope.enter();
    // dataset to split
    Frame testData = parse_test_file(Key.make("test_deep_select_1"), "smalldata/sparse/created_frame_binomial.svm.zip");
    // premade splits from R
    Frame subset1 = parse_test_file(Key.make("test_deep_select_2"), "smalldata/sparse/data_split_1.svm.zip");
    // subset2 commented out to save time
//    Frame subset2 = parse_test_file(Key.make("test_deep_select_3"),"smalldata/sparse/data_split_2.svm");
    // predicates (0: runif 1:runif < .5 2: runif >= .5
    Frame rnd = parse_test_file(Key.make("test_deep_select_4"), "smalldata/sparse/rnd_r.csv");
    Frame x = null;
    Frame y = null;
    try {
      x = testData.deepSlice(new Frame(rnd.vec(1)), null);
//      y = testData.deepSlice(new Frame(rnd.vec(2)),null);
      assertTrue(TestUtil.isBitIdentical(subset1, x));
//      assertTrue(isBitIdentical(subset2,y));
    } finally {
      Scope.exit();
      testData.delete();
      rnd.delete();
      subset1.delete();
//      subset2.delete();
      if (x != null) x.delete();
      if (y != null) y.delete();
    }
  }

  /**
   * This test is testing deepSlice functionality and shows that we can use zero-based indexes for slicing
   * // TODO if confirmed go and correct comments for Frame.deepSlice() method
   */
  @Test
  public void testRowDeepSlice() {
    Scope.enter();
    try {
      long[] numericalCol = ar(1, 2, 3, 4);
      Frame input = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB")
              .withVecTypes(Vec.T_STR, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "c", "d"))
              .withDataForCol(1, numericalCol)
              .withChunkLayout(numericalCol.length)
              .build();

      // Single number row slice
      Frame sliced = input.deepSlice(new long[]{1}, null);
      assertEquals(1, sliced.numRows());
      assertEquals("b", sliced.vec(0).stringAt(0));
      assertEquals(2, sliced.vec(1).at(0), 1e-5);

      //checking that 0-based indexing is allowed as well
      // We are slicing here particular indexes of rows : 0 and 3
      Frame slicedRange = input.deepSlice(new long[]{0, 3}, null);
      printOutFrameAsTable(slicedRange, false, slicedRange.numRows());
      assertEquals(2, slicedRange.numRows());
      assertStringVecEquals(svec("a", "d"), slicedRange.vec(0));
      assertVecEquals(vec(1,4), slicedRange.vec(1), 1e-5);

      //TODO add test for new long[]{-4} values
    } finally {
      Scope.exit();
    }
  }

  // This test keeps failing locally when we run the whole suite but green if we run it alone.
  // Vec$ESPC.rowLayout is using shared state... see jira PUBDEV-6019
  @Ignore
  @Test
  public void testRowDeepSliceWithPredicateFrame() {
    Scope.enter();
    try {
      long[] numericalCol = ar(1, 2, 3, 4);
      Frame input = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB")
              .withVecTypes(Vec.T_STR, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "c", "d"))
              .withDataForCol(1, numericalCol)
              .withChunkLayout(numericalCol.length)
              .build();

      // Single number row slice
      Frame sliced = input.deepSlice(new Frame(vec(0, 1, 0, 0)), null);
      assertEquals(1, sliced.numRows());
      assertEquals("b", sliced.vec(0).stringAt(0));
      assertEquals(2, sliced.vec(1).at(0), 1e-5);

      //checking that 0-based indexing is allowed as well
      Frame slicedRange = input.deepSlice(new Frame(vec(1, 0, 0, 1)), null);
      assertEquals(2, slicedRange.numRows());
      assertStringVecEquals(svec("a", "d"), slicedRange.vec(0));
      assertVecEquals(vec(1,4), slicedRange.vec(1), 1e-5);

      //TODO add test for new long[]{-4} values
    } finally {
      Scope.exit();
    }
  }

  @Test // deep select filters out all defined values of the chunk and the only left ones are NAs, eg.: c(1, NA, NA) -> c(NA, NA)
  public void testDeepSelectNAs() {
    Scope.enter();
    try {
      String[] data = new String[2 /*defined*/ + 17 /*undefined*/];
      data[0] = "A";
      data[data.length - 1] = "Z";
      double[] pred = new double[data.length];
      Arrays.fill(pred, 1.0);
      pred[0] = 0;
      pred[data.length - 1] = 0;
      Frame input = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "predicate")
              .withVecTypes(Vec.T_STR, Vec.T_NUM)
              .withDataForCol(0, data)
              .withDataForCol(1, pred)
              .withChunkLayout(data.length) // single chunk
              .build();
      Scope.track(input);
      Frame result = new Frame.DeepSelect().doAll(Vec.T_STR, input).outputFrame();
      Scope.track(result);
      assertEquals(data.length - 2, result.numRows());
      for (int i = 0; i < data.length - 2; i++)
        assertTrue("Value in row " + i + " is NA", result.vec(0).isNA(i));
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testFinalizePartialFrameRemovesTrailingChunks() {
    final String fName = "part_frame";
    final long[] layout = new long[]{0, 1, 0, 3, 2, 0, 0, 0};

    try {
      Scope.enter();
      Key<Frame> fKey = Key.make(fName);
      Frame f = new Frame(fKey);
      f.preparePartialFrame(new String[]{"C0"});
      Scope.track(f);
      f.update();

      for (int i = 0; i < layout.length; i++) {
        FrameTestUtil.createNC(fName, i, (int) layout[i], new byte[]{Vec.T_NUM});
      }
      f = DKV.get(fName).get();

      f.finalizePartialFrame(layout, new String[][] {null}, new byte[]{Vec.T_NUM});

      final long[] expectedESPC = new long[]{0, 0, 1, 1, 4, 6};
      assertArrayEquals(expectedESPC, f.anyVec().espc());

      Frame f2 = Scope.track(new MRTask(){
        @Override
        public void map(Chunk c, NewChunk nc) {
          for (int i = 0; i < c._len; i++)
            nc.addNum(c.atd(i));
        }
      }.doAll(Vec.T_NUM, f).outputFrame());

      // the ESPC is the same
      assertArrayEquals(expectedESPC, f2.anyVec().espc());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void deepCopyFrameTest() {
    Scope.enter();
    try {
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b"))
              .withDataForCol(1, ar("c", "d"))
              .build();

      Frame newFrame = fr.deepCopy(Key.make().toString());

      fr.delete();
      assertStringVecEquals(newFrame.vec("ColB"), cvec("c", "d"));

    } finally {
      Scope.exit();
    }
  }
}
