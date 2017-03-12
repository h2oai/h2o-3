package water.fvec;

import water.*;
import water.util.FrameUtils;

import java.util.Arrays;

/**
 * Filters data based on a predicate
 * 
 * Created by vpatryshev on 2/21/17.
 */
public abstract class FrameFilter extends Iced<FrameFilter> {

  public abstract boolean accept(Chunk c, int i);

  private Key<Frame> frameKey;
  private String signalColName;
  private Key<Vec> signalKey;

  public FrameFilter() {}
  
  public FrameFilter(Frame frame, String signalColName) {
    assert frame.vec(signalColName) != null : "dataset should contain column " + signalColName;
    this.frameKey = FrameUtils.save(frame);
    this.signalColName = signalColName;
    this.signalKey = frame.vec(signalColName)._key;
    assert signalKey != null : "signal vector shoul dbe in DKV";
  }

  public Frame eval() {
    Frame dataset = DKV.getGet(frameKey);
    Key<Frame> destinationKey = Key.make();
    Vec signal = DKV.getGet(signalKey);
    Vec flagCol = new MRTask() {

      @Override
      public void map(Chunk c, Chunk c2) {
        for (int i = 0; i < c.len(); ++i) {
          if (accept(c, i)) c2.set(i, 1);
        }
      }

    }.doAll(new Frame(new Vec[]{signal, signal.makeZero()}))._fr.vec(1);

    final Vec[] oldVecs = dataset.vecs();
    Vec[] vecs = Arrays.copyOf(oldVecs, oldVecs.length+1);
    vecs[vecs.length-1] = flagCol;
    Frame ff = new Frame(dataset.names(), oldVecs);
    ff.add("predicate", flagCol);
    Frame res = new Frame.DeepSelect().doAll(dataset.types(),ff).outputFrame(destinationKey, dataset.names(), dataset.domains());
    res.remove(signalColName);
    return Scope.track(res);
  }
}

