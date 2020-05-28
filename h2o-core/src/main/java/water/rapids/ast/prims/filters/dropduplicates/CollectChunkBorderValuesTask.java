package water.rapids.ast.prims.filters.dropduplicates;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.NewChunk;

/**
 * Collects border values from a chunk - last element of each chunk.
 */
public class CollectChunkBorderValuesTask extends MRTask<CollectChunkBorderValuesTask> {

  @Override
  public void map(final Chunk[] oldChunks, NewChunk[] newChunks) {
    for (int columnIndex = 0; columnIndex < oldChunks.length; columnIndex++) {
      oldChunks[columnIndex].extractRows(newChunks[columnIndex], oldChunks[columnIndex].len() - 1);
    }
  }
}
