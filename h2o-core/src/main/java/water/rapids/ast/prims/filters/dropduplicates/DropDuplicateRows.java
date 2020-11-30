package water.rapids.ast.prims.filters.dropduplicates;

import water.Scope;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Merge;
import water.util.ArrayUtils;

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
      final Vec labelVec = Scope.track(Vec.makeSeq(1, sourceFrame.numRows()));
      final Frame fr = new Frame(sourceFrame);
      fr.add(LABEL_COLUMN_NAME, labelVec);
      final Frame sortedFrame = Scope.track(sortByComparedColumns(fr));
      final Frame chunkBoundaries = Scope.track(new CollectChunkBorderValuesTask()
              .doAll(sortedFrame.types(), sortedFrame)
              .outputFrame(null, sortedFrame.names(), sortedFrame.domains()));
      final Frame deDuplicatedFrame = Scope.track(new DropDuplicateRowsTask(chunkBoundaries, comparedColumnIndices)
              .doAll(sortedFrame.types(), sortedFrame)
              .outputFrame(null, sortedFrame.names(), sortedFrame.domains())); // Removing duplicates, domains remain the same
      // Before the final sorted duplicated is created, remove the unused datasets to free some space early
      chunkBoundaries.remove();
      sortedFrame.remove();
      outputFrame = Scope.track(Merge.sort(deDuplicatedFrame, deDuplicatedFrame.numCols() - 1));
      outputFrame.remove(outputFrame.numCols() - 1).remove();
      return outputFrame;
    } finally {
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
  private Frame sortByComparedColumns(Frame fr) {
    final int labelColumnIndex = fr.find(LABEL_COLUMN_NAME);
    final int[] sortByColumns = ArrayUtils.append(comparedColumnIndices, labelColumnIndex);
    final boolean ascendingSort = KeepOrder.First == keepOrder;

    final int[] sortOrder = new int[sortByColumns.length];
    // Compared columns are always sorted in the same order
    Arrays.fill(sortOrder, 0, sortOrder.length - 1, Merge.ASCENDING);
    // Label column is sorted differently based on DropOrder
    sortOrder[sortOrder.length - 1] = ascendingSort ? Merge.ASCENDING : Merge.DESCENDING;

    return Merge.sort(fr, sortByColumns, sortOrder);
  }
}
