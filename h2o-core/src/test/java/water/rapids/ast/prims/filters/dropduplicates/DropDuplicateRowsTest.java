package water.rapids.ast.prims.filters.dropduplicates;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Scope;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class DropDuplicateRowsTest {

  @Test
  public void testDeduplication() {
    try {
      Scope.enter();
      final Frame frame = new TestFrameBuilder()
          .withColNames("C1", "C2", "C3")
          .withDataForCol(0, new double[]{1d, 1d, 3d})
          .withDataForCol(1, new double[]{2d, 2d, 5d})
          .withDataForCol(2, new double[]{1d, 2d, 6d})
          .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
          .build();
      final int[] comparedColumns = new int[]{0, 1};
      final DropDuplicateRows dropDuplicateRows = new DropDuplicateRows(frame, comparedColumns, KeepOrder.First);
      final Frame deduplicatedFrame = Scope.track(dropDuplicateRows.dropDuplicates());
      assertNotNull(deduplicatedFrame);
      assertEquals(frame.numCols(), deduplicatedFrame.numCols());
      assertEquals(2, deduplicatedFrame.numRows());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testDuplicatedRowsSortedSingleChunk() {
    try {
      Scope.enter();
      final Frame frame = new TestFrameBuilder()
          .withColNames("C1", "C2")
          .withDataForCol(0, new double[]{1d, 1d, 1d, 1d, 1d, 2d, 2d})
          .withDataForCol(1, new double[]{2d, 2d, 2d, 2d, 2d, 3d, 3d})
          .withChunkLayout(7)
          .withVecTypes(Vec.T_NUM, Vec.T_NUM)
          .build();
      final int[] comparedColumns = new int[]{0, 1};
      final DropDuplicateRows dropDuplicateRows = new DropDuplicateRows(frame, comparedColumns, KeepOrder.First);
      final Frame deduplicatedFrame = Scope.track(dropDuplicateRows.dropDuplicates());
      assertNotNull(deduplicatedFrame);
      assertEquals(frame.numCols(), deduplicatedFrame.numCols());
      assertEquals(2, deduplicatedFrame.numRows());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testDuplicateValuesOnChunkBoundary() {
    try {
      Scope.enter();
      final Frame frame = new TestFrameBuilder()
          .withColNames("C1", "C2")
          .withDataForCol(0, new double[]{1d, 1d, 1d, 1d, 1d, 2d, 2d})
          .withDataForCol(1, new double[]{2d, 2d, 2d, 2d, 2d, 3d, 4d})
          .withChunkLayout(3, 4)
          .withVecTypes(Vec.T_NUM, Vec.T_NUM)
          .build();
      final int[] comparedColumns = new int[]{0, 1};
      final DropDuplicateRows dropDuplicateRows = new DropDuplicateRows(frame, comparedColumns, KeepOrder.First);
      final Frame deduplicatedFrame = Scope.track(dropDuplicateRows.dropDuplicates());
      assertNotNull(deduplicatedFrame);
      assertEquals(frame.numCols(), deduplicatedFrame.numCols());
      assertEquals(3, deduplicatedFrame.numRows());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testDuplicateValuesSingleChunk() {
    try {
      Scope.enter();
      final Frame frame = new TestFrameBuilder()
          .withColNames("C1", "C2")
          .withDataForCol(0, new double[]{1d, 1d})
          .withDataForCol(1, new double[]{2d, 2d})
          .withChunkLayout(2)
          .withVecTypes(Vec.T_NUM, Vec.T_NUM)
          .build();
      final int[] comparedColumns = new int[]{0, 1};
      final DropDuplicateRows dropDuplicateRows = new DropDuplicateRows(frame, comparedColumns, KeepOrder.First);
      final Frame deduplicatedFrame = Scope.track(dropDuplicateRows.dropDuplicates());
      assertNotNull(deduplicatedFrame);
      assertEquals(frame.numCols(), deduplicatedFrame.numCols());
      assertEquals(1, deduplicatedFrame.numRows());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testSingleDuplicateTwoChunks() {
    try {
      Scope.enter();
      final Frame frame = new TestFrameBuilder()
          .withColNames("C1", "C2")
          .withDataForCol(0, new double[]{1d, 1d, 1d, 1d, 1d, 1d})
          .withDataForCol(1, new double[]{2d, 2d, 2d, 2d, 2d, 2d})
          .withChunkLayout(3, 3)
          .withVecTypes(Vec.T_NUM, Vec.T_NUM)
          .build();
      final int[] comparedColumns = new int[]{0, 1};
      final DropDuplicateRows dropDuplicateRows = new DropDuplicateRows(frame, comparedColumns, KeepOrder.First);
      final Frame deduplicatedFrame = Scope.track(dropDuplicateRows.dropDuplicates());
      assertNotNull(deduplicatedFrame);
      assertEquals(frame.numCols(), deduplicatedFrame.numCols());
      assertEquals(1, deduplicatedFrame.numRows());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testKeepFirst() {
    try {
      Scope.enter();
      final Frame frame = new TestFrameBuilder()
          .withColNames("C1", "C2", "C3")
          .withDataForCol(0, new double[]{1d, 1d, 1d, 1d, 1d, 1d})
          .withDataForCol(1, new double[]{2d, 2d, 2d, 2d, 2d, 2d})
          .withDataForCol(2, new long[]{1, 2, 3, 4, 5, 6}) // Last label should be kept
          .withChunkLayout(3, 3)
          .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
          .build();
      final int[] comparedColumns = new int[]{0, 1};
      final DropDuplicateRows dropDuplicateRows = new DropDuplicateRows(frame, comparedColumns, KeepOrder.First);
      final Frame deduplicatedFrame = Scope.track(dropDuplicateRows.dropDuplicates());
      assertNotNull(deduplicatedFrame);
      assertEquals(frame.numCols(), deduplicatedFrame.numCols());
      assertEquals(1, deduplicatedFrame.numRows());
      assertEquals(1L, deduplicatedFrame.vec("C3").at8(0)); // First label is in place

      assertNotNull(DKV.get(frame._key));
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testKeepLast() {
    try {
      Scope.enter();
      final Frame frame = new TestFrameBuilder()
          .withColNames("C1", "C2", "C3")
          .withDataForCol(0, new double[]{1d, 1d, 1d, 1d, 1d, 1d})
          .withDataForCol(1, new double[]{2d, 2d, 2d, 2d, 2d, 2d})
          .withDataForCol(2, new long[]{1, 2, 3, 4, 5, 6}) // First label should be kept
          .withChunkLayout(3, 3)
          .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
          .build();
      final int[] comparedColumns = new int[]{0, 1};
      final DropDuplicateRows dropDuplicateRows = new DropDuplicateRows(frame, comparedColumns, KeepOrder.Last);
      final Frame deduplicatedFrame = Scope.track(dropDuplicateRows.dropDuplicates());
      assertNotNull(deduplicatedFrame);
      assertEquals(frame.numCols(), deduplicatedFrame.numCols());
      assertEquals(6L, deduplicatedFrame.vec("C3").at8(0)); // Last label is in place

      assertNotNull(DKV.get(frame._key));
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testDeduplicationWitNaNs() {
    try {
      Scope.enter();
      final Frame frame = new TestFrameBuilder()
              .withColNames("C1", "C2")
              .withDataForCol(0, new double[]{Float.NaN, Float.NaN, 3d})
              .withDataForCol(1, new double[]{Float.NaN, Float.NaN, 5d})
              .withVecTypes(Vec.T_NUM, Vec.T_NUM)
              .build();
      final int[] comparedColumns = new int[]{0, 1};
      final DropDuplicateRows dropDuplicateRows = new DropDuplicateRows(frame, comparedColumns, KeepOrder.First);
      final Frame deduplicatedFrame = Scope.track(dropDuplicateRows.dropDuplicates());
      assertNotNull(deduplicatedFrame);
      assertEquals(frame.numCols(), deduplicatedFrame.numCols());
      assertEquals(2, deduplicatedFrame.numRows());
      assertTrue(deduplicatedFrame.vec("C1").isNA(0));
      assertTrue(deduplicatedFrame.vec("C2").isNA(0));
      assertEquals(3, deduplicatedFrame.vec("C1").at(1), 0d);
      assertEquals(5, deduplicatedFrame.vec("C2").at(1), 0d);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testDeduplicationWitCategoricalNaNs() {
    try {
      Scope.enter();
      final Frame frame = new TestFrameBuilder()
              .withColNames("C1", "C2")
              .withDataForCol(0, new String[]{null, null, "CAT1"})
              .withDataForCol(1, new String[]{null, null, "CAT1"})
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .build();
      final int[] comparedColumns = new int[]{0, 1};
      final DropDuplicateRows dropDuplicateRows = new DropDuplicateRows(frame, comparedColumns, KeepOrder.First);
      final Frame deduplicatedFrame = Scope.track(dropDuplicateRows.dropDuplicates());
      assertNotNull(deduplicatedFrame);
      assertEquals(frame.numCols(), deduplicatedFrame.numCols());
      assertEquals(2, deduplicatedFrame.numRows());
      assertTrue(deduplicatedFrame.vec("C1").isNA(0));
      assertTrue(deduplicatedFrame.vec("C2").isNA(0));
      assertEquals("CAT1", deduplicatedFrame.vec("C1").stringAt(1));
      assertEquals("CAT1", deduplicatedFrame.vec("C2").stringAt(1));
      assertEquals(frame.vec("C1").cardinality(), deduplicatedFrame.vec("C1").cardinality());
      assertEquals(frame.vec("C2").cardinality(), deduplicatedFrame.vec("C2").cardinality());
    } finally {
      Scope.exit();
    }
  }
  
}
