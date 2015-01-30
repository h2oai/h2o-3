package hex;

import water.fvec.Frame;

public class ModelMetricsRegression extends ModelMetricsSupervised {
  public ModelMetricsRegression(Model model, Frame frame) {
    super(model, frame);
  }
  public ModelMetricsRegression(Model model, Frame frame, double sigma, double mse) {
    super(model, frame, sigma, mse);
  }

  public static class MetricBuilderRegression extends MetricBuilderSupervised {
    public MetricBuilderRegression() {
      super(null); //this will make _work = new float[2];
    }

    // ds[0] has the prediction and ds[1] is ignored
    public float[] perRow( float ds[], float[] yact ) {
      if( Float.isNaN(yact[0]) ) return ds; // No errors if   actual   is missing
      if( Float.isNaN(ds[0])) return ds; // No errors if prediction is missing

      // Compute error
      float err = yact[0] - ds[0]; // Error: distance from the actual
      _sumsqe += err*err;       // Squared error
      assert !Double.isNaN(_sumsqe);
      _count++;
      return ds;                // Flow coding
    }

    // Having computed a MetricBuilder, this method fills in a ModelMetrics
    public ModelMetrics makeModelMetrics( Model m, Frame f, double sigma) {
      double mse = _sumsqe / _count;
      return m._output.addModelMetrics(new ModelMetricsRegression( m, f, sigma, mse));
    }
  }
}
