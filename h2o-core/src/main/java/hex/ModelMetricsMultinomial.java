package hex;

import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.ModelUtils;

import java.util.Arrays;

public class ModelMetricsMultinomial extends ModelMetricsSupervised {
  public final float[] _hit_ratios;         // Hit ratios
  public final ConfusionMatrix _cm;
  public final double _logloss;

  public ModelMetricsMultinomial(Model model, Frame frame, ConfusionMatrix cm, float[] hr, double logloss, double sigma, double mse) {
    super(model, frame, sigma, mse);
    _cm = cm;
    _hit_ratios = hr;
    _logloss = logloss;
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

  public static void updateHits(int iact, double[] ds, long[] hits, int row) {
    if (iact == ds[0]) {
      hits[0]++;
      return;
    }
    else {
      long before = ArrayUtils.sum(hits);
      // Use getPrediction logic to see which top K labels we would have predicted
      // Pick largest prob, assign label, then set prob to 0, find next-best label, etc.
      double[] ds_copy = Arrays.copyOf(ds, ds.length); //don't modify original ds!
      ds_copy[1+(int)ds[0]] = 0;
      for (int k=1; k<hits.length; ++k) {
        final int pred_labels = ModelUtils.getPrediction(ds_copy, row); //use tie-breaking of getPrediction
        ds_copy[1+pred_labels] = 0; //next iteration, we'll find the next-best label
        if (pred_labels==iact) {
          hits[k]++;
          break;
        }
      }
      // must find at least one hit if K == n_classes
      if (hits.length == ds.length-1) {
        long after = ArrayUtils.sum(hits);
        if (after == before) hits[hits.length-1]++; //assume worst case
      }
    }
  }


  public static class MetricBuilderMultinomial extends MetricBuilderSupervised {
    long[/*nclasses*/][/*nclasses*/] _cm;
    long[/*K*/] _hits;            // the number of hits for hitratio, length: K
    private int _K;               // TODO: Let user set K
    double _logloss;

    public MetricBuilderMultinomial( int nclasses, String[] domain ) {
      super(nclasses,domain);
      _cm = new long[domain.length][domain.length];
      _K = Math.min(10,_nclasses);
      _hits = new long[_K];
    }

    // Passed a float[] sized nclasses+1; ds[0] must be a prediction.  ds[1...nclasses-1] must be a class
    // distribution;
    @Override public double[] perRow(double ds[], float[] yact, Model m, int row) {
      if( Float .isNaN(yact[0]) ) return ds; // No errors if   actual   is missing
      if( Double.isNaN(ds  [0]) ) return ds; // No errors if prediction is missing
      final int iact = (int)yact[0];

      // Compute error
//      double sum = 0;          // Check for sane class distribution
//      for( int i=1; i<ds.length; i++ ) { assert 0 <= ds[i] && ds[i] <= 1; sum += ds[i]; }
//      assert Math.abs(sum-1.0f) < 1e-6;
      double err = iact+1 < ds.length ? 1-ds[iact+1] : 1;  // Error: distance from predicting ycls as 1.0
      _sumsqe += err*err;           // Squared error
      assert !Double.isNaN(_sumsqe);

      // Plain Olde Confusion Matrix
      _cm[iact][(int)ds[0]]++; // actual v. predicted
      _count++;

      // Compute hit ratio
      if( _K > 0 && iact < ds.length-1) updateHits(iact,ds,_hits,row);

      // Compute log loss
      if (iact+1 < ds.length) _logloss -= Math.log(Math.max(1e-15, ds[iact+1]));

      return ds;                // Flow coding
    }

    @Override public void reduce( MetricBuilder mb ) {
      super.reduce(mb);
      assert(((MetricBuilderMultinomial) mb)._K == _K);
      ArrayUtils.add(_cm, ((MetricBuilderMultinomial)mb)._cm);
      _hits = ArrayUtils.add(_hits, ((MetricBuilderMultinomial) mb)._hits);
      _logloss += ((MetricBuilderMultinomial) mb)._logloss;
    }

    public ModelMetrics makeModelMetrics( Model m, Frame f, double sigma) {
      if (sigma != 0) {
        ConfusionMatrix cm = new ConfusionMatrix(_cm, _domain);
        float[] hr = new float[_K];
        double mse = Double.NaN;
        double logloss = Double.NaN;
        if (_count != 0) {
          if (_hits != null) {
            for (int i = 0; i < hr.length; i++) {
              hr[i] = (float)_hits[i] / _count;
            }
            for (int i = 1; i < hr.length; i++) {
              hr[i] += hr[i-1];
            }
            if (hr.length == _nclasses) {
              assert(Math.abs(hr[hr.length-1]-1) < 1e-4);
              hr[hr.length-1] = 1.0f;
            }
          }
          mse = _sumsqe / _count;
          logloss = _logloss / _count;
        }
        return m._output.addModelMetrics(new ModelMetricsMultinomial(m, f, cm, hr, logloss, sigma, mse));
      } else {
        return m._output.addModelMetrics(new ModelMetricsMultinomial(m, f, null, null, Double.NaN, Double.NaN, Double.NaN));
      }
    }
  }
}
