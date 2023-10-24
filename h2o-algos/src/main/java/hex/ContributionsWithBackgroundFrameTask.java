package hex;

import water.*;
import water.fvec.*;
import water.util.Log;
import water.util.fp.Function;

import java.util.*;
import java.util.stream.IntStream;

import static water.SplitToChunksApplyCombine.concatFrames;

/***
 * Calls map(Chunk[] frame, Chunk[] background, NewChunk[] ncs) by copying the smaller frame across the nodes.
 * @param <T>
 */
public abstract class ContributionsWithBackgroundFrameTask<T extends ContributionsWithBackgroundFrameTask<T>> extends MRTask<T> {
  transient Frame _frame;
  transient Frame _backgroundFrame;
  Key<Frame> _frameKey;
  Key<Frame> _backgroundFrameKey;

  final boolean _aggregate;

  boolean _isFrameBigger;

  long _startRow;
  long _endRow;
  Job _job;
  
  public ContributionsWithBackgroundFrameTask(Key<Frame> frKey, Key<Frame> backgroundFrameKey, boolean perReference) {
    assert null != frKey.get();
    assert null != backgroundFrameKey.get();

    _frameKey = frKey;
    _backgroundFrameKey = backgroundFrameKey;

    _frame = frKey.get();
    _backgroundFrame = backgroundFrameKey.get();
    assert _frame.numRows() > 0 : "Frame has to contain at least one row.";
    assert _backgroundFrame.numRows() > 0 : "Background frame has to contain at least one row.";

    _isFrameBigger = _frame.numRows() > _backgroundFrame.numRows();
    _aggregate = !perReference;
    _startRow = -1;
    _endRow = -1;
  }

  protected void loadFrames() {
    if (null == _frame)
      _frame = _frameKey.get();
    if (null == _backgroundFrame)
      _backgroundFrame = _backgroundFrameKey.get();
    assert _frame != null && _backgroundFrame != null;
  }

