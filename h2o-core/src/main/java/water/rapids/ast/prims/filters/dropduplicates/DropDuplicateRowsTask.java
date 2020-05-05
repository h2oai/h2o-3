package water.rapids.ast.prims.filters.dropduplicates;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;

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
                equal = compareRows(chunks, row);
            }

            if (!equal) {
                for (int columnIndex = 0; columnIndex < chunks.length; columnIndex++) {
                    chunks[columnIndex].extractRows(newChunks[columnIndex], row);
                }
            }

        }
    }

    private boolean compareFirstRowWithPreviousChunk(final Chunk[] chunks, final int row, final int chunkId) {
        for (int column : comparedColumnIndices) {
            final double previousValue = chunkBoundaries.vec(column).chunkForChunkIdx(chunkId - 1).atd(0);
            final double curentValue = chunks[column].atd(row);
            if (previousValue != curentValue) return false;
        }

        return true;
    }

    private boolean compareRows(final Chunk[] chunks, final int row) {
        for (final int column : comparedColumnIndices) {
            final double previousValue = chunks[column].atd(row - 1);
            final double curentValue = chunks[column].atd(row);
            if (previousValue != curentValue) return false;
        }

        return true;
    }

}
