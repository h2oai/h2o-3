package hex;

import static water.util.ModelUtils.getPredictions;
import java.util.Arrays;
import java.util.Random;

import water.Iced;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;

public class HitRatio extends Iced {
  public Frame actual;
  public Vec vactual; // Column of the actual results (will display vertically)
  public Frame predict;
  private int _max_k = 10; // Max. number of labels (K) to use for hit ratio computation
  private long seed = new Random().nextLong(); // Random number seed for breaking ties between equal probabilities
  private String [] actual_domain; // domain of the actual response
  private float[] hit_ratios; // Hit ratios for k=1...K

  public HitRatio(Vec actuals, Frame predictions, int max_k) {
    _max_k = max_k;
    throw water.H2O.unimpl();
  }


////  @Override
//  private void init() throws IllegalArgumentException {
//    // Input handling
//    if( actual==null || predict==null )
//      throw new IllegalArgumentException("Missing actual or predict!");
//    if( vactual==null )
//      throw new IllegalArgumentException("Missing vactual!");
//    if (vactual.length() != predict.anyVec().length())
//      throw new IllegalArgumentException("Both arguments must have the same length!");
//    if (!vactual.isInt())
//      throw new IllegalArgumentException("Actual column must be integer class labels!");
//  }
//
////  @Override
//  public void execImpl() {
//    init();
//    Vec va = null;
//    try {
//      va = vactual.toEnum(); // always returns EnumWrappedVec
//      actual_domain = va.domain();
//      if (_max_k > predict.numCols()-1) {
//        Log.warn("Reducing Hitratio Top-K value to maximum value allowed: " + String.format("%,d", predict.numCols() - 1));
//        _max_k = predict.numCols() - 1;
//      }
//      final Frame actual_predict = new Frame(predict.names().clone(), predict.vecs().clone());
//      actual_predict.replace(0, va); // place actual labels in first column
//      hit_ratios = new HitRatioTask(_max_k, seed).doAll(actual_predict).hit_ratios();
//    } finally {       // Delete adaptation vectors
//      if (va!=null) DKV.remove(va._key);
//    }
//  }

  public void toASCII( StringBuilder sb ) {
    if (hit_ratios==null) return;
    sb.append("K   Hit-ratio\n");
    for (int k = 1; k<=_max_k; ++k) sb.append(k + "   " + String.format("%.3f", hit_ratios[k-1]*100.) + "%\n");
  }

  /**
   * Update hit counts for given set of actual label and predicted labels
   * This is to be called for each predicted row
   * @param hits Array of length K, counting the number of hits (entries will be incremented)
   * @param actual_label 1 actual label
   * @param pred_labels K predicted labels
   */
  static void updateHits(long[] hits, int actual_label, int[] pred_labels) {
    assert(hits != null);
    for (long h : hits) assert(h >= 0);
    assert(pred_labels != null);
    assert(actual_label >= 0);
    assert(hits.length == pred_labels.length);

    //find the first occurrence of the actual label and increment all counters from there on
    //do nothing if no hit
    for (int k=0;k<pred_labels.length;++k) {
      if (pred_labels[k] == actual_label) {
        while (k<pred_labels.length) hits[k++]++;
      }
    }
  }

  // Compute CMs for different thresholds via MRTask
  private static class HitRatioTask extends MRTask<HitRatioTask> {
    /* @OUT CMs */ private final float[] hit_ratios() {
      float[] hit_ratio = new float[_K];
      if (_count == 0) return new float[_K];
      for (int i=0;i<_K;++i) {
        hit_ratio[i] = ((float)_hits[i])/_count;
      }
      return hit_ratio;
    }

    /* IN K */
    final private int _K;
    /* IN Seed */
    private long _seed;

    /* Helper */
    private long[] _hits; //the number of hits, length: K
    private long _count; //the number of scored rows

    HitRatioTask(int K, long seed) {
      _K = K;
      _seed = seed;
    }

    @Override public void map( Chunk[] cs ) {
      _hits = new long[_K];
      Arrays.fill(_hits, 0);

      // pseudo-random tie breaking needs some bits to work with
      final double[] tieBreaker = new double [] {
              new Random(_seed).nextDouble(), new Random(_seed+1).nextDouble(),
              new Random(_seed+2).nextDouble(), new Random(_seed+3).nextDouble() };

      float [] preds = new float[cs.length];

      // rows
      for( int r=0; r < cs[0]._len; r++ ) {
        if (cs[0].isNA(r)) {
          _count--;
          continue;
        }
        final int actual_label = (int)cs[0].at8(r);

        //predict K labels
        for(int p=1; p < cs.length; p++) preds[p] = (float)cs[p].atd(r);
        final int[] pred_labels = getPredictions(_K, preds, tieBreaker);

        if (actual_label < cs.length-1) updateHits(_hits, actual_label, pred_labels);
      }
      _count += cs[0]._len;
    }

    @Override public void reduce( HitRatioTask other ) {
      assert(other._K == _K);
      _hits = ArrayUtils.add(_hits, other._hits);
      _count += other._count;
    }
  }
}
