package hex;

import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.ModelUtils;

public class ModelMetricsBinomial extends ModelMetricsSupervised {
  public final AUCData _aucdata;
  public final ConfusionMatrix _cm;

  public ModelMetricsBinomial(Model model, Frame frame) {
    super(model, frame);
    _aucdata = null;
    _cm = null;
  }

  public ModelMetricsBinomial(Model model, Frame frame, AUCData aucdata, double sigma, double mse) {
    super(model, frame);
    _aucdata = aucdata;
    _cm = aucdata.CM();
    _sigma = sigma;
    _mse = mse;
  }

  @Override public ConfusionMatrix cm() {
    return _cm;
  }
  @Override public AUCData auc() {
    return _aucdata;
  }

  public static ModelMetricsBinomial getFromDKV(Model model, Frame frame) {
    ModelMetrics mm = ModelMetrics.getFromDKV(model, frame);

    if (! (mm instanceof ModelMetricsBinomial))
      throw new H2OIllegalArgumentException("Expected to find a Binomial ModelMetrics for model: " + model._key.toString() + " and frame: " + frame._key.toString(),
              "Expected to find a ModelMetricsBinomial for model: " + model._key.toString() + " and frame: " + frame._key.toString() + " but found a: " + mm.getClass());

    return (ModelMetricsBinomial) mm;
  }

  public static class MetricBuilderBinomial extends MetricBuilderSupervised {
    protected final float[] _thresholds;
    protected long[/*nthreshes*/][/*nclasses*/][/*nclasses*/] _cms; // Confusion Matric(es)
    public MetricBuilderBinomial( String[] domain, float[] thresholds ) {
      super(2,domain);
      _thresholds = thresholds;
      // Thresholds are only for binomial classes
      assert (_nclasses==2 && thresholds.length>0) || (_nclasses!=2 && thresholds.length==1);
      _cms = new long[thresholds.length][_nclasses][_nclasses];
    }

    // Passed a float[] sized nclasses+1; ds[0] must be a prediction.  ds[1...nclasses-1] must be a class
    // distribution;
    @Override public float[] perRow( float ds[], float[] yact, Model m ) {
      if( Float.isNaN(yact[0]) ) return ds; // No errors if   actual   is missing
      if( Float.isNaN(ds  [0]) ) return ds; // No errors if prediction is missing
      final int iact = (int)yact[0];

      // Compute error
      float sum = 0;          // Check for sane class distribution
      for( int i=1; i<ds.length; i++ ) { assert 0 <= ds[i] && ds[i] <= 1; sum += ds[i]; }
      assert Math.abs(sum-1.0f) < 1e-6;
      float err = 1.0f-ds[iact+1];  // Error: distance from predicting ycls as 1.0
      _sumsqe += err*err;           // Squared error
      assert !Double.isNaN(_sumsqe);

      // Binomial classification -> compute AUC, draw ROC
      float snd = ds[2];      // Probability of a TRUE
      // TODO: Optimize this: just keep deltas from one CM to the next
      for(int i = 0; i < ModelUtils.DEFAULT_THRESHOLDS.length; i++) {
        int p = snd >= ModelUtils.DEFAULT_THRESHOLDS[i] ? 1 : 0; // Compute prediction based on threshold
        _cms[i][iact][p]++;   // Increase matrix
      }
      _count++;
      return ds;                // Flow coding
    }

    @Override public void reduce( MetricBuilder mb ) {
      super.reduce(mb);
      ArrayUtils.add(_cms, ((MetricBuilderBinomial)mb)._cms);
    }

    public ModelMetrics makeModelMetrics( Model m, Frame f, double sigma) {
      ConfusionMatrix[] cms = new ConfusionMatrix[_cms.length];
      for( int i=0; i<cms.length; i++ ) cms[i] = new ConfusionMatrix(_cms[i], _domain);

      AUCData aucdata = new AUC(cms,_thresholds,_domain).data();
      double mse = _sumsqe / _count;
      return m._output.addModelMetrics(new ModelMetricsBinomial(m, f, aucdata, sigma, mse));
    }
  }
}
