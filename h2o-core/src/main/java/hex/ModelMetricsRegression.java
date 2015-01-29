package hex;

import water.fvec.Frame;
import water.util.ModelUtils;

public class ModelMetricsRegression extends ModelMetrics {
  public final double _sigma;   // stddev of the response (if any)
  public final double _mse;     // Mean Squared Error
  public ModelMetricsRegression(Model model, Frame frame) {
    super(model, frame);
    _sigma = Double.NaN;
    _mse = Double.NaN;
  }
  public ModelMetricsRegression(Model model, Frame frame, double sigma, double mse) {
    super(model, frame);
    _sigma = sigma;
    _mse = mse;
  }
  @Override public double r2() {
    double var = _sigma*_sigma;
    return 1.0-(_mse/var);
  }

  public static class MetricBuilderRegression extends MetricBuilder {
    public MetricBuilderRegression( String[] domain ) { super(domain); }

    public float[] perRow( float ds[], float yact ) {
      if( Float.isNaN(yact) ) return ds; // No errors if   actual   is missing
      if( Float.isNaN(ds[0])) return ds; // No errors if prediction is missing

      // Compute error
      float err = yact - ds[0];     // Error: distance from the actual
      _sumsqe += err*err;       // Squared error
      assert !Double.isNaN(_sumsqe);

      _cms[0][0][0]++;        // Regression: count of rows only
      return ds;                // Flow coding
    }

    // Having computed a MetricBuilder, this method fills in a ModelMetrics
    public ModelMetrics makeModelMetrics( Model m, Frame f, double sigma) {
      ConfusionMatrix cm = new ConfusionMatrix(_cms[0], _domain);
      double mse = _sumsqe / cm.totalRows();
      return m._output.addModelMetrics(new ModelMetricsRegression( m, f, sigma, mse));
    }
  }
}
