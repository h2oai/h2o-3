package water.rapids.ast.prims.filters.dropduplicates;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;

/**
 * Performs the row de-duplication itself.
 */
public class DropDuplicateRowsTask extends MRTask<DropDuplicateRowsTask> {
  final Frame chunkBoundaries;
  private final int[] comparedColumnIndices;

  /**
   * @param chunkBoundaries       Frame with border values of each chunk
   * @param comparedColumnIndices Columns indices to include in row deduplication comparison
   */
  public DropDuplicateRowsTask(final Frame chunkBoundaries, final int[] comparedColumnIndices) {
    this.chunkBoundaries = chunkBoundaries;
    this.comparedColumnIndices = comparedColumnIndices;
  }

  @Override
  public void map(Chunk[] chunks, NewChunk[] newChunks) {
    final int chunkLength = chunks[0].len();
    final int chunkId = chunks[0].cidx();
    for (int row = 0; row < chunkLength; row++) {
      final boolean equal;
      if (chunkId == 0 && row == 0) {
        for (int columnIndex = 0; columnIndex < chunks.length; columnIndex++) {
          chunks[columnIndex].extractRows(newChunks[columnIndex], row);
        }
        continue;
      } else if (chunkId != 0 && row == 0) {
        equal = compareFirstRowWithPreviousChunk(chunks, row, chunkId);
      } else {
        equal = compareRows(chunks, row, chunks, row - 1);
      }

      if (!equal) {
        for (int columnIndex = 0; columnIndex < chunks.length; columnIndex++) {
          chunks[columnIndex].extractRows(newChunks[columnIndex], row);
        }
      }

    }
  }

  private boolean compareFirstRowWithPreviousChunk(final Chunk[] chunks, final int row, final int chunkId) {
    final Chunk[] previousRowChunks = new Chunk[chunkBoundaries.numCols()];

    for (int column = 0; column < chunkBoundaries.numCols(); column++) {
      previousRowChunks[column] = chunkBoundaries.vec(column).chunkForChunkIdx(chunkId - 1);
    }
    return compareRows(chunks, row, previousRowChunks, 0);
  }

  private boolean compareRows(final Chunk[] chunksA, final int rowA, final Chunk[] chunksB, final int rowB) {
    for (final int column : comparedColumnIndices) {
      final boolean isPreviousNA = chunksA[column].isNA(rowA);
      final boolean isCurrentNA = chunksB[column].isNA(rowB);
      if (isPreviousNA || isCurrentNA) {
        return isPreviousNA && isCurrentNA;
      }

      switch (chunksA[column].vec().get_type()) {
        case Vec.T_NUM:
          final double previousDoubleValue = chunksA[column].atd(rowA);
          final double currentDoubleValue = chunksB[column].atd(rowB);
          if (previousDoubleValue != currentDoubleValue) return false;
          break;
        case Vec.T_CAT:
        case Vec.T_TIME:
          final long previousTimeValue = chunksA[column].at8(rowA);
          final long currentTimeValue = chunksB[column].at8(rowB);
          if (previousTimeValue != currentTimeValue) return false;
          break;
        default:
          throw new IllegalStateException("Unexpected value: " + chunksA[column].vec().get_type());
      }
    }

    return true;
  }

}
