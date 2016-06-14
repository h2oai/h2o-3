package hex;

import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.util.ArrayUtils;

public class ModelMetricsRegression extends ModelMetricsSupervised {
  public double residual_deviance() { return _mean_residual_deviance; }
  public final double _mean_residual_deviance;
  public ModelMetricsRegression(Model model, Frame frame, long nobs, double mse, double sigma, double meanResidualDeviance) {
    super(model, frame, nobs, mse, null, sigma);
    _mean_residual_deviance = meanResidualDeviance;
  }

  public static ModelMetricsRegression getFromDKV(Model model, Frame frame) {
    ModelMetrics mm = ModelMetrics.getFromDKV(model, frame);

    if (! (mm instanceof ModelMetricsRegression))
      throw new H2OIllegalArgumentException("Expected to find a Regression ModelMetrics for model: " + model._key.toString() + " and frame: " + frame._key.toString(),
              "Expected to find a ModelMetricsRegression for model: " + model._key.toString() + " and frame: " + frame._key.toString() + " but found a: " + mm.getClass());

    return (ModelMetricsRegression) mm;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(super.toString());
    sb.append(" mean residual deviance: " + (float)_mean_residual_deviance + "\n");
    return sb.toString();
  }

  public static class MetricBuilderRegression<T extends MetricBuilderRegression<T>> extends MetricBuilderSupervised<T> {
    double _sumdeviance;
    public MetricBuilderRegression() {
      super(1,null); //this will make _work = new float[2];
    }

    // ds[0] has the prediction and ds[1] is ignored
    @Override public double[] perRow(double ds[], float[] yact, Model m) {return perRow(ds, yact, 1, 0, m);}
    @Override public double[] perRow(double ds[], float[] yact, double w, double o,  Model m) {
      if( Float.isNaN(yact[0]) ) return ds; // No errors if   actual   is missing
      if(ArrayUtils.hasNaNs(ds)) return ds;  // No errors if prediction has missing values (can happen for GLM)
      if(w == 0 || Double.isNaN(w)) return ds;
      // Compute error
      double err = yact[0] - ds[0]; // Error: distance from the actual
      _sumsqe += w*err*err;       // Squared error
      assert !Double.isNaN(_sumsqe);
      if (m!=null) _sumdeviance += m.deviance(w, yact[0], ds[0]);
      _count++;
      _wcount += w;
      _wY += w*yact[0];
      _wYY += w*yact[0]*yact[0];
      return ds;                // Flow coding
    }

    @Override public void reduce( T mb ) {
      super.reduce(mb);
      _sumdeviance += mb._sumdeviance;
    }

    // Having computed a MetricBuilder, this method fills in a ModelMetrics
    public ModelMetrics makeModelMetrics(Model m, Frame f, Frame adaptedFrame, Frame preds) {
      double mse = _sumsqe / _wcount;
      double meanResDeviance = _sumdeviance / _wcount; //mean residual deviance
      return m._output.addModelMetrics(new ModelMetricsRegression( m, f, _count, mse, weightedSigma(), meanResDeviance));
    }

    public String toString() {return " mse = " + _sumsqe / _wcount;}
  }
}
