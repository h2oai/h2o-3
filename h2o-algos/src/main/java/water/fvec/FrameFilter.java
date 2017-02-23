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


  public FrameFilter() {
  }

  public abstract boolean accept(Chunk c, int i);
  
  public Frame eval(Frame dataset, String signalColName) {
    final Vec signal = dataset.vec(signalColName);
    Key<Frame> destinationKey = Key.make();
    Vec flagCol = new MRTask() {
      @Override
      public void map(Chunk c, Chunk c2) {
        for (int i=0;i<c.len();++i) {
          if (accept(c, i)) c2.set(i, 1);
        }
      }
    }.doAll(new Frame(new Vec[]{signal, signal.makeZero()}))._fr.vec(1);

    Vec[] vecs = Arrays.copyOf(dataset.vecs(), dataset.vecs().length+1);
    vecs[vecs.length-1] = flagCol;
    Frame ff = new Frame(dataset.names(), dataset.vecs());
    ff.add("predicate", flagCol);
    Frame res = new Frame.DeepSelect().doAll(dataset.types(),ff).outputFrame(destinationKey, dataset.names(), dataset.domains());
    res.remove(signalColName);
    return res;
  }
  /*extends H2O.H2OCountedCompleter<DatasetSplitter> {
  private final Object columnName;
  Predicate<T> predicate;

  private Map<String, Frame> splits;
  final Key<Job> jobKey;
  private final Frame source;
  private final Frame target;

  public FrameFilter(Frame source, String columnName) {
    jobKey = Key.make();
    this.source = source;
    this.columnName = columnName;
    target = new Frame(source);
  }

  public void compute2() {
    new FilterTask().doAll(target.types(), source); // complete the computation of thrown tasks
  }
  @Override public void onCompletion(CountedCompleter caller) {
     target.update(jobKey).unlock(jobKey);
  }

  public Frame getResult() {
    return target;
  }

  class FilterTask extends MRTask<FilterTask> {
    FilterTask() {
      super(FrameFilter.this);
    }

    @Override public void map(Chunk[] cs) { // Output chunks
      int coutidx = cs[0].cidx(); // Index of output Chunk
      int cinidx = _pcidx + coutidx;
      int startRow = coutidx > 0 ? 0 : _psrow; // where to start extracting
      int nrows = cs[0]._len;
      // For each output chunk extract appropriate rows for partIdx-th part
      for (int i=0; i<cs.length; i++) {
        // WARNING: this implementation does not preserve co-location of chunks so we are forcing here network transfer!
        ChunkSplitter.extractChunkPart(_srcVecs[i].chunkForChunkIdx(cinidx), cs[i], startRow, nrows, _fs);
      }
    }
  }
}
*/  
}
