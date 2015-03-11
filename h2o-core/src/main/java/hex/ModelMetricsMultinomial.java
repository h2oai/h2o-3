package hex;

import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.util.ArrayUtils;

public class ModelMetricsMultinomial extends ModelMetricsSupervised {
  public final float[] _hit_ratios;         // Hit ratios
  public final ConfusionMatrix _cm;

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

  public static void updateHits(int iact, float[] ds, long[] hits ) {
    int pred = (int)ds[0];
    if( iact == pred ) hits[0]++; // Top prediction is correct?
    else {                  // Else need to find how far down the correct guy is
      float p = ds[pred+1]; // Prediction value which failed
      int tie=0;
      for( int k=1; k<hits.length; k++ ) {
        // Find largest prediction less than 'p', or for ties, the tie'th
        int best = 0;
        int tiebreak=0;
        for( int i=1; i<ds.length; i++ ) {
          if( i != pred+1 && (ds[i] < p || (ds[i]==p && tie < tiebreak)) ) {
            if( best==0 || ds[i] > ds[best] )
              best = i;
          }
        }
        if( best == 0 ) return; // prediction not in top K
        if( ds[best] < p ) {
          p = ds[best]; tie=0;
        } else {
          assert ds[best]==p;
          tie++;
        }
        if( best==iact+1 ) { hits[k]++; return; }
      }
    }
  }


  public static class MetricBuilderMultinomial extends MetricBuilderSupervised {
    long[/*nclasses*/][/*nclasses*/] _cm;
    long[/*K*/] _hits;            // the number of hits for hitratio, length: K
    private int _K;               // TODO: Let user set K

    public MetricBuilderMultinomial( int nclasses, String[] domain ) {
      super(nclasses,domain);
      _cm = new long[domain.length][domain.length];
      _K = Math.min(10,_nclasses-1);
      _hits = new long[_K];
    }

    // Passed a float[] sized nclasses+1; ds[0] must be a prediction.  ds[1...nclasses-1] must be a class
    // distribution;
    @Override public float[] perRow( float ds[], float [] yact, Model m ) {
      if( Float.isNaN(yact[0]) ) return ds; // No errors if   actual   is missing
      if( Float.isNaN(ds  [0]) ) return ds; // No errors if prediction is missing
      final int iact = (int)yact[0];

      // Compute error
      float sum = 0;          // Check for sane class distribution
      for( int i=1; i<ds.length; i++ ) { assert 0 <= ds[i] && ds[i] <= 1; sum += ds[i]; }
//      assert Math.abs(sum-1.0f) < 1e-6;
      float err = iact+1 < ds.length ? 1.0f-ds[iact+1] : 1.0f;  // Error: distance from predicting ycls as 1.0
      _sumsqe += err*err;           // Squared error
      assert !Double.isNaN(_sumsqe);

      // Plain Olde Confusion Matrix
      _cm[iact][(int)ds[0]]++; // actual v. predicted
      _count++;

      // Compute hit ratio
      if( _K > 0 && iact < ds.length-1) updateHits(iact,ds,_hits);

      return ds;                // Flow coding
    }

    @Override public void reduce( MetricBuilder mb ) {
      super.reduce(mb);
      assert(((MetricBuilderMultinomial) mb)._K == _K);
      ArrayUtils.add(_cm, ((MetricBuilderMultinomial)mb)._cm);
      _hits = ArrayUtils.add(_hits, ((MetricBuilderMultinomial) mb)._hits);
    }

    public ModelMetrics makeModelMetrics( Model m, Frame f, double sigma) {
      ConfusionMatrix cm = new ConfusionMatrix(_cm, _domain);
      float[] hr = new float[_K];
      double mse = 0;
      if (_count != 0) {
        if (_hits != null) {
          for (int i = 0; i < hr.length; i++) {
            hr[i] = _hits[i] / _count;
          }
        }
        mse = _sumsqe / _count;
      }
      return m._output.addModelMetrics(new ModelMetricsMultinomial(m, f, cm, hr, sigma, mse));
    }
  }
}
