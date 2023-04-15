package hex.modelselection;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.stream.LongStream;

public class ModelSelectionTasks {
  public static class SweepFrameParallel extends MRTask<SweepFrameParallel> {
    final double _oneOPivot;
    final int[] _trackPivotSweeps;
    final int _sweepIndex;
    final double[] _ariCol;  // store column of a_ir
    final int _cpmLen;

    public SweepFrameParallel(int[] trackPSweeps, int sweepInd, Frame cpm) {
      _trackPivotSweeps = trackPSweeps;
      _sweepIndex = sweepInd;
      _cpmLen = cpm.numCols();
      // extract row corresponding to sweeping row
      Vec sweepVec = cpm.vec(_sweepIndex);
      _ariCol = LongStream.range(0, _cpmLen).mapToDouble(x -> sweepVec.at(x)).toArray();
      _oneOPivot = 1.0 / _ariCol[_sweepIndex];
    }

    public void map(Chunk[] chks) {
      int chunkNRows = chks[0]._len;
      int rowOffset = (int) chks[0].start();
      int numCols = chks.length;
      double currEle;
      for (int rInd = 0; rInd < chunkNRows; rInd++) {
        int trueRowInd = rInd + rowOffset;
        for (int cInd = 0; cInd < numCols; cInd++) {
          currEle = chks[cInd].atd(rInd);
          if (trueRowInd != _sweepIndex && cInd != _sweepIndex) { // not working on the sweeping row/column
            currEle = currEle - _ariCol[trueRowInd] * _trackPivotSweeps[cInd] * _trackPivotSweeps[_sweepIndex] * _ariCol[cInd] * _oneOPivot;
          } else if (trueRowInd == _sweepIndex && cInd == _sweepIndex) { // working on the sweeping element
            currEle = _oneOPivot;
          } else if (trueRowInd == _sweepIndex && cInd != _sweepIndex) {  // working on sweeping row
            currEle = _trackPivotSweeps[cInd] * _trackPivotSweeps[_sweepIndex] * _ariCol[cInd] * _oneOPivot;
          } else if (trueRowInd != _sweepIndex && cInd == _sweepIndex) {  // working on sweeping column
            currEle = -_oneOPivot * _ariCol[trueRowInd];
          }
          chks[cInd].set(rInd, currEle);
        }
      }
    }
  }
}
