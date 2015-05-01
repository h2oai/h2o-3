package hex;

import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;

public class ModelMetricsBinomial extends ModelMetricsSupervised {
  public final AUC2 _auc;
  public final double _logloss;

  public ModelMetricsBinomial(Model model, Frame frame, double mse, String[] domain, double sigma, AUC2 auc, double logloss) {
    super(model, frame, mse, domain, sigma);
    _auc = auc;
    _logloss = logloss;
  }

  public static ModelMetricsBinomial getFromDKV(Model model, Frame frame) {
    ModelMetrics mm = ModelMetrics.getFromDKV(model, frame);
    if( !(mm instanceof ModelMetricsBinomial) )
      throw new H2OIllegalArgumentException("Expected to find a Binomial ModelMetrics for model: " + model._key.toString() + " and frame: " + frame._key.toString(),
              "Expected to find a ModelMetricsBinomial for model: " + model._key.toString() + " and frame: " + frame._key.toString() + " but found a: " + (mm == null ? null : mm.getClass()));
    return (ModelMetricsBinomial) mm;
  }

  @Override public AUC2 auc() { return _auc; }
  @Override public ConfusionMatrix cm() {
    if( _auc == null ) return null;
    long[][] cm = _auc.defaultCM();
    return cm == null ? null : new ConfusionMatrix(cm, _domain);
  }


  public static class MetricBuilderBinomial<T extends MetricBuilderBinomial<T>> extends MetricBuilderSupervised<T> {
    protected double _logloss;
    protected AUC2.AUCBuilder _auc;
    public MetricBuilderBinomial( String[] domain ) { super(2,domain); _auc = new AUC2.AUCBuilder(AUC2.NBINS); }

    // Passed a float[] sized nclasses+1; ds[0] must be a prediction.  ds[1...nclasses-1] must be a class
    // distribution;
    @Override public double[] perRow(double ds[], float[] yact, Model m) {
      if( Float .isNaN(yact[0]) ) return ds; // No errors if   actual   is missing
      if( Double.isNaN(ds  [0]) ) return ds; // No errors if prediction is missing
      _count++;
      final int iact = (int)yact[0];

      // Compute error
      double err = 1-ds[iact+1];  // Error: distance from predicting ycls as 1.0
      _sumsqe += err*err;           // Squared error
      assert !Double.isNaN(_sumsqe);

      // Compute log loss
      final double eps = 1e-15;
      _logloss += -Math.log(Math.max(eps,ds[iact+1]));
      _auc.perRow(ds[2],iact);

      return ds;                // Flow coding
    }

    @Override public void reduce( T mb ) {
      super.reduce(mb); // sumseq, count
      _logloss += mb._logloss;
      _auc.reduce(mb._auc);
    }

    @Override public ModelMetrics makeModelMetrics( Model m, Frame f, double sigma) {
      if (sigma != 0.0 && _count > 0 ) {
        double mse = _sumsqe / _count;
        double logloss = _logloss / _count;
        AUC2 auc = new AUC2(_auc);
        return m._output.addModelMetrics(new ModelMetricsBinomial(m, f, mse, _domain, sigma, auc, logloss));
      } else {
        return m._output.addModelMetrics(new ModelMetricsBinomial(m, f, Double.NaN, null, Double.NaN, null, Double.NaN));
      }
    }
  }
}
