package hex;

import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;

public class ModelMetricsRegression extends ModelMetricsSupervised {
  public ModelMetricsRegression(Model model, Frame frame, double mse, double sigma) {
    super(model, frame, mse, null, sigma);
  }

  public static ModelMetricsRegression getFromDKV(Model model, Frame frame) {
    ModelMetrics mm = ModelMetrics.getFromDKV(model, frame);

    if (! (mm instanceof ModelMetricsRegression))
      throw new H2OIllegalArgumentException("Expected to find a Regression ModelMetrics for model: " + model._key.toString() + " and frame: " + frame._key.toString(),
              "Expected to find a ModelMetricsRegression for model: " + model._key.toString() + " and frame: " + frame._key.toString() + " but found a: " + mm.getClass());

    return (ModelMetricsRegression) mm;
  }


  public static class MetricBuilderRegression extends MetricBuilderSupervised {
    public MetricBuilderRegression() {
      super(1,null); //this will make _work = new float[2];
    }

    // ds[0] has the prediction and ds[1] is ignored
    @Override public double[] perRow( double ds[], float[] yact, Model m ) {
      if( Float.isNaN(yact[0]) ) return ds; // No errors if   actual   is missing
      if( Double.isNaN(ds[0])) return ds; // No errors if prediction is missing

      // Compute error
      double err = yact[0] - ds[0]; // Error: distance from the actual
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
