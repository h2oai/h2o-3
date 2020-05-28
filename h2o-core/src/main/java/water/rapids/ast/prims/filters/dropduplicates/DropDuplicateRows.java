package water.rapids.ast.prims.filters.dropduplicates;

import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Merge;
import water.util.ArrayUtils;
import water.util.FrameUtils;

import java.util.Arrays;

/**
 * Drops duplicated rows of a Frame
 */
public class DropDuplicateRows {

  private static final String LABEL_COLUMN_NAME = "label";

  final Frame sourceFrame;
  final int[] comparedColumnIndices;
  final KeepOrder keepOrder;

  /**
   * @param sourceFrame           Frame to perform the row de-duplication on
   * @param comparedColumnIndices Indices of columns to consider during the comparison
   * @param keepOrder             Which rows to keep.
   */
  public DropDuplicateRows(final Frame sourceFrame, final int[] comparedColumnIndices, final KeepOrder keepOrder) {
    this.sourceFrame = sourceFrame;
    this.comparedColumnIndices = comparedColumnIndices;
    this.keepOrder = keepOrder;
  }

  public Frame dropDuplicates() {
    Frame outputFrame = null;
    try {
      Scope.enter();
      FrameUtils.labelRows(sourceFrame, LABEL_COLUMN_NAME);
      final Frame sortedFrame = Scope.track(sortByComparedColumns());
      final Frame chunkBoundaries = Scope.track(new CollectChunkBorderValuesTask()
              .doAll(sortedFrame.types(), sortedFrame)
              .outputFrame());

      final Frame deDuplicatedFrame = Scope.track(new DropDuplicateRowsTask(chunkBoundaries, comparedColumnIndices)
              .doAll(sortedFrame.types(), sortedFrame)
              .outputFrame(Key.make(), sourceFrame.names(), sourceFrame.domains())); // Removing duplicates, domains remain the same

      outputFrame = Merge.sort(deDuplicatedFrame, deDuplicatedFrame.numCols() - 1);
      Scope.track(outputFrame.remove(outputFrame.numCols() - 1));
      return outputFrame;

    } finally {
      final Vec label = sourceFrame.remove(LABEL_COLUMN_NAME);
      if (label != null) {
        label.remove();
      }
      if (outputFrame != null) {
        Scope.exit(outputFrame.keys());
      } else {
        Scope.exit(); // Clean up in case of any exception/error.
      }
    }
  }


  /**
   * Creates a copy of the original dataset, sorted by all compared columns.
   * The sort is done with respect to {@link KeepOrder} value.
   *
   * @return A new Frame sorted by all compared columns.
   */
  private Frame sortByComparedColumns() {
    final int labelColumnIndex = sourceFrame.find(LABEL_COLUMN_NAME);
    final int[] sortByColumns = ArrayUtils.append(comparedColumnIndices, labelColumnIndex);
    final boolean ascendingSort = KeepOrder.First == keepOrder;

    final int[] sortOrder = new int[sortByColumns.length];
    // Compared columns are always sorted in the same order
    Arrays.fill(sortOrder, 0, sortOrder.length - 1, Merge.ASCENDING);
    // Label column is sorted differently based on DropOrder
    sortOrder[sortOrder.length - 1] = ascendingSort ? Merge.ASCENDING : Merge.DESCENDING;

    return Merge.sort(sourceFrame, sortByColumns, sortOrder);
  }
}
