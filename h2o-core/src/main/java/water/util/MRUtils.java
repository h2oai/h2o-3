package water.util;

import static water.util.RandomUtils.getDeterRNG;

import water.*;
import water.H2O.H2OCallback;
import water.H2O.H2OCountedCompleter;
import water.fvec.*;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class MRUtils {


  /**
   * Sample rows from a frame.
   * Can be unlucky for small sampling fractions - will continue calling itself until at least 1 row is returned.
   * @param fr Input frame
   * @param rows Approximate number of rows to sample (across all chunks)
   * @param seed Seed for RNG
   * @return Sampled frame
   */
  public static Frame sampleFrame(Frame fr, final long rows, final long seed) {
    if (fr == null) return null;
    final float fraction = rows > 0 ? (float)rows / fr.numRows() : 1.f;
    if (fraction >= 1.f) return fr;
    Frame r = new MRTask() {
      @Override
      public void map(Chunk[] cs, NewChunk[] ncs) {
        final Random rng = getDeterRNG(seed + cs[0].cidx());
        int count = 0;
        for (int r = 0; r < cs[0]._len; r++)
          if (rng.nextFloat() < fraction || (count == 0 && r == cs[0]._len-1) ) {
            count++;
            for (int i = 0; i < ncs.length; i++) {
              ncs[i].addNum(cs[i].at0(r));
            }
          }
      }
    }.doAll(fr.numCols(), fr).outputFrame(fr.names(), fr.domains());
    if (r.numRows() == 0) {
      Log.warn("You asked for " + rows + " rows (out of " + fr.numRows() + "), but you got none (seed=" + seed + ").");
      Log.warn("Let's try again. You've gotta ask yourself a question: \"Do I feel lucky?\"");
      return sampleFrame(fr, rows, seed+1);
    }
    return r;
  }

  /**
   * Row-wise shuffle of a frame (only shuffles rows inside of each chunk)
   * @param fr Input frame
   * @return Shuffled frame
   */
  public static Frame shuffleFramePerChunk(Frame fr, final long seed) {
    return new MRTask() {
      @Override
      public void map(Chunk[] cs, NewChunk[] ncs) {
        long[] idx = new long[cs[0]._len];
        for (int r=0; r<idx.length; ++r) idx[r] = r;
        ArrayUtils.shuffleArray(idx, seed);
        for (long anIdx : idx) {
          for (int i = 0; i < ncs.length; i++) {
            ncs[i].addNum(cs[i].at0((int) anIdx));
          }
        }
      }
    }.doAll(fr.numCols(), fr).outputFrame(fr.names(), fr.domains());
  }

  /**
   * Compute the class distribution from a class label vector
   * (not counting missing values)
   *
   * Usage 1: Label vector is categorical
   * ------------------------------------
   * Vec label = ...;
   * assert(label.isEnum());
   * long[] dist = new ClassDist(label).doAll(label).dist();
   *
   * Usage 2: Label vector is numerical
   * ----------------------------------
   * Vec label = ...;
   * int num_classes = ...;
   * assert(label.isInt());
   * long[] dist = new ClassDist(num_classes).doAll(label).dist();
   *
   */
  public static class ClassDist extends ClassDistHelper {
    public ClassDist(final Vec label) { super(label.domain().length); }
//    public ClassDist(int n) { super(n); }
    public final long[] dist() { return _ys; }
    public final float[] rel_dist() {
      float[] rel = new float[_ys.length];
      for (int i=0; i<_ys.length; ++i) rel[i] = (float)_ys[i];
      final float sum = ArrayUtils.sum(rel);
      assert(sum != 0.);
      ArrayUtils.div(rel, sum);
      return rel;
    }
  }
  private static class ClassDistHelper extends MRTask<ClassDist> {
    private ClassDistHelper(int nclass) { _nclass = nclass; }
    final int _nclass;
    protected long[] _ys;
    @Override public void map(Chunk ys) {
      _ys = new long[_nclass];
      for( int i=0; i<ys._len; i++ )
        if( !ys.isNA0(i) )
          _ys[(int)ys.at80(i)]++;
    }
    @Override public void reduce( ClassDist that ) { ArrayUtils.add(_ys,that._ys); }
  }


  /**
   * Stratified sampling for classifiers
   * @param fr Input frame
   * @param label Label vector (must be enum)
   * @param sampling_ratios Optional: array containing the requested sampling ratios per class (in order of domains), will be overwritten if it contains all 0s
   * @param maxrows Maximum number of rows in the returned frame
   * @param seed RNG seed for sampling
   * @param allowOversampling Allow oversampling of minority classes
   * @param verbose Whether to print verbose info
   * @return Sampled frame, with approximately the same number of samples from each class (or given by the requested sampling ratios)
   */
  public static Frame sampleFrameStratified(final Frame fr, Vec label, float[] sampling_ratios, long maxrows, final long seed, final boolean allowOversampling, final boolean verbose) {
    if (fr == null) return null;
    assert(label.isEnum());
    assert(maxrows >= label.domain().length);

    long[] dist = new ClassDist(label).doAll(label).dist();
    assert(dist.length > 0);
    Log.info("Doing stratified sampling for data set containing " + fr.numRows() + " rows from " + dist.length + " classes. Oversampling: " + (allowOversampling ? "on" : "off"));
    if (verbose) {
      for (int i=0; i<dist.length;++i) {
        Log.info("Class " + label.factors()[i] + ": count: " + dist[i] + " prior: " + (float)dist[i]/fr.numRows());
      }
    }

    // create sampling_ratios for class balance with max. maxrows rows (fill existing array if not null)
    if (sampling_ratios == null || (ArrayUtils.minValue(sampling_ratios) == 0 && ArrayUtils.maxValue(sampling_ratios) == 0)) {
      // compute sampling ratios to achieve class balance
      if (sampling_ratios == null) {
        sampling_ratios = new float[dist.length];
      }
      assert(sampling_ratios.length == dist.length);
      for (int i=0; i<dist.length;++i) {
        sampling_ratios[i] = ((float)fr.numRows() / label.domain().length) / dist[i]; // prior^-1 / num_classes
      }
      final float inv_scale = ArrayUtils.minValue(sampling_ratios); //majority class has lowest required oversampling factor to achieve balance
      if (!Float.isNaN(inv_scale) && !Float.isInfinite(inv_scale))
        ArrayUtils.div(sampling_ratios, inv_scale); //want sampling_ratio 1.0 for majority class (no downsampling)
    }

    if (!allowOversampling) {
      for (int i=0; i<sampling_ratios.length; ++i) {
        sampling_ratios[i] = Math.min(1.0f, sampling_ratios[i]);
      }
    }

    // given these sampling ratios, and the original class distribution, this is the expected number of resulting rows
    float numrows = 0;
    for (int i=0; i<sampling_ratios.length; ++i) {
      numrows += sampling_ratios[i] * dist[i];
    }
    final long actualnumrows = Math.min(maxrows, Math.round(numrows)); //cap #rows at maxrows
    assert(actualnumrows >= 0); //can have no matching rows in case of sparse data where we had to fill in a makeZero() vector
    Log.info("Stratified sampling to a total of " + String.format("%,d", actualnumrows) + " rows" + (actualnumrows < numrows ? " (limited by max_after_balance_size).":"."));

    if (actualnumrows != numrows) {
      ArrayUtils.mult(sampling_ratios, (float)actualnumrows/numrows); //adjust the sampling_ratios by the global rescaling factor
      if (verbose)
        Log.info("Downsampling majority class by " + (float)actualnumrows/numrows
                + " to limit number of rows to " + String.format("%,d", maxrows));
    }
    for (int i=0;i<label.domain().length;++i) {
      Log.info("Class '" + label.domain()[i].toString()
              + "' sampling ratio: " + sampling_ratios[i]);
    }

    return sampleFrameStratified(fr, label, sampling_ratios, seed, verbose);
  }

  /**
   * Stratified sampling
   * @param fr Input frame
   * @param label Label vector (from the input frame)
   * @param sampling_ratios Given sampling ratios for each class, in order of domains
   * @param seed RNG seed
   * @param debug Whether to print debug info
   * @return Stratified frame
   */
  public static Frame sampleFrameStratified(final Frame fr, Vec label, final float[] sampling_ratios, final long seed, final boolean debug) {
    return sampleFrameStratified(fr, label, sampling_ratios, seed, debug, 0);
  }

  // internal version with repeat counter
  // currently hardcoded to do up to 10 tries to get a row from each class, which can be impossible for certain wrong sampling ratios
  private static Frame sampleFrameStratified(final Frame fr, Vec label, final float[] sampling_ratios, final long seed, final boolean debug, int count) {
    if (fr == null) return null;
    assert(label.isEnum());
    assert(sampling_ratios != null && sampling_ratios.length == label.domain().length);
    final int labelidx = fr.find(label); //which column is the label?
    assert(labelidx >= 0);

    final boolean poisson = false; //beta feature

    Frame r = new MRTask() {
      @Override
      public void map(Chunk[] cs, NewChunk[] ncs) {
        final Random rng = getDeterRNG(seed + cs[0].cidx());
        for (int r = 0; r < cs[0]._len; r++) {
          if (cs[labelidx].isNA0(r)) continue; //skip missing labels
          final int label = (int)cs[labelidx].at80(r);
          assert(sampling_ratios.length > label && label >= 0);
          int sampling_reps;
          if (poisson) {
            throw H2O.unimpl();
//            sampling_reps = ArrayUtils.getPoisson(sampling_ratios[label], rng);
          } else {
            final float remainder = sampling_ratios[label] - (int)sampling_ratios[label];
            sampling_reps = (int)sampling_ratios[label] + (rng.nextFloat() < remainder ? 1 : 0);
          }
          for (int i = 0; i < ncs.length; i++) {
            for (int j = 0; j < sampling_reps; ++j) {
              ncs[i].addNum(cs[i].at0(r));
            }
          }
        }
      }
    }.doAll(fr.numCols(), fr).outputFrame(fr.names(), fr.domains());

    // Confirm the validity of the distribution
    long[] dist = new ClassDist(r.vecs()[labelidx]).doAll(r.vecs()[labelidx]).dist();

    // if there are no training labels in the test set, then there is no point in sampling the test set
    if (dist == null) return fr;

    if (debug) {
      long sumdist = ArrayUtils.sum(dist);
      Log.info("After stratified sampling: " + sumdist + " rows.");
      for (int i=0; i<dist.length;++i) {
        Log.info("Class " + r.vecs()[labelidx].factors()[i] + ": count: " + dist[i]
                + " sampling ratio: " + sampling_ratios[i] + " actual relative frequency: " + (float)dist[i] / sumdist * dist.length);
      }
    }

    // Re-try if we didn't get at least one example from each class
    if (ArrayUtils.minValue(dist) == 0 && count < 10) {
      Log.info("Re-doing stratified sampling because not all classes were represented (unlucky draw).");
      r.delete();
      return sampleFrameStratified(fr, label, sampling_ratios, seed+1, debug, ++count);
    }

    // shuffle intra-chunk
    Frame shuffled = shuffleFramePerChunk(r, seed+0x580FF13);
    r.delete();

    return shuffled;
  }

  public static class ParallelTasks<T extends DTask<T>> extends H2OCountedCompleter {
    public transient final T [] _tasks;
    transient final public int _maxP;
    transient private AtomicInteger _nextTask;

    public ParallelTasks(H2OCountedCompleter cmp, T[] tsks){
      this(cmp,tsks,H2O.CLOUD.size());
    }
    public ParallelTasks(H2OCountedCompleter cmp, T[] tsks, int maxP){
      super(cmp);
      _maxP = maxP;
      _tasks = tsks;
      addToPendingCount(_tasks.length-1);
    }

    private void forkDTask(int i){
      int nodeId = i%H2O.CLOUD.size();
      forkDTask(i,H2O.CLOUD._memary[nodeId]);
    }
    private void forkDTask(final int i, H2ONode n){
      if(n == H2O.SELF) H2O.submitTask(_tasks[i]);
      else new RPC(n,_tasks[i]).addCompleter(this).call();
    }
    class Callback extends H2OCallback<H2OCountedCompleter> {
      final int i;
      final H2ONode n;
      public Callback(H2ONode n, int i){super(ParallelTasks.this); this.n = n; this.i = i;}
      @Override public void callback(H2OCountedCompleter cc){
        int i;
        if((i = _nextTask.getAndIncrement()) < _tasks.length)  // not done yet
          forkDTask(i, n);
      }
    }
    @Override public void compute2(){
      final int n = Math.min(_maxP, _tasks.length);
      _nextTask = new AtomicInteger(n);
      for(int i = 0; i < n; ++i)
        forkDTask(i);
    }
  }
}
