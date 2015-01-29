package hex;

import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.util.ModelUtils;

public class ModelMetricsMultinomial extends ModelMetrics {
  public final ConfusionMatrix _cm;
  public final HitRatio _hr;

  public ModelMetricsMultinomial(Model model, Frame frame) {
    super(model, frame);
    _cm=null;
    _hr=null;
  }
  public ModelMetricsMultinomial(Model model, Frame frame, ConfusionMatrix cm, HitRatio hr) {
    super(model, frame);
    _cm = cm;
    _hr = hr;
  }

  @Override public ConfusionMatrix cm() {
    return _cm;
  }
  @Override public HitRatio hr() {
    return _hr;
  }

  public static ModelMetricsMultinomial getFromDKV(Model model, Frame frame) {
    ModelMetrics mm = ModelMetrics.getFromDKV(model, frame);

    if (! (mm instanceof ModelMetricsMultinomial))
      throw new H2OIllegalArgumentException("Expected to find a Multinomial ModelMetrics for model: " + model._key.toString() + " and frame: " + frame._key.toString(),
              "Expected to find a ModelMetricsMultinomial for model: " + model._key.toString() + " and frame: " + frame._key.toString() + " but found a: " + mm.getClass());

    return (ModelMetricsMultinomial) mm;
  }

  public static class MetricBuilderMultinomial extends MetricBuilder {
    public MetricBuilderMultinomial( String[] domain ) { super(domain); }
    public MetricBuilderMultinomial( String[] domain, float[] thresholds ) { super(domain, thresholds); }

    public float[] perRow( float ds[], float yact ) {
      if( Float.isNaN(yact) ) return ds; // No errors if   actual   is missing
      if( Float.isNaN(ds[0])) return ds; // No errors if prediction is missing
      final int iact = (int)yact;

      // Compute error
      float sum = 0;          // Check for sane class distribution
      for( int i=1; i<ds.length; i++ ) { assert 0 <= ds[i] && ds[i] <= 1; sum += ds[i]; }
      assert Math.abs(sum-1.0f) < 1e-6;
      float err = 1.0f-ds[iact+1];  // Error: distance from predicting ycls as 1.0
      _sumsqe += err*err;       // Squared error
      assert !Double.isNaN(_sumsqe);

      // Plain Olde Confusion Matrix
      _cms[0][iact][(int)ds[0]]++; // actual v. predicted
      return ds;                // Flow coding
    }

    public ModelMetrics makeModelMetrics( Model m, Frame f, double sigma) {
      ConfusionMatrix cm = new ConfusionMatrix(_cms[0], _domain);
      HitRatio hr = null;       // TODO
      return m._output.addModelMetrics(new ModelMetricsMultinomial(m, f, cm, hr));
    }
  }
}
