package hex;

import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.util.ModelUtils;

public class ModelMetricsBinomial extends ModelMetrics {

  public final AUCData _aucdata;
  public final ConfusionMatrix _cm;

  public ModelMetricsBinomial(Model model, Frame frame) {
    super(model, frame);
    _aucdata = null;
    _cm = null;
  }

  public ModelMetricsBinomial(Model model, Frame frame, AUCData aucdata, ConfusionMatrix cm, HitRatio hr) {
    super(model, frame);
    _aucdata = aucdata;
    _cm = cm;
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

  public static class MetricBuilderBinomial extends MetricBuilder {
    public MetricBuilderBinomial( String[] domain ) { super(domain); }
    public MetricBuilderBinomial( String[] domain, float[] thresholds ) { super(domain, thresholds); }

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

      // Binomial classification -> compute AUC, draw ROC
      float snd = ds[2];      // Probability of a TRUE
      // TODO: Optimize this: just keep deltas from one CM to the next
      for(int i = 0; i < ModelUtils.DEFAULT_THRESHOLDS.length; i++) {
        int p = snd >= ModelUtils.DEFAULT_THRESHOLDS[i] ? 1 : 0; // Compute prediction based on threshold
        _cms[i][iact][p]++;   // Increase matrix
      }
      return ds;                // Flow coding
    }

    public ModelMetrics makeModelMetrics( Model m, Frame f, double sigma) {
      ConfusionMatrix[] cms = new ConfusionMatrix[_cms.length];
      for( int i=0; i<cms.length; i++ ) cms[i] = new ConfusionMatrix(_cms[i], _domain);

      AUCData aucdata = new AUC(cms,_thresholds,_domain).data();
      ConfusionMatrix cm = aucdata.CM();
      HitRatio hr = null;       // TODO
      return m._output.addModelMetrics(new ModelMetricsBinomial(m, f, aucdata, cm, hr));
    }
  }
}
