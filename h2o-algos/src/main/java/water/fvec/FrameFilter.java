package water.fvec;

import water.*;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Filters data based on a predicate
 * 
 * Created by vpatryshev on 2/21/17.
 */
public abstract class FrameFilter implements Serializable {

  private Key<Frame> datasetKey;
  private String signalColName;
  private Key<Vec> signalKey;

  public FrameFilter() {}
  
  public FrameFilter(Frame dataset, String signalColName) {
    this.datasetKey = dataset._key;
    this.signalColName = signalColName;
    this.signalKey = dataset.vec(signalColName)._key;
  }

  public abstract boolean accept(Chunk c, int i);

  public Frame eval() {
    Frame dataset = DKV.getGet(datasetKey);
    Key<Frame> destinationKey = Key.make();
    Vec signal = DKV.getGet(signalKey);
    Vec flagCol = new FrameFilterTask(this).doAll(new Frame(new Vec[]{signal, signal.makeZero()}))._fr.vec(1);

    Vec[] vecs = Arrays.copyOf(dataset.vecs(), dataset.vecs().length+1);
    vecs[vecs.length-1] = flagCol;
    Frame ff = new Frame(dataset.names(), dataset.vecs());
    ff.add("predicate", flagCol);
    Frame res = new Frame.DeepSelect().doAll(dataset.types(),ff).outputFrame(destinationKey, dataset.names(), dataset.domains());
    res.remove(signalColName);
    return res;
  }
}

