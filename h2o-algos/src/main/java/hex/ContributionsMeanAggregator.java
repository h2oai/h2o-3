package hex;

import water.Job;
import water.MRTask;
import water.MemoryManager;
import water.fvec.Chunk;
import water.fvec.NewChunk;

import java.util.stream.Stream;

public class ContributionsMeanAggregator extends MRTask<ContributionsMeanAggregator> {
  final int _nBgRows;
  double[][] _partialSums;
  final int _rowIdxIdx;
  final int _nRows;
  final int _nCols;
  int _startIndex;
  final Job _j;

  public ContributionsMeanAggregator(Job j, int nRows, int nCols, int nBgRows) {
    _j = j;
    _nRows = nRows;
    _nCols = nCols;
    _rowIdxIdx = nCols;
    _nBgRows = nBgRows;
    _startIndex = 0;
  }

  public ContributionsMeanAggregator setStartIndex(int startIndex) {
    _startIndex = startIndex;
    return this;
  }

  @Override
  public void map(Chunk[] cs, NewChunk[] ncs) {
    if (isCancelled() || null != _j && _j.stop_requested()) return;
      _partialSums = MemoryManager.malloc8d(_nRows, _nCols);
    for (int i = 0; i < cs[0]._len; i++) {
      final int rowIdx = (int) cs[_rowIdxIdx].at8(i);

      for (int j = 0; j < _nCols; j++) {
        _partialSums[rowIdx - _startIndex][j] += cs[j].atd(i);
      }
    }
  }

  @Override
  public void reduce(ContributionsMeanAggregator mrt) {
    for (int i = 0; i < _partialSums.length; i++) {
      for (int j = 0; j < _partialSums[0].length; j++) {
        _partialSums[i][j] += mrt._partialSums[i][j];
      }
    }
    mrt._partialSums = null;
  }

  @Override
  protected void postGlobal() {
    NewChunk[] ncs = Stream.of(appendables()).map(vec -> vec.chunkForChunkIdx(0)).toArray(NewChunk[]::new);
    for (int i = 0; i < _partialSums.length; i++) {
      for (int j = 0; j < _partialSums[0].length; j++) {
        ncs[j].addNum(_partialSums[i][j] / _nBgRows);
      }
    }
    _partialSums = null;
    for (NewChunk nc : ncs)
      nc.close(0, _fs);
  }
}
