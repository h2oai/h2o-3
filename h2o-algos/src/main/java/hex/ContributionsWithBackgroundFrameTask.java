package hex;

import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;

import java.util.Arrays;
import java.util.stream.IntStream;

/***
 * Calls map(Chunk[] frame, Chunk[] background, NewChunk[] ncs) by copying the smaller frame across the nodes.
 * @param <T>
 */
public abstract class ContributionsWithBackgroundFrameTask<T extends ContributionsWithBackgroundFrameTask<T>> extends MRTask<T> {
  final Frame _frame;
  final Frame _backgroundFrame;
  final boolean _aggregate;

  final boolean _isFrameBigger;

  public ContributionsWithBackgroundFrameTask(Frame fr, Frame backgroundFrame, boolean perReference) {
    _frame = fr;
    _backgroundFrame = backgroundFrame;
    assert _frame.numRows() > 0 : "Frame has to contain at least one row.";
    assert _backgroundFrame.numRows() > 0 : "Background frame has to contain at least one row.";
    _isFrameBigger = fr.numRows() > backgroundFrame.numRows();
    _aggregate = !perReference;
  }


  @Override
  public void map(Chunk[] cs, NewChunk[] ncs) {
    Frame smallerFrame = _isFrameBigger ? _backgroundFrame : _frame;
    for (int sfIdx = 0; sfIdx < smallerFrame.numRows(); ) {
      int finalSfIdx = sfIdx;
      Chunk[] sfCs = IntStream
              .range(0, smallerFrame.numCols())
              .mapToObj(col -> smallerFrame.vec(col).chunkForRow(finalSfIdx))
              .toArray(Chunk[]::new);
      NewChunk[] ncsSlice = Arrays.copyOf(ncs, ncs.length - 2);
      if (_isFrameBigger) {
        map(cs, sfCs, ncsSlice);
        for (int i = 0; i < cs[0]._len; i++) {
          for (int j = 0; j < sfCs[0]._len; j++) {
            ncs[ncs.length - 2].addNum(cs[0].start() + i); // row idx
            ncs[ncs.length - 1].addNum(sfCs[0].start() + j); // background idx
          }
        }
      } else {
        map(sfCs, cs, ncsSlice);
        for (int i = 0; i < sfCs[0]._len; i++) {
          for (int j = 0; j < cs[0]._len; j++) {
            ncs[ncs.length - 2].addNum(sfCs[0].start() + i); // row idx
            ncs[ncs.length - 1].addNum(cs[0].start() + j); // background idx
          }
        }
      }
      sfIdx += sfCs[0]._len;
    }
  }

  double estimateRequiredMemory(int nCols) {
    return 8 * nCols * _frame.numRows() * _backgroundFrame.numRows();
  }

  double estimatePerNodeMinimalMemory(int nCols) {
    double reqMem = estimateRequiredMemory(nCols);
    double maxMinChunkSizeInVectorGroup = Math.min(reqMem, 10000 * 8 * nCols);

    long nRowsOfSmallerFrame = _isFrameBigger ? _backgroundFrame.numRows() : _frame.numRows();

    // We need the whole smaller frame on each node and one chunk per col of the bigger frame (at minimum)
    return Math.max(reqMem / H2O.CLOUD._memary.length, maxMinChunkSizeInVectorGroup + nRowsOfSmallerFrame * nCols * 8);
  }


  long minMemoryPerNode() {
    long minMem = Long.MAX_VALUE;
    for (H2ONode h2o : H2O.CLOUD._memary) {
      long mem = h2o._heartbeat.get_free_mem(); // in bytes
      if (mem < minMem)
        minMem = mem;
    }
    return minMem;
  }

  long totalFreeMemory() {
    long mem = 0;
    for (H2ONode h2o : H2O.CLOUD._memary) {
      mem += h2o._heartbeat.get_free_mem(); // in bytes
    }
    return mem;
  }

  boolean enoughMinMemory(double estimatedMemory) {
    return minMemoryPerNode() > estimatedMemory;
  }

  abstract protected void map(Chunk[] cs, Chunk[] bgCs, NewChunk[] ncs);

  // takes care of mapping over the bigger frame 
  public Frame runAndGetOutput(Job j, Key<Frame> destinationKey, String[] names) {
    double reqMem = estimateRequiredMemory(names.length + 2);
    double reqPerNodeMem = estimatePerNodeMinimalMemory(names.length + 2);
    if (!enoughMinMemory(reqPerNodeMem)) {
      throw new RuntimeException("Not enough memory. Estimated minimal total memory is " + reqMem + "B. " +
              "Estimated minimal per node memory (assuming perfectly balanced datasets) is " + reqPerNodeMem + "B. " +
              "Node with minimum memory has " + minMemoryPerNode() + "B. Total available memory is " + totalFreeMemory() + "B."
      );
    }
  
    String[] namesWithRowIdx = new String[names.length + 2];
    System.arraycopy(names, 0, namesWithRowIdx, 0, names.length);
    namesWithRowIdx[names.length] = "RowIdx";
    namesWithRowIdx[names.length + 1] = "BackgroundRowIdx";
    Key<Frame> individualContributionsKey = _aggregate ? Key.make(destinationKey + "_individual_contribs") : destinationKey;
    Frame indivContribs = withPostMapAction(JobUpdatePostMap.forJob(j))
            .doAll(namesWithRowIdx.length, Vec.T_NUM, _isFrameBigger ? _frame : _backgroundFrame)
            .outputFrame(individualContributionsKey, namesWithRowIdx, null);
    if (!_aggregate)
      return indivContribs;
    try {
      return new ContributionsMeanAggregator((int) _frame.numRows(), names.length, (int)_backgroundFrame.numRows())
              .withPostMapAction(JobUpdatePostMap.forJob(j))
              .doAll(names.length, Vec.T_NUM, indivContribs)
              .outputFrame(destinationKey, names, null);
    } finally {
      indivContribs.delete(true);
    }
  }
}