  @Override
  public void map(Chunk[] cs, NewChunk[] ncs) {
    loadFrames();
    Frame smallerFrame = _isFrameBigger ? _backgroundFrame : _frame;
    long sfIdx = 0;
    long maxSfIdx = smallerFrame.numRows();
    if (!_isFrameBigger && _startRow != -1 && _endRow != -1) {
      sfIdx = _startRow;
      maxSfIdx = _endRow;
    }

    while (sfIdx < maxSfIdx) {
      if (isCancelled() || null != _job && _job.stop_requested()) return;

      long finalSfIdx = sfIdx;
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

  public static double estimateRequiredMemory(int nCols, Frame frame, Frame backgroundFrame) {
    return 8 * nCols * frame.numRows() * backgroundFrame.numRows();
  }

  public static double estimatePerNodeMinimalMemory(int nCols, Frame frame, Frame backgroundFrame){
    boolean isFrameBigger = frame.numRows() > backgroundFrame.numRows();
    double reqMem = estimateRequiredMemory(nCols, frame, backgroundFrame);
    Frame biggerFrame = isFrameBigger ? frame : backgroundFrame;
    long[] frESPC = biggerFrame.anyVec().espc();
    // Guess the max size of the chunk from the bigger frame as 2 * average chunk
    double maxMinChunkSizeInVectorGroup = 2 * 8 * nCols *  biggerFrame.numRows() / (double) biggerFrame.anyVec().nChunks();

    // Try to compute it exactly
    if (null != frESPC) {
      long maxFr = 0;
      for (int i = 0; i < frESPC.length-1; i++) {
        maxFr = Math.max(maxFr, frESPC[i+1]-frESPC[i]);
      }
      maxMinChunkSizeInVectorGroup = Math.max(maxMinChunkSizeInVectorGroup, 8*nCols*maxFr);
    }
    long nRowsOfSmallerFrame = isFrameBigger ? backgroundFrame.numRows() : frame.numRows();

    // We need the whole smaller frame on each node and one chunk per col of the bigger frame (at minimum)
    return Math.max(reqMem / H2O.CLOUD._memary.length, maxMinChunkSizeInVectorGroup + nRowsOfSmallerFrame * nCols * 8);
  }
  
  double estimatePerNodeMinimalMemory(int nCols) {
    return estimatePerNodeMinimalMemory(nCols, _frame, _backgroundFrame);
  }


  public static long minMemoryPerNode() {
    long minMem = Long.MAX_VALUE;
    for (H2ONode h2o : H2O.CLOUD._memary) {
      long mem = h2o._heartbeat.get_free_mem(); // in bytes
      if (mem < minMem)
        minMem = mem;
    }
    return minMem;
  }

  public static long totalFreeMemory() {
    long mem = 0;
    for (H2ONode h2o : H2O.CLOUD._memary) {
      mem += h2o._heartbeat.get_free_mem(); // in bytes
    }
    return mem;
  }

  public static boolean enoughMinMemory(double estimatedMemory) {
    return minMemoryPerNode() > estimatedMemory;
  }

  abstract protected void map(Chunk[] cs, Chunk[] bgCs, NewChunk[] ncs);
  

  void setChunkRange(int startCIdx, int endCIdx) {
    assert !_isFrameBigger;
    _startRow = _frame.anyVec().chunkForChunkIdx(startCIdx).start();
    _endRow = _frame.anyVec().chunkForChunkIdx(endCIdx).start() + _frame.anyVec().chunkForChunkIdx(endCIdx)._len;
  }


  // takes care of mapping over the bigger frame 
  public Frame runAndGetOutput(Job j, Key<Frame> destinationKey, String[] names) {
    _job = j;
    loadFrames();
    double reqMem = estimateRequiredMemory(names.length + 2, _frame, _backgroundFrame);
    double reqPerNodeMem = estimatePerNodeMinimalMemory(names.length + 2);

    String[] namesWithRowIdx = new String[names.length + 2];
    System.arraycopy(names, 0, namesWithRowIdx, 0, names.length);
    namesWithRowIdx[names.length] = "RowIdx";
    namesWithRowIdx[names.length + 1] = "BackgroundRowIdx";
    Key<Frame> individualContributionsKey = _aggregate ? Key.make(destinationKey + "_individual_contribs") : destinationKey;

    if (!_aggregate) {
      if (!enoughMinMemory(reqPerNodeMem)) {
        throw new RuntimeException("Not enough memory. Estimated minimal total memory is " + reqMem + "B. " +
                "Estimated minimal per node memory (assuming perfectly balanced datasets) is " + reqPerNodeMem + "B. " +
                "Node with minimum memory has " + minMemoryPerNode() + "B. Total available memory is " + totalFreeMemory() + "B."
        );
      }
      Frame indivContribs = withPostMapAction(JobUpdatePostMap.forJob(j))
              .doAll(namesWithRowIdx.length, Vec.T_NUM, _isFrameBigger ? _frame : _backgroundFrame)
              .outputFrame(individualContributionsKey, namesWithRowIdx, null);

      return indivContribs;
    } else {
      if (!enoughMinMemory(reqPerNodeMem)) {
        if (minMemoryPerNode() < 5 * (names.length + 2) * _frame.numRows() * 8) {
          throw new RuntimeException("Not enough memory. Estimated minimal total memory is " + reqMem + "B. " +
                  "Estimated minimal per node memory (assuming perfectly balanced datasets) is " + reqPerNodeMem + "B. " +
                  "Node with minimum memory has " + minMemoryPerNode() + "B. Total available memory is " + totalFreeMemory() + "B."
          );
        }
        // Split the _frame in subsections and calculate baselines (expand the frame) and then the average (reduce the frame sieze)
        int nChunks = _frame.anyVec().nChunks();
        // last iteration we need memory for ~whole aggregated frame + expanded subframe
        int nSubFrames = (int) Math.ceil(2*reqMem / (minMemoryPerNode() - 8 * _frame.numRows() * (names.length)));
        nSubFrames = nChunks;
        int chunksPerIter = (int) Math.max(1, Math.floor(nChunks / nSubFrames));

        Log.warn("Not enough memory to calculate SHAP at once. Calculating in " + (nSubFrames) + " iterations.");
        _isFrameBigger = false; // ensure we map over the BG frame so we can average over the results properly;
        try (Scope.Safe safe = Scope.safe()) {
          List<Frame> subFrames = new LinkedList<Frame>();
          for (int i = 0; i < nSubFrames; i++) {
            setChunkRange(i * chunksPerIter, Math.min(nChunks - 1, (i + 1) * chunksPerIter - 1));
            Frame indivContribs = clone().withPostMapAction(JobUpdatePostMap.forJob(j))
                    .doAll(namesWithRowIdx.length, Vec.T_NUM, _backgroundFrame)
                    .outputFrame(Key.make(destinationKey + "_individual_contribs_" + i), namesWithRowIdx, null);

            subFrames.add(new ContributionsMeanAggregator(_job,(int) (_endRow - _startRow), names.length, (int) _backgroundFrame.numRows())
                    .setStartIndex((int) _startRow)
                    .withPostMapAction(JobUpdatePostMap.forJob(j))
                    .doAll(names.length, Vec.T_NUM, indivContribs)
                    .outputFrame(Key.make(destinationKey + "_part_" + i), names, null));
            indivContribs.delete();
          }
          
          Frame result = concatFrames(subFrames, destinationKey);
          Set<String> homes = new HashSet<>(); // not used?
          for (int i = 0; i < result.anyVec().nChunks(); i++) {
            for (int k = 0; k < result.numCols(); k++) {
              homes.add(result.vec(k).chunkKey(i).home_node().getIpPortString());
            }
          }
          return Scope.untrack(result);
        }
      } else {
        Frame indivContribs = withPostMapAction(JobUpdatePostMap.forJob(j))
                .doAll(namesWithRowIdx.length, Vec.T_NUM, _isFrameBigger ? _frame : _backgroundFrame)
                .outputFrame(individualContributionsKey, namesWithRowIdx, null);
        try {
          return new ContributionsMeanAggregator(_job, (int) _frame.numRows(), names.length, (int) _backgroundFrame.numRows())
                  .withPostMapAction(JobUpdatePostMap.forJob(j))
                  .doAll(names.length, Vec.T_NUM, indivContribs)
                  .outputFrame(destinationKey, names, null);
        } finally {
          indivContribs.delete(true);
        }
      }
    }
  }
}
