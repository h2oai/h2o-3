package hex;

import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.util.ArrayUtils;

import java.util.Arrays;
import java.util.Random;

import static water.util.ModelUtils.getPredictions;

public class ModelMetricsMultinomial extends ModelMetricsSupervised {
  public final float[] _hit_ratios;         // Hit ratios
  public final ConfusionMatrix _cm;

  public ModelMetricsMultinomial(Model model, Frame frame) {
    super(model, frame);
    _cm = null;
    _hit_ratios = null;
  }
  public ModelMetricsMultinomial(Model model, Frame frame, ConfusionMatrix cm, float[] hr, double sigma, double mse) {
    super(model, frame, sigma, mse);
    _cm = cm;
    _hit_ratios = hr;
  }

  @Override public ConfusionMatrix cm() {
    return _cm;
  }
  @Override public float[] hr() {
    return _hit_ratios;
  }

  public static ModelMetricsMultinomial getFromDKV(Model model, Frame frame) {
    ModelMetrics mm = ModelMetrics.getFromDKV(model, frame);

    if (! (mm instanceof ModelMetricsMultinomial))
      throw new H2OIllegalArgumentException("Expected to find a Multinomial ModelMetrics for model: " + model._key.toString() + " and frame: " + frame._key.toString(),
              "Expected to find a ModelMetricsMultinomial for model: " + model._key.toString() + " and frame: " + frame._key.toString() + " but found a: " + mm.getClass());

    return (ModelMetricsMultinomial) mm;
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

    // find the first occurrence of the actual label and increment all counters from there on
    // do nothing if no hit
    for (int k = 0; k < pred_labels.length; ++k) {
      if (pred_labels[k] == actual_label) {
        while (k < pred_labels.length) hits[k++]++;
      }
    }
  }

  public static class MetricBuilderMultinomial extends MetricBuilderSupervised {
    long[/*nclasses*/][/*nclasses*/] _cm;
    long[/*K*/] _hits;            //the number of hits, length: K
    private int _K = 10;          // TODO: Let user set K and seed
    private long _seed = 12345;

    public MetricBuilderMultinomial( String[] domain ) {
      super(domain);
      _cm = new long[_nclasses][_nclasses];
      _K = Math.min(10, _nclasses-1);
    }

    // Passed a float[] sized nclasses+1; ds[0] must be a prediction.  ds[1...nclasses-1] must be a class
    // distribution;
    @Override public float[] perRow( float ds[], float [] yact, Model m ) {
      if( Float.isNaN(yact[0]) ) return ds; // No errors if   actual   is missing
      if( Float.isNaN(ds[0])) return ds; // No errors if prediction is missing
      final int iact = (int)yact[0];

      // Compute error
      float sum = 0;          // Check for sane class distribution
      for( int i=1; i<ds.length; i++ ) { assert 0 <= ds[i] && ds[i] <= 1; sum += ds[i]; }
      assert Math.abs(sum-1.0f) < 1e-6;
      float err = 1.0f-ds[iact+1];  // Error: distance from predicting ycls as 1.0
      _sumsqe += err*err;       // Squared error
      assert !Double.isNaN(_sumsqe);

      // Compute hit ratio
      // TODO: Skip rows with predicted class = NA
      _hits = new long[_K];
      Arrays.fill(_hits, 0);
      final double[] tieBreaker = new double [] {
              new Random(_seed).nextDouble(), new Random(_seed+1).nextDouble(),
              new Random(_seed+2).nextDouble(), new Random(_seed+3).nextDouble() };
      final int[] pred_labels = getPredictions(_K, ds, tieBreaker);
      if(iact < ds.length-1) updateHits(_hits, iact, pred_labels);

      // Plain Olde Confusion Matrix
      _cm[iact][(int)ds[0]]++; // actual v. predicted
      _count++;
      return ds;                // Flow coding
    }

    @Override public void reduce( MetricBuilder mb ) {
      super.reduce(mb);
      assert(((MetricBuilderMultinomial) mb)._K == _K);
      _hits = ArrayUtils.add(_hits, ((MetricBuilderMultinomial) mb)._hits);
    }

    public ModelMetrics makeModelMetrics( Model m, Frame f, double sigma) {
      ConfusionMatrix cm = new ConfusionMatrix(_cm, _domain);
      float[] hr = new float[_K];
      for(int i = 0; i < hr.length; i++) hr[i] = _hits[i] / _count;
      final double mse = _sumsqe / _count;
      return m._output.addModelMetrics(new ModelMetricsMultinomial(m, f, cm, hr, sigma, mse));
    }
  }
}
