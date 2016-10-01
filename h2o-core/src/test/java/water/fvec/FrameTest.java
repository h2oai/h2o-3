package water.fvec;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.H2O;
import water.Key;
import water.Scope;
import water.TestUtil;

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

}
