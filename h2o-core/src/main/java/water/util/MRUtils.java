package water.util;

import water.*;
import water.H2O.H2OCallback;
import water.H2O.H2OCountedCompleter;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.nbhm.NonBlockingHashMap;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static water.util.RandomUtils.getRNG;

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
    Key newKey = fr._key != null ? Key.make(fr._key.toString() + (fr._key.toString().contains("temporary") ? ".sample." : ".temporary.sample.") + PrettyPrint.formatPct(fraction).replace(" ","")) : null;

    Frame r = new MRTask() {
      @Override
      public void map(Chunk[] cs, NewChunk[] ncs) {
        final Random rng = getRNG(seed + cs[0].cidx());
        int count = 0;
        for (int r = 0; r < cs[0]._len; r++)
          if (rng.nextFloat() < fraction || (count == 0 && r == cs[0]._len-1) ) {
            count++;
            for (int i = 0; i < ncs.length; i++) {
              ncs[i].addNum(cs[i].atd(r));
            }
          }
      }
    }.doAll(fr.types(), fr).outputFrame(newKey, fr.names(), fr.domains());
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
        int[] idx = new int[cs[0]._len];
        for (int r=0; r<idx.length; ++r) idx[r] = r;
        ArrayUtils.shuffleArray(idx, getRNG(seed));
        for (long anIdx : idx) {
          for (int i = 0; i < ncs.length; i++) {
            ncs[i].addNum(cs[i].atd((int) anIdx));
          }
        }
      }
    }.doAll_numericResult(fr.numCols(), fr).outputFrame(fr.names(), fr.domains());
  }

  /**
   * Compute the class distribution from a class label vector
   * (not counting missing values)
   *
   * Usage 1: Label vector is categorical
   * ------------------------------------
   * Vec label = ...;
   * assert(label.isCategorical());
   * double[] dist = new ClassDist(label).doAll(label).dist();
   *
   * Usage 2: Label vector is numerical
   * ----------------------------------
   * Vec label = ...;
   * int num_classes = ...;
   * assert(label.isInt());
   * double[] dist = new ClassDist(num_classes).doAll(label).dist();
   *
   */
  public static class ClassDist extends MRTask<ClassDist> {
    final int _nclass;
    protected double[] _ys;
    public ClassDist(final Vec label) { _nclass = label.domain().length; }
    public ClassDist(int n) { _nclass = n; }

    public final double[] dist() { return _ys; }
    public final double[] rel_dist() {
      final double sum = ArrayUtils.sum(_ys);
      return ArrayUtils.div(Arrays.copyOf(_ys, _ys.length), sum);
    }
    @Override public void map(Chunk ys) {
      _ys = new double[_nclass];
      for( int i=0; i<ys._len; i++ )
        if (!ys.isNA(i))
          _ys[(int) ys.at8(i)]++;
    }
    @Override public void map(Chunk ys, Chunk ws) {
      _ys = new double[_nclass];
      for( int i=0; i<ys._len; i++ )
        if (!ys.isNA(i))
          _ys[(int) ys.at8(i)] += ws.atd(i);
    }
    @Override public void reduce( ClassDist that ) { ArrayUtils.add(_ys,that._ys); }
  }

  public static class Dist extends MRTask<Dist> {
    private transient NonBlockingHashMap<Double,Integer> _dist;
    @Override public void map(Chunk ys) {
      _dist = new NonBlockingHashMap<>();
      for( int row=0; row< ys._len; row++ )
        if( !ys.isNA(row) ) {
          double v = ys.atd(row);
          Integer oldV = _dist.putIfAbsent(v,1);
          if( oldV!=null ) _dist.put(v,oldV+1);
        }
    }

    @Override public void reduce(Dist mrt) {
      if( _dist != mrt._dist ) {
        NonBlockingHashMap<Double,Integer> l = _dist;
        NonBlockingHashMap<Double,Integer> r = mrt._dist;
        if( l.size() < r.size() ) { l=r; r=_dist; }
        for( Double v: r.keySet() ) {
          Integer oldVal = l.putIfAbsent(v, r.get(v));
          if( oldVal!=null ) l.put(v, oldVal+r.get(v));
        }
        _dist=l;
        mrt._dist=null;
      }
    }
    public double[] dist() {
      int i=0;
      double[] dist = new double[_dist.size()];
      for( int v: _dist.values() ) dist[i++] = v;
      return dist;
    }
    public double[] keys() {
      int i=0;
      double[] keys = new double[_dist.size()];
      for( double v: _dist.keySet() ) keys[i++] = v;
      return keys;
    }
  }


  /**
   * Stratified sampling for classifiers - FIXME: For weights, this is not accurate, as the sampling is done with uniform weights
   * @param fr Input frame
   * @param label Label vector (must be categorical)
   * @param weights Weights vector, can be null
   * @param sampling_ratios Optional: array containing the requested sampling ratios per class (in order of domains), will be overwritten if it contains all 0s
   * @param maxrows Maximum number of rows in the returned frame
   * @param seed RNG seed for sampling
   * @param allowOversampling Allow oversampling of minority classes
   * @param verbose Whether to print verbose info
   * @return Sampled frame, with approximately the same number of samples from each class (or given by the requested sampling ratios)
   */
  public static Frame sampleFrameStratified(final Frame fr, Vec label, Vec weights, float[] sampling_ratios, long maxrows, final long seed, final boolean allowOversampling, final boolean verbose) {
    if (fr == null) return null;
    assert(label.isCategorical());
    if (maxrows < label.domain().length) {
      Log.warn("Attempting to do stratified sampling to fewer samples than there are class labels - automatically increasing to #rows == #labels (" + label.domain().length + ").");
      maxrows = label.domain().length;
    }

    ClassDist cd = new ClassDist(label);
    double[] dist = weights != null ? cd.doAll(label, weights).dist() : cd.doAll(label).dist();
    assert(dist.length > 0);
    Log.info("Doing stratified sampling for data set containing " + fr.numRows() + " rows from " + dist.length + " classes. Oversampling: " + (allowOversampling ? "on" : "off"));
    if (verbose)
      for (int i=0; i<dist.length;++i)
        Log.info("Class " + label.factor(i) + ": count: " + dist[i] + " prior: " + (float)dist[i]/fr.numRows());

    // create sampling_ratios for class balance with max. maxrows rows (fill
    // existing array if not null).  Make a defensive copy.
    sampling_ratios = sampling_ratios == null ? new float[dist.length] : sampling_ratios.clone();
    assert sampling_ratios.length == dist.length;
    if( ArrayUtils.minValue(sampling_ratios) == 0 && ArrayUtils.maxValue(sampling_ratios) == 0 ) {
      // compute sampling ratios to achieve class balance
      for (int i=0; i<dist.length;++i)
        sampling_ratios[i] = ((float)fr.numRows() / label.domain().length) / (float)dist[i]; // prior^-1 / num_classes
      final float inv_scale = ArrayUtils.minValue(sampling_ratios); //majority class has lowest required oversampling factor to achieve balance
      if (!Float.isNaN(inv_scale) && !Float.isInfinite(inv_scale))
        ArrayUtils.div(sampling_ratios, inv_scale); //want sampling_ratio 1.0 for majority class (no downsampling)
    }

    if (!allowOversampling)
      for (int i=0; i<sampling_ratios.length; ++i)
        sampling_ratios[i] = Math.min(1.0f, sampling_ratios[i]);

    // given these sampling ratios, and the original class distribution, this is the expected number of resulting rows
    float numrows = 0;
    for (int i=0; i<sampling_ratios.length; ++i) {
      numrows += sampling_ratios[i] * dist[i];
    }
    if (Float.isNaN(numrows)) {
      throw new IllegalArgumentException("Error during sampling - too few points?");
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
      Log.info("Class '" + label.domain()[i] + "' sampling ratio: " + sampling_ratios[i]);
    }

    return sampleFrameStratified(fr, label, weights, sampling_ratios, seed, verbose);
  }

  /**
   * Stratified sampling
   * @param fr Input frame
   * @param label Label vector (from the input frame)
   * @param weights Weight vector (from the input frame), can be null
   * @param sampling_ratios Given sampling ratios for each class, in order of domains
   * @param seed RNG seed
   * @param debug Whether to print debug info
   * @return Stratified frame
   */
  public static Frame sampleFrameStratified(final Frame fr, Vec label, Vec weights, final float[] sampling_ratios, final long seed, final boolean debug) {
    return sampleFrameStratified(fr, label, weights, sampling_ratios, seed, debug, 0);
  }

  // internal version with repeat counter
  // currently hardcoded to do up to 10 tries to get a row from each class, which can be impossible for certain wrong sampling ratios
  private static Frame sampleFrameStratified(final Frame fr, Vec label, Vec weights, final float[] sampling_ratios, final long seed, final boolean debug, int count) {
    if (fr == null) return null;
    assert(label.isCategorical());
    assert(sampling_ratios != null && sampling_ratios.length == label.domain().length);
    final int labelidx = fr.find(label); //which column is the label?
    assert(labelidx >= 0);
    final int weightsidx = fr.find(weights); //which column is the weight?

    final boolean poisson = false; //beta feature

    //FIXME - this is doing uniform sampling, even if the weights are given
    Frame r = new MRTask() {
      @Override
      public void map(Chunk[] cs, NewChunk[] ncs) {
        final Random rng = getRNG(seed + cs[0].cidx());
        for (int r = 0; r < cs[0]._len; r++) {
          if (cs[labelidx].isNA(r)) continue; //skip missing labels
          final int label = (int)cs[labelidx].at8(r);
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
              ncs[i].addNum(cs[i].atd(r));
            }
          }
        }
      }
    }.doAll(fr.types(), fr).outputFrame(fr.names(), fr.domains());

    // Confirm the validity of the distribution
    Vec lab = r.vecs()[labelidx];
    Vec wei = weightsidx != -1 ? r.vecs()[weightsidx] : null;
    double[] dist = wei != null ? new ClassDist(lab).doAll(lab, wei).dist() : new ClassDist(lab).doAll(lab).dist();

    // if there are no training labels in the test set, then there is no point in sampling the test set
    if (dist == null) return fr;

    if (debug) {
      double sumdist = ArrayUtils.sum(dist);
      Log.info("After stratified sampling: " + sumdist + " rows.");
      for (int i=0; i<dist.length;++i) {
        Log.info("Class " + r.vecs()[labelidx].factor(i) + ": count: " + dist[i]
                + " sampling ratio: " + sampling_ratios[i] + " actual relative frequency: " + (float)dist[i] / sumdist * dist.length);
      }
    }

    // Re-try if we didn't get at least one example from each class
    if (ArrayUtils.minValue(dist) == 0 && count < 10) {
      Log.info("Re-doing stratified sampling because not all classes were represented (unlucky draw).");
      r.delete();
      return sampleFrameStratified(fr, label, weights, sampling_ratios, seed+1, debug, ++count);
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
      if(n == H2O.SELF) {
        _tasks[i].setCompleter(new Callback(H2O.SELF,i));
        H2O.submitTask(_tasks[i]);
      } else
        new RPC(n,_tasks[i]).addCompleter(this).call();
    }
    class Callback extends H2OCallback<H2OCountedCompleter> {
      final int i;
      final H2ONode n;

      public Callback(H2ONode n, int i){
        super(ParallelTasks.this); this.n = n; this.i = i;
      }
      @Override public void callback(H2OCountedCompleter cc){
        Log.info("callback for task " + i);
        int nextI;
        if((nextI = _nextTask.getAndIncrement()) < _tasks.length)  // not done yet
          forkDTask(nextI, n);
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
