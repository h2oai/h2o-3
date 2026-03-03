package water;

import water.fvec.Frame;
import water.fvec.OneChunkVec;
import water.fvec.Vec;
import water.util.fp.Function;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class SplitToChunksApplyCombine {
  public static Frame concatFrames(List<Frame> frms, Key<Frame> destinationKey) {
    Frame result = new Frame(destinationKey);
    long nRows = frms.stream().mapToLong(frame -> frame.numRows()).sum();
    for (int i = 0; i < frms.get(0).numCols(); i++) {
      Vec v = Vec.makeZero(nRows);
      try (Vec.Writer vw = v.open()) {
        long cnt = 0;
        for (Frame fr : frms) {
          Vec.Reader vr = fr.vec(i).new Reader();
          for (int k = 0; k < fr.numRows(); k++) {
            vw.set(cnt++, vr.at(k));
          }
        }
      }
      result.add(frms.get(0)._names[i], v);
    }
    DKV.put(result);
    return result;
  }

  public static Frame createSubFrame(Frame fr, int cidx, String destinationKeyPrefix) {
    assert cidx >= 0 && fr.anyVec().nChunks() > cidx;
    Futures fs = new Futures();
    Vec[] vecs = Arrays.stream(fr.vecs()).map(v -> OneChunkVec.make(v, cidx, fs)).toArray(Vec[]::new);
    fs.blockForPending();
    return new Frame(Key.make(destinationKeyPrefix + "_oneChunkFrame_" + cidx), fr.names(), vecs);
  }


  public static Frame splitApplyCombine(Frame frameToSplit, Function<Frame, Frame> fun, Key<Frame> destinationKey) {
    try (Scope.Safe safe = Scope.safe(frameToSplit)) {
      List<Frame> resultSubFrames = new LinkedList<>();
      int nChunks = frameToSplit.anyVec().nChunks();
      for (int cidx = 0; cidx < nChunks; cidx++) {
        Frame subFrame = createSubFrame(frameToSplit, cidx, destinationKey.toString());
        if (subFrame.numRows() == 0) continue;
        DKV.put(subFrame);
        resultSubFrames.add(Scope.track(fun.apply(subFrame)));
      }
      return Scope.untrack(concatFrames(resultSubFrames, destinationKey));
    }
  }
}
