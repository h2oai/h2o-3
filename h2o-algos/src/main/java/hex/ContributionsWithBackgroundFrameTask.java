package hex;

import water.Job;
import water.JobUpdatePostMap;
import water.Key;
import water.MRTask;
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

  abstract protected void map(Chunk[] cs, Chunk[] bgCs, NewChunk[] ncs);

  // takes care of mapping over the bigger frame 
  public Frame runAndGetOutput(Job j, Key<Frame> destinationKey, String[] names) {
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
