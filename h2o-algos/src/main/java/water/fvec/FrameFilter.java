package water.fvec;

import water.*;

import java.util.Arrays;

/**
 * Filters data based on a predicate
 * 
 * Created by vpatryshev on 2/21/17.
 */
public abstract class FrameFilter extends Iced<FrameFilter> {

  public abstract boolean accept(Chunk c, int i);

  private Key<Frame> datasetKey;
  private String signalColName;
  private Key<Vec> signalKey;

  public FrameFilter() {}
  
  public FrameFilter(Frame dataset, String signalColName) {
    assert dataset.vec(signalColName) != null : "dataset should contain column " + signalColName;
    if (dataset._key == null) saveDataset(dataset);
    this.datasetKey = dataset._key;
    this.signalColName = signalColName;
    this.signalKey = dataset.vec(signalColName)._key;
    assert signalKey != null : "signal vector shoul dbe in DKV";
  }

  private void saveDataset(Frame dataset) {
    dataset._key = Key.make();
    DKV.put(dataset);
    Scope.track(dataset);
    for (Vec v : dataset.vecs()) {
      if (DKV.get(v._key) == null) DKV.put(v);
    }
  }

  public Frame eval() {
    Frame dataset = DKV.getGet(datasetKey);
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

